package com.poyal.perilog.widget

import android.appwidget.AppWidgetManager
import android.content.*
import android.util.Log
import androidx.glance.appwidget.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.*
import com.poyal.perilog.PerilogApplication
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.concurrent.TimeUnit

object WidgetUpdates {
    private const val PERIODIC="perilog-widgets-periodic"
    private const val BOUNDARY="perilog-widgets-boundary"
    private const val REQUEST="perilog-widgets-refresh"
    private val mutex=Mutex()
    private fun providers() = listOf(
        DailyRecordWidgetReceiver::class.java to DailyRecordWidget(),
        CompactRecordWidgetReceiver::class.java to CompactRecordWidget(),
        AppointmentWidgetReceiver::class.java to AppointmentWidget()
    )
    fun installed(context: Context): Boolean = providers().map {it.first}
        .any { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,it)).isNotEmpty() }
    fun request(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(REQUEST,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
    fun restoreProviders(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers().forEach { (receiver, _) ->
            val component = ComponentName(context, receiver)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
    suspend fun refresh(context: Context) = mutex.withLock {
        val work=WorkManager.getInstance(context)
        if(!installed(context)) {
            work.cancelUniqueWork(PERIODIC);work.cancelUniqueWork(BOUNDARY)
            return@withLock
        }
        val now=System.currentTimeMillis()
        val manager=GlanceAppWidgetManager(context)
        val system = AppWidgetManager.getInstance(context)
        var failed = false
        // The OS receiver binding is authoritative, including after an upgrade
        // from a release whose R8-merged provider names remain in Glance state.
        providers().forEach { (receiver, widget) ->
            val component = ComponentName(context, receiver)
            system.getAppWidgetIds(component).forEach { appWidgetId ->
                if (system.getAppWidgetInfo(appWidgetId)?.provider == component) try {
                    val id = manager.getGlanceIdBy(appWidgetId)
                    updateAppWidgetState(context,id) { it[widgetRefreshKey]=now }
                    widget.update(context,id)
                } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                catch(_:Exception) {
                    // Do not log snapshots, exception messages or personal widget content.
                    Log.w("PerilogWidgets", "refresh failed: id=$appWidgetId receiver=${receiver.simpleName}")
                    failed = true
                }
            }
        }
        work.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30,TimeUnit.MINUTES).build())
        val s=(context.applicationContext as PerilogApplication).repository.snapshot()
        val clock=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val delay=Duration.between(clock,nextWidgetBoundary(s,clock)).toMillis().coerceAtLeast(1000)
        work.enqueueUniqueWork(BOUNDARY,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<WidgetBoundaryWorker>()
            .setInitialDelay(delay,TimeUnit.MILLISECONDS).build())
        check(!failed) { "Widget refresh incomplete" }
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = try { WidgetUpdates.refresh(applicationContext);Result.success() }
        catch(e:kotlinx.coroutines.CancellationException) {throw e}
        catch(_:Exception) {Result.retry()}
}
// The boundary worker enqueues a separate refresh so it never replaces/cancels itself while running.
class WidgetBoundaryWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result { WidgetUpdates.request(applicationContext);return Result.success() }
}
class WidgetClockReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) WidgetUpdates.restoreProviders(context)
        if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED))WidgetUpdates.request(context)
    }
}
