package com.poyal.perilog

import androidx.test.runner.AndroidJUnitRunner
import com.poyal.perilog.backup.deviceStore
import androidx.datastore.preferences.core.edit
import com.poyal.perilog.update.ReleaseInfo
import com.poyal.perilog.update.ReleaseSource

class PerilogTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        // Observe native Settings/credential windows as well as the app window.
        // Some OS images do not report an active root until window tracking is on.
        uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        (targetContext.applicationContext as PerilogApplication).updateSource = object : ReleaseSource {
            override suspend fun latest(): ReleaseInfo? = null
        }
        if(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("notificationPrompt")!="true") kotlinx.coroutines.runBlocking {
            targetContext.deviceStore.edit {it[com.poyal.perilog.backup.DeviceKeys.notificationRequested]=true}
        }
        super.onStart()
    }
}
