package com.poyal.perilog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.poyal.perilog.ui.PerilogTheme
import com.poyal.perilog.ui.UpdatesScreen
import com.poyal.perilog.update.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class UpdatesFlowTest {
    @get:Rule val ui = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val latest = ReleaseInfo("1.0.11", "fixture/1.0.11", "perilog-1.0.11.apk", 100, "a".repeat(64), "fixture")
    private class Memory(var record: UpdateRecord): UpdatePersistence {
        override suspend fun read() = record
        override suspend fun write(record: UpdateRecord) { this.record = record }
    }
    private class Downloads: UpdateDownloads {
        var status = TransferStatus.READY
        var enqueues = 0
        override suspend fun enqueue(release: ReleaseInfo): DownloadRecord {
            enqueues++; status = TransferStatus.DOWNLOADING
            return DownloadRecord(enqueues.toLong(), release, "new-$enqueues.apk")
        }
        override suspend fun progress(record: DownloadRecord) = DownloadProgress(status)
        override suspend fun verify(record: DownloadRecord) = Unit
        override suspend fun installationCopy(record: DownloadRecord) = "content://fixture"
        override suspend fun cancel(record: DownloadRecord) { status = TransferStatus.CANCELLED }
    }
    @After fun cleanup() { scope.cancel() }
    @Test fun oldDownloadedApkDoesNotShowRetryOrInstallAndLatestAttemptGetsVersionedActions() {
        val old = DownloadRecord(42, latest.copy(version = "1.0.10", apkUrl = "fixture/1.0.10"), "perilog-1.0.10-old.apk")
        val memory = Memory(UpdateRecord(latest, 1000, 1000, old))
        val downloads = Downloads()
        val controller = UpdateController("1.0.10", object: ReleaseSource { override suspend fun latest() = latest }, memory, downloads, scope) { 2000 }
        runBlocking { controller.refreshDownload() }
        ui.setContent { PerilogTheme("LIGHT") { UpdatesScreen(controller) {} } }
        ui.onNodeWithText("현재 버전 1.0.10").assertIsDisplayed()
        ui.onNodeWithText("1.0.11 APK 다운로드").performScrollTo().assertIsEnabled()
        ui.onNodeWithText("1.0.11 다시 다운로드").assertDoesNotExist()
        ui.onNodeWithText("1.0.11 업데이트 설치").assertDoesNotExist()
        ui.onNodeWithText("perilog-1.0.10-old.apk", substring = true).assertDoesNotExist()
        ui.onNodeWithText("1.0.11 APK 다운로드").performClick()
        ui.waitUntil(10000) { controller.state.value.downloadId == 1L }
        ui.onNodeWithText("1.0.11 APK 다운로드").assertIsNotEnabled()
        ui.onNodeWithText("다운로드 취소").performScrollTo().performClick()
        ui.waitUntil(10000) { controller.state.value.transfer == TransferStatus.CANCELLED }
        ui.onNodeWithText("1.0.11 다시 다운로드").performScrollTo().performClick()
        ui.waitUntil(10000) { downloads.enqueues == 2 }
        downloads.status = TransferStatus.READY
        runBlocking { controller.refreshDownload() }
        ui.onNodeWithText("1.0.11 업데이트 설치").performScrollTo().assertIsEnabled()
        ui.onNodeWithText("저장 파일: new-2.apk").performScrollTo().assertIsDisplayed()
    }
    @Test fun alreadyInstalledReleaseHasNoTransferOrInstallControls() {
        val download = DownloadRecord(42, latest, "old.apk")
        val controller = UpdateController(latest.version, object: ReleaseSource { override suspend fun latest() = latest },
            Memory(UpdateRecord(latest, 1000, 1000, download)), Downloads(), scope) { 2000 }
        runBlocking { controller.refreshDownload() }
        ui.setContent { PerilogTheme("LIGHT") { UpdatesScreen(controller) {} } }
        ui.onNodeWithText("최신 버전을 사용하고 있어요.").assertIsDisplayed()
        ui.onNodeWithText("1.0.11 APK 다운로드").assertDoesNotExist()
        ui.onNodeWithText("1.0.11 업데이트 설치").assertDoesNotExist()
        ui.onNodeWithText("저장 파일:", substring = true).assertDoesNotExist()
    }
}
