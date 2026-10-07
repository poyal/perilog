package com.poyal.perilog.capture

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.BuildConfig
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.ui.PerilogTheme
import com.poyal.perilog.ui.UpdatesScreen
import com.poyal.perilog.ui.LocalHelpAction
import com.poyal.perilog.update.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual update UI with a synthetic next release; never sends a download request. */
class UpdateCapture {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun availableVersionIgnoresPreviousDownload() {
        val latest = ReleaseInfo("1.0.14", "fixture", "perilog-1.0.14.apk", 100, "a".repeat(64), "fixture")
        val previous = DownloadRecord(42, latest.copy(version = "1.0.10"), "perilog-1.0.10-old.apk")
        val persistence = object: UpdatePersistence {
            override suspend fun read() = UpdateRecord(latest, System.currentTimeMillis(), System.currentTimeMillis(), previous)
            override suspend fun write(record: UpdateRecord) = Unit
        }
        val downloads = object: UpdateDownloads {
            override suspend fun enqueue(release: ReleaseInfo): DownloadRecord = error("Capture never downloads")
            override suspend fun progress(record: DownloadRecord): DownloadProgress = error("Old download must be ignored")
            override suspend fun verify(record: DownloadRecord) = error("Capture never verifies")
            override suspend fun installationCopy(record: DownloadRecord): String = error("Capture never installs")
            override suspend fun cancel(record: DownloadRecord) = error("Capture never cancels")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val controller = UpdateController(BuildConfig.VERSION_NAME, object: ReleaseSource { override suspend fun latest() = latest }, persistence, downloads, scope)
            runBlocking { controller.refreshDownload() }
            ui.runOnUiThread {
                ui.activity.enableEdgeToEdge()
                WindowCompat.getInsetsController(ui.activity.window, ui.activity.window.decorView).apply {
                    isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true
                }
            }
            ui.setContent { PerilogTheme("LIGHT") {
                CompositionLocalProvider(LocalHelpAction provides {}) {
                    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) { UpdatesScreen(controller) {} }
                    }
                }
            } }
            ui.onNodeWithText("1.0.14 APK 다운로드").assertIsDisplayed()
            ui.onNodeWithText("perilog-1.0.10-old.apk", substring = true).assertDoesNotExist()
            ui.waitForIdle(); SystemClock.sleep(650)
            val app = ApplicationProvider.getApplicationContext<PerilogApplication>()
            val folder = File(app.filesDir, "manual-screenshots").apply { mkdirs() }
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(folder, "29-about-update.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { scope.cancel() }
    }
}
