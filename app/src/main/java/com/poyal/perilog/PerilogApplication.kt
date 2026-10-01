package com.poyal.perilog

import android.app.Application
import com.poyal.perilog.data.*
import com.poyal.perilog.backup.*

class PerilogApplication: Application() {
    val repository by lazy { Repository(JournalDb.open(this)) }
    val backup by lazy { BackupManager(this,repository) }
    override fun onCreate() { super.onCreate(); BackupManager.schedule(this) }
}
