package com.poyal.perilog

import android.app.Application
import com.poyal.perilog.data.*
import com.poyal.perilog.backup.*

class PerilogApplication: Application() {
    // Replaced by the instrumentation runner before any Activity is created.
    var updateSource: com.poyal.perilog.update.ReleaseSource = com.poyal.perilog.update.GitHubUpdates()
    private val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val updates by lazy { com.poyal.perilog.update.UpdateController(
        BuildConfig.VERSION_NAME, updateSource, com.poyal.perilog.update.AndroidUpdatePersistence(this),
        com.poyal.perilog.update.AndroidUpdateDownloads(this), updateScope) }
    val repository by lazy { Repository(JournalDb.open(this)) }
    val backup by lazy { BackupManager(this,repository) }
    override fun onCreate() { super.onCreate(); BackupManager.schedule(this) }
}
