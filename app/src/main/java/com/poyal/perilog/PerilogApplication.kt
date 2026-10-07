package com.poyal.perilog

import android.app.Application
import com.poyal.perilog.data.*
import com.poyal.perilog.backup.*
import com.poyal.perilog.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PerilogApplication: Application() {
    // Replaced by the instrumentation runner before any Activity is created.
    var updateSource: com.poyal.perilog.update.ReleaseSource = com.poyal.perilog.update.GitHubUpdates()
    private val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val updates by lazy { com.poyal.perilog.update.UpdateController(
        BuildConfig.VERSION_NAME, updateSource, com.poyal.perilog.update.AndroidUpdatePersistence(this),
        com.poyal.perilog.update.AndroidUpdateDownloads(this), updateScope) }
    val repository by lazy { Repository(JournalDb.open(this)) }
    val backup by lazy { BackupManager(this,repository) }
    val calendar by lazy { com.poyal.perilog.calendar.CalendarRuntime(this,repository) }
    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate(); BackupManager.schedule(this)
        calendar.start(updateScope)
        Reminders.ensureChannel(this)
        updateScope.launch(Dispatchers.IO) { Reminders.schedule(this@PerilogApplication,repository.snapshot().preferences) }
        updateScope.launch(Dispatchers.IO) {
            repository.snapshots.map { widgetDataKey(it) }.distinctUntilChanged().debounce(500).collect {
                if(WidgetUpdates.installed(this@PerilogApplication)) try { WidgetUpdates.refresh(this@PerilogApplication) }
                    catch(e:CancellationException) {throw e}
                    catch(_:Exception) {WidgetUpdates.request(this@PerilogApplication)}
            }
        }
    }
}
