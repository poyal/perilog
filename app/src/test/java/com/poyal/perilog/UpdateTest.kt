package com.poyal.perilog

import com.poyal.perilog.update.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UpdateTest {
    private val bytes = "signed-apk-test-bytes".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val release = ReleaseInfo("1.0.4", "$RELEASES_URL/download/v1.0.4/perilog-1.0.4.apk", "perilog-1.0.4.apk", bytes.size.toLong(), hash, "$RELEASES_URL/download/v1.0.4/perilog-1.0.4.sha256")
    private class Memory(var value: UpdateRecord = UpdateRecord()) : UpdatePersistence {
        override suspend fun read() = value
        override suspend fun write(record: UpdateRecord) { value = record }
    }
    private class Downloads : UpdateDownloads {
        var enqueues = 0
        var verifications = 0
        var copies = 0
        var broken = false
        var cancelled = false
        var status = TransferStatus.DOWNLOADING
        override suspend fun enqueue(release: ReleaseInfo) = DownloadRecord((++enqueues).toLong(), release, "download-$enqueues.apk")
        override suspend fun progress(record: DownloadRecord) = DownloadProgress(status, 3, 10)
        override suspend fun verify(record: DownloadRecord) { verifications++; check(!broken) { "APK 체크섬이 일치하지 않아요." } }
        override suspend fun installationCopy(record: DownloadRecord): String { verify(record); copies++; return "content://verified" }
        override suspend fun cancel(record: DownloadRecord) { cancelled = status == TransferStatus.DOWNLOADING }
    }
    @Test fun numericVersionComparisonAndInvalidVersions() {
        assertTrue(compareVersions("v1.10.0", "1.9.9-debug") > 0)
        assertEquals(0, compareVersions("1.0.4", "v1.0.4"))
        assertTrue(compareVersions("1.0.3", "1.0.4") < 0)
        for (bad in listOf("1.2", "01.2.3", "1.2.3-beta", "1.2.3/evil", "99999999999.0.0")) {
            assertThrows(IllegalArgumentException::class.java) { normalizedVersion(bad) }
        }
    }
    @Test fun checksumsMustMatchBothExactFilenamesAndGitHubDigest() {
        val sums = "$hash  perilog-1.0.4.apk\n${"0".repeat(64)}  perilog-1.0.4-screenshots.zip\n"
        verifyChecksums(release, sums)
        for (bad in listOf(sums.replace(hash, "a".repeat(64)), sums + sums, sums.replace("perilog-1.0.4.apk", "../perilog-1.0.4.apk"), sums.lines()[0])) {
            assertThrows(IllegalArgumentException::class.java) { verifyChecksums(release, bad) }
        }
    }
    @Test fun verifyExactBytesAndCopyRejectsTamperingTruncationAndOversize() {
        val output = ByteArrayOutputStream()
        verifyApkStream(ByteArrayInputStream(bytes), output, release)
        assertArrayEquals(bytes, output.toByteArray())
        for (bad in listOf(bytes.dropLast(1).toByteArray(), bytes + 0, bytes.reversedArray())) {
            assertThrows(IllegalArgumentException::class.java) { verifyApkStream(ByteArrayInputStream(bad), null, release) }
        }
    }
    private fun json(digest: String = "sha256:$hash", state: String = "uploaded", prefix: String = RELEASES_URL) = """
        {"tag_name":"v1.0.4","draft":false,"prerelease":false,"assets":[
        {"name":"perilog-1.0.4.apk","state":"$state","size":${bytes.size},"digest":"$digest","browser_download_url":"$prefix/download/v1.0.4/perilog-1.0.4.apk"},
        {"name":"perilog-1.0.4.sha256","state":"uploaded","size":200,"browser_download_url":"$prefix/download/v1.0.4/perilog-1.0.4.sha256"}]}
    """.trimIndent()
    @Test fun onlyStableCompleteOfficialAssetsAreAccepted() {
        assertEquals(release, parseRelease(json()))
        for (bad in listOf(json(digest = ""), json(state = "new"), json(prefix = "https://evil.invalid"), json().replace("\"draft\":false", "\"draft\":true"), json().replace("\"prerelease\":false", "\"prerelease\":true"), json().replace("perilog-1.0.4.sha256", "other.txt"))) {
            assertThrows(IllegalArgumentException::class.java) { parseRelease(bad) }
        }
    }
    @Test fun automaticCheckIsDailyManualBypassesAndClockRollbackRecovers() {
        assertTrue(autoCheckDue(1, 0))
        assertFalse(autoCheckDue(2000, 1000))
        assertTrue(autoCheckDue(1000 + AUTO_CHECK_INTERVAL, 1000))
        assertTrue(autoCheckDue(500, 1000))
    }
    @Test fun startupPromptOncePerProcessButReturnsOnNextProcessFromCache() = runTest {
        val memory = Memory(UpdateRecord(release, 1000, 1000))
        var requests = 0
        val source = object : ReleaseSource { override suspend fun latest(): ReleaseInfo { requests++; return release } }
        fun controller(version: String = "1.0.3") = UpdateController(version, source, memory, Downloads(), backgroundScope) { 2000 }
        val first = controller(); first.startSession(); runCurrent()
        assertTrue(first.state.value.prompt); assertEquals(0, requests)
        first.dismissPrompt(); first.startSession(); runCurrent(); assertFalse(first.state.value.prompt)
        val next = controller(); next.startSession(); runCurrent(); assertTrue(next.state.value.prompt)
        val installed = controller("1.0.4"); installed.startSession(); runCurrent(); assertFalse(installed.state.value.prompt)
    }
    @Test fun failureKeepsLastSuccessWithoutClaimingCurrentAndManualRetryWorks() = runTest {
        val memory = Memory(UpdateRecord(release, 1000, 1000))
        var failed = true
        var now = AUTO_CHECK_INTERVAL + 2000
        var requests = 0
        val source = object : ReleaseSource { override suspend fun latest(): ReleaseInfo { requests++; if (failed) error("offline"); return release } }
        val controller = UpdateController("1.0.3", source, memory, Downloads(), backgroundScope) { now }
        controller.startSession(); runCurrent()
        assertEquals(CheckStatus.ERROR, controller.state.value.check)
        assertEquals(1000, controller.state.value.checkedAt)
        assertEquals(release, controller.state.value.release)
        assertTrue(controller.state.value.prompt)
        controller.check(); assertEquals(1, requests)
        now += 6000; failed = false; controller.check()
        assertEquals(CheckStatus.AVAILABLE, controller.state.value.check); assertEquals(2, requests)
    }
    @Test fun downloadsResumeReuseVerifiedBytesAndRecheckBeforeEveryInstall() = runTest {
        val memory = Memory(UpdateRecord(release, 1000, 1000))
        val downloads = Downloads()
        val source = object : ReleaseSource { override suspend fun latest() = release }
        fun controller() = UpdateController("1.0.3", source, memory, downloads, backgroundScope) { 2000 }
        val first = controller(); first.startSession(); runCurrent()
        first.download(); runCurrent(); first.download(); runCurrent()
        assertEquals(1, downloads.enqueues)
        downloads.status = TransferStatus.READY
        val resumed = controller(); resumed.startSession(); runCurrent()
        assertEquals(TransferStatus.READY, resumed.state.value.transfer)
        resumed.download(); runCurrent(); assertEquals(1, downloads.enqueues)
        assertEquals("content://verified", resumed.installationCopy())
        downloads.broken = true
        assertNull(resumed.installationCopy()); assertEquals(TransferStatus.FAILED, resumed.state.value.transfer)
        assertFalse(downloads.cancelled)
        resumed.download(); runCurrent(); assertEquals(2, downloads.enqueues)
    }
    @Test fun cancellationNeverDeletesCompletedDownload() = runTest {
        val downloads = Downloads().apply { status = TransferStatus.READY }
        val memory = Memory(UpdateRecord(release, 1000, 1000, DownloadRecord(1, release, "existing.apk")))
        val controller = UpdateController("1.0.3", object : ReleaseSource { override suspend fun latest() = release }, memory, downloads, backgroundScope) { 2000 }
        controller.cancelDownload(); runCurrent(); assertFalse(downloads.cancelled)
        assertNull(memory.value.download)
    }
}
