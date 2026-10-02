package com.poyal.perilog.update

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface UpdatePersistence {
    suspend fun read(): UpdateRecord
    suspend fun write(record: UpdateRecord)
}
data class DownloadProgress(val status: TransferStatus, val bytes: Long = 0, val total: Long = 0, val message: String = "")
interface UpdateDownloads {
    suspend fun enqueue(release: ReleaseInfo): DownloadRecord
    suspend fun progress(record: DownloadRecord): DownloadProgress
    suspend fun verify(record: DownloadRecord)
    suspend fun installationCopy(record: DownloadRecord): String
    suspend fun cancel(record: DownloadRecord)
}

/** Application-owned: a rotation/background round trip cannot create a second startup prompt. */
class UpdateController(
    val installedVersion: String,
    private val source: ReleaseSource,
    private val persistence: UpdatePersistence,
    private val downloads: UpdateDownloads,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private val initLock = Mutex()
    private val recordLock = Mutex()
    private val checkLock = Mutex()
    private val downloadLock = Mutex()
    private var initialized = false
    private var record = UpdateRecord()
    private var started = false
    private var verifiedId: Long? = null
    private var failedId: Long? = null

    private suspend fun initialize() = initLock.withLock {
        if (!initialized) {
            record = persistence.read()
            mutableState.update { it.copy(release = record.release, checkedAt = record.checkedAt,
                check = record.release?.let(::status) ?: CheckStatus.IDLE,
                message = record.release?.let(::versionMessage) ?: it.message,
                fileName = record.download?.fileName) }
            initialized = true
        }
    }
    private suspend fun save(change: (UpdateRecord) -> UpdateRecord) = recordLock.withLock {
        val next = change(record)
        persistence.write(next)
        record = next
    }
    private fun status(release: ReleaseInfo) = if (compareVersions(release.version, installedVersion) > 0) CheckStatus.AVAILABLE else CheckStatus.CURRENT
    private fun versionMessage(release: ReleaseInfo) = when {
        compareVersions(release.version, installedVersion) > 0 -> "페리로그 ${release.version} 업데이트가 있어요."
        compareVersions(release.version, installedVersion) == 0 -> "최신 버전을 사용하고 있어요."
        else -> "현재 설치 버전이 최신 공개 버전보다 높아요."
    }
    fun startSession(automatic: Boolean = true) {
        if (started) return
        started = true
        scope.launch {
            try {
                initialize()
                if (automatic) {
                    if (autoCheckDue(clock(), record.attemptedAt)) check()
                    mutableState.update { it.copy(prompt = it.release?.let { r -> status(r) == CheckStatus.AVAILABLE } == true) }
                }
                refreshDownload()
            } catch (e: Exception) { checkFailure(e) }
        }
    }
    fun dismissPrompt() { mutableState.update { it.copy(prompt = false) } }
    fun checkNow() { scope.launch { check() } }
    suspend fun check() {
        if (!checkLock.tryLock()) return
        try {
            initialize()
            // Protect GitHub from repeated manual taps, including immediately after an error.
            if (state.value.check != CheckStatus.IDLE && record.attemptedAt != 0L && clock() - record.attemptedAt in 0..4999) return
            save { it.copy(attemptedAt = clock()) }
            mutableState.update { it.copy(check = CheckStatus.CHECKING, message = "새 버전을 확인하고 있어요.") }
            val release = source.latest()
            save { it.copy(release = release, checkedAt = clock()) }
            mutableState.update { it.copy(release = release, checkedAt = record.checkedAt,
                check = release?.let(::status) ?: CheckStatus.NO_RELEASE,
                message = release?.let(::versionMessage) ?: "아직 공개된 정식 릴리즈가 없어요.",
                prompt = it.prompt && release?.let { r -> status(r) == CheckStatus.AVAILABLE } == true) }
        } catch (e: Exception) { checkFailure(e) }
        finally { checkLock.unlock() }
    }
    private fun checkFailure(e: Exception) {
        if (e is CancellationException) throw e
        mutableState.update { it.copy(check = CheckStatus.ERROR, message = friendly(e, "업데이트를 확인하지 못했어요. 인터넷 연결을 확인해 주세요.")) }
    }
    fun download() { scope.launch {
        if (!downloadLock.tryLock()) return@launch
        try {
            initialize()
            val release = state.value.release ?: error("업데이트를 먼저 확인해 주세요.")
            require(status(release) == CheckStatus.AVAILABLE) { "이미 해당 버전 이상을 사용하고 있어요." }
            val existing = record.download
            if (existing != null) {
                val progress = downloads.progress(existing)
                if (progress.status == TransferStatus.DOWNLOADING) {
                    mutableState.update { it.copy(transfer = progress.status, transferMessage = "이미 다운로드 중이에요.") }
                    return@launch
                }
                if (existing.release == release && progress.status == TransferStatus.READY) {
                    try {
                        downloads.verify(existing)
                        verifiedId = existing.id; failedId = null
                        ready(existing)
                        return@launch
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Keep the original file and use a new filename. */ }
                }
            }
            mutableState.update { it.copy(transfer = TransferStatus.DOWNLOADING, downloaded = 0, total = release.bytes, transferMessage = "다운로드를 준비하고 있어요.") }
            val next = downloads.enqueue(release)
            try { save { it.copy(download = next) } }
            catch (e: Exception) { downloads.cancel(next); throw e }
            verifiedId = null; failedId = null
            mutableState.update { it.copy(fileName = next.fileName, transferMessage = "Download 폴더에 저장하고 있어요.") }
        } catch (e: Exception) { transferFailure(e) }
        finally { downloadLock.unlock() }
    } }
    suspend fun refreshDownload() {
        try { initialize() } catch (e: Exception) { transferFailure(e); return }
        if (!downloadLock.tryLock()) return
        try {
            val current = record.download ?: return
            if (failedId == current.id) return
            val progress = downloads.progress(current)
            mutableState.update { it.copy(transfer = progress.status, downloaded = progress.bytes,
                total = progress.total, fileName = current.fileName, transferMessage = progress.message) }
            if (progress.status == TransferStatus.READY) {
                if (verifiedId != current.id) {
                    mutableState.update { it.copy(transfer = TransferStatus.VERIFYING, transferMessage = "APK 체크섬을 확인하고 있어요.") }
                    downloads.verify(current)
                    verifiedId = current.id
                }
                ready(current)
            }
        } catch (e: Exception) { failedId = record.download?.id; transferFailure(e) }
        finally { downloadLock.unlock() }
    }
    private fun ready(current: DownloadRecord) {
        mutableState.update { it.copy(transfer = TransferStatus.READY, fileName = current.fileName,
            downloaded = current.release.bytes, total = current.release.bytes,
            transferMessage = "${current.release.version} APK 검증 완료 · Download 폴더에 보관돼요.") }
    }
    fun cancelDownload() { scope.launch { downloadLock.withLock {
        try {
            initialize()
            record.download?.let { downloads.cancel(it) }
            save { it.copy(download = null) }
            verifiedId = null; failedId = null
            mutableState.update { it.copy(transfer = TransferStatus.CANCELLED, transferMessage = "다운로드를 취소했어요. 완료된 파일은 보존해요.") }
        } catch (e: Exception) { transferFailure(e) }
    } } }
    suspend fun installationCopy(): String? = downloadLock.withLock {
        try {
            initialize()
            val current = record.download ?: error("APK를 먼저 다운로드해 주세요.")
            require(compareVersions(current.release.version, installedVersion) > 0) { "이미 설치된 버전이에요. 업데이트를 다시 확인해 주세요." }
            mutableState.update { it.copy(transfer = TransferStatus.VERIFYING, transferMessage = "설치 전에 파일을 다시 확인하고 있어요.") }
            val uri = downloads.installationCopy(current)
            ready(current)
            uri
        } catch (e: Exception) { failedId = record.download?.id; transferFailure(e); null }
    }
    fun installationMessage(text: String) { mutableState.update { it.copy(transferMessage = text) } }
    private fun transferFailure(e: Exception) {
        if (e is CancellationException) throw e
        mutableState.update { it.copy(transfer = TransferStatus.FAILED, transferMessage = friendly(e, "파일을 처리하지 못했어요. 저장 공간과 인터넷 연결을 확인해 주세요.")) }
    }
    private fun friendly(e: Exception, fallback: String) = e.message?.takeIf { it.any { c -> c in '가'..'힣' } } ?: fallback
}
