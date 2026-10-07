package com.poyal.perilog.calendar

import android.content.Context
import androidx.work.*
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.Repository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit

class CalendarRuntime(private val context: Context, repository: Repository) {
    val store = CalendarStore(repository.db)
    val auth = GoogleCalendarAuth(context)
    // Replaced only by tests, before connecting any calendar.
    var gatewayFactory: (CalendarConnection) -> CalendarGateway = { c ->
        if(c.provider == CalendarConnection.DEVICE) DeviceCalendarGateway(context)
        else GoogleCalendarGateway(c.account, context.packageName, GoogleCalendarHttp(c.account, auth))
    }
    val engine = CalendarEngine(store) { gatewayFactory(it) }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            var previous = emptySet<String>()
            combine(store.dao.observeConnections(), store.dao.observeJobs()) { connections, jobs ->
                connections to jobs
            }.collect { (connections, jobs) ->
                val enabled = connections.filter { it.state in listOf(CalendarConnection.ACTIVE, CalendarConnection.CLEANING) }
                val current = jobs.filter { job -> !job.blocked && enabled.any {it.id == job.connectionId && it.generation == job.generation} }
                val signatures = current.map {"${it.linkId}:${it.generation}:${it.revision}"}.toSet()
                current.filter {"${it.linkId}:${it.generation}:${it.revision}" !in previous}
                    .mapNotNull {job -> enabled.find {it.id == job.connectionId}?.provider}.distinct().forEach(::request)
                previous = signatures
            }
        }
        scope.launch(Dispatchers.IO) {
            store.dao.observeConnections().distinctUntilChanged().collect { connections ->
                listOf(CalendarConnection.DEVICE, CalendarConnection.GOOGLE).forEach { provider ->
                    val work = WorkManager.getInstance(context)
                    val name = "perilog-calendar-periodic-$provider"
                    if(connections.any { it.provider == provider && it.state in listOf(CalendarConnection.ACTIVE, CalendarConnection.CLEANING) }) {
                        val request = PeriodicWorkRequestBuilder<CalendarWorker>(15, TimeUnit.MINUTES)
                            .setInputData(workDataOf("provider" to provider)).setConstraints(constraints(provider)).build()
                        work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
                    } else work.cancelUniqueWork(name)
                }
            }
        }
    }

    fun request(provider: String) {
        val request = OneTimeWorkRequestBuilder<CalendarWorker>().setInputData(workDataOf("provider" to provider))
            .setConstraints(constraints(provider)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("perilog-calendar-$provider", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun constraints(provider: String) = Constraints.Builder().apply {
        if(provider == CalendarConnection.GOOGLE) setRequiredNetworkType(NetworkType.CONNECTED)
    }.build()
}

class CalendarWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val provider = inputData.getString("provider") ?: return Result.failure()
        return try {
            if((applicationContext as PerilogApplication).calendar.engine.drain(provider)) Result.retry() else Result.success()
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { Result.retry() }
    }
}
