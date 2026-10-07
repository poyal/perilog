package com.poyal.perilog

import android.app.ActivityManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Synthetic settings only. Run on a disposable emulator with no pre-existing screen credential. */
class PrivacyFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun snapshot() = runBlocking { app.repository.snapshot() }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
    private fun waitFor(condition: () -> Boolean) = ui.waitUntil(20000, condition)
    private fun click(text: String) {
        val node = ui.onNode(hasText(text) and hasClickAction())
        runCatching { node.performScrollTo() }; node.performClick()
    }
    private fun secure() = ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    private fun foreground() = ui.activity.getSystemService(ActivityManager::class.java).appTasks
        .first { it.taskInfo.taskId == ui.activity.taskId }.moveToFront()
    private fun waitForCredentialInput() {
        waitFor { automation.rootInActiveWindow?.packageName?.toString() in setOf("com.android.settings", "com.android.systemui") &&
            automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.isEditable == true }
        automation.waitForIdle(500, 5000)
    }
    private fun preferences(lock: Boolean, protection: Boolean) {
        runBlocking { app.repository.preferences(snapshot().preferences.copy(lock = lock, screenProtection = protection)) }
        waitFor { secure() == protection }; ui.waitForIdle()
    }
    private fun screenshot(name: String): Bitmap? {
        SystemClock.sleep(350)
        val bitmap = automation.takeScreenshot()
        val directory = File(app.filesDir, "e2e-artifacts/privacy").apply { mkdirs() }
        if (bitmap != null) {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else File(directory, "$name.blocked.txt").writeText("Android refused the screenshot; FLAG_SECURE=${secure()}")
        return bitmap
    }
    private fun assertContentVisibility(bitmap: Bitmap?, protected: Boolean) {
        // Android 12 can refuse the capture altogether instead of returning a masked bitmap.
        if (bitmap == null) { assertTrue("Unprotected capture was refused", protected); return }
        // Sample the app interior, excluding OS bars. FLAG_SECURE leaves this region black in captures.
        var black = 0; var count = 0
        for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 8)
            for (y in bitmap.height / 4 until bitmap.height * 3 / 4 step 8) {
                val color = bitmap.getPixel(x, y)
                if ((color and 0x00ffffff) == 0) black++
                count++
            }
        val fraction = black.toDouble() / count
        if (protected) assertTrue("Protected app pixels leaked: black=$fraction", fraction > .99)
        else assertTrue("Unprotected app content should be visible: black=$fraction", fraction < .9)
    }
    @Before fun seed() {
        runBlocking { app.repository.restore(Snapshot(preferences = Preferences(darkMode = "LIGHT"))) }
        waitFor { ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        preferences(false, false)
    }
    @Test fun allFourCombinationsControlScreenshotsRecordingsAndDialogProtection() {
        for (lock in listOf(false, true)) for (protection in listOf(false, true)) {
            preferences(lock, protection)
            ui.onNodeWithContentDescription("설정").assertIsDisplayed()
            val name = "lock-$lock-protection-$protection"
            assertContentVisibility(screenshot(name), protection)
            val video = File(app.cacheDir, "$name.mp4")
            val recording = shell("screenrecord --time-limit 1 /data/local/tmp/$name.mp4")
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("cat /data/local/tmp/$name.mp4")).use { input ->
                video.outputStream().use { input.copyTo(it) }
            }
            assertTrue("No screen recording: $recording", video.length() > 0)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.absolutePath)
                val frame = requireNotNull(retriever.getFrameAtTime(300000))
                assertContentVisibility(frame, protection)
            } finally { retriever.release() }
            if (!lock) {
                shell("input keyevent KEYCODE_APP_SWITCH")
                SystemClock.sleep(700); screenshot("recents-$name")
                foreground()
                waitFor { ui.activity.hasWindowFocus() }
            }
        }
        preferences(false, true)
        ui.onNodeWithContentDescription("설정").performClick(); click("앱 잠금·화면 보호")
        ui.onNodeWithText("화면 보호").assertIsOn()
        click("화면 보호")
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { ui.onAllNodesWithText("변경 내용을 버릴까요?").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("변경 내용을 버릴까요?").assertIsDisplayed()
        assertContentVisibility(screenshot("protected-discard-dialog"), true)
        click("확인")
    }
    @Test fun protectionDraftCancelSaveRecreationAndUnrelatedSettingsAreIndependent() {
        ui.onNodeWithContentDescription("설정").performClick(); click("앱 잠금·화면 보호")
        click("화면 보호")
        assertFalse(secure()); assertFalse(snapshot().preferences.screenProtection)
        ui.activityRule.scenario.recreate()
        waitFor { ui.onAllNodesWithText("화면 보호").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("화면 보호").assertIsOn()
        click("취소"); click("확인")
        assertFalse(snapshot().preferences.screenProtection)
        click("앱 잠금·화면 보호"); click("화면 보호")
        runBlocking { app.repository.preferences(snapshot().preferences.copy(reminderHour = 17, keepBackups = 12)) }
        click("잠금·보호 설정 저장")
        waitFor { secure() && snapshot().preferences.screenProtection }
        assertFalse(snapshot().preferences.lock)
        assertEquals(17, snapshot().preferences.reminderHour)
        assertEquals(12, snapshot().preferences.keepBackups)
        ui.activityRule.scenario.recreate()
        waitFor { secure() && ui.onAllNodesWithText("화면 보호").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("화면 보호").assertIsOn()
        ui.onNodeWithText("앱 잠금").assertIsOff()
        click("화면 보호"); click("잠금·보호 설정 저장")
        waitFor { !secure() && !snapshot().preferences.screenProtection }
    }
    @Test fun lockOnlyAuthenticatesOnReturnAndCancelNeverExposesContent() {
        check(shell("locksettings set-pin 1234").contains("1234")) { "Disposable emulator must have no existing credential" }
        try {
            ui.onNodeWithContentDescription("설정").performClick(); click("앱 잠금·화면 보호")
            click("앱 잠금"); click("잠금·보호 설정 저장")
            waitFor { snapshot().preferences.lock }
            assertFalse(snapshot().preferences.screenProtection); assertFalse(secure())
            shell("input keyevent KEYCODE_HOME")
            waitFor { ui.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED }
            foreground()
            waitForCredentialInput()
            shell("input keyevent KEYCODE_BACK")
            // Some Android versions consume the first back to hide the PIN keyboard.
            if (runCatching { ui.waitUntil(3000) { ui.activity.hasWindowFocus() } }.isFailure)
                shell("input keyevent KEYCODE_BACK")
            waitFor { ui.activity.hasWindowFocus() }
            ui.onNodeWithText("기록 열기").assertIsDisplayed()
            ui.onNodeWithText("잠금·보호 설정 저장").assertDoesNotExist()
            ui.onNodeWithContentDescription("설정").assertDoesNotExist()
            click("기록 열기")
            waitForCredentialInput()
            shell("input text 1234")
            shell("input keyevent KEYCODE_ENTER")
            waitFor { ui.activity.hasWindowFocus() && ui.onAllNodesWithText("잠금·보호 설정 저장").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(secure())
            ui.onNodeWithText("앱 잠금").assertIsOn()
            click("화면 보호"); click("잠금·보호 설정 저장")
            waitFor { secure() }
            click("앱 잠금"); click("잠금·보호 설정 저장")
            waitFor { !snapshot().preferences.lock }
            assertTrue(snapshot().preferences.screenProtection)
        } finally {
            preferences(false, false)
            shell("locksettings clear --old 1234")
            shell("wm dismiss-keyguard")
        }
    }
}
