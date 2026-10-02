package com.poyal.perilog

import androidx.test.runner.AndroidJUnitRunner
import com.poyal.perilog.update.ReleaseInfo
import com.poyal.perilog.update.ReleaseSource

class PerilogTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        (targetContext.applicationContext as PerilogApplication).updateSource = object : ReleaseSource {
            override suspend fun latest(): ReleaseInfo? = null
        }
        super.onStart()
    }
}
