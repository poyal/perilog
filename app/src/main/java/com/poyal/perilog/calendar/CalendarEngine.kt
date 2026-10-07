package com.poyal.perilog.calendar

import androidx.room.withTransaction
import com.poyal.perilog.data.*
import com.poyal.perilog.calendar.CalendarConnection.Companion.ACTIVE
import com.poyal.perilog.calendar.CalendarConnection.Companion.CLEANING
import com.poyal.perilog.calendar.CalendarConnection.Companion.AUTH
import com.poyal.perilog.calendar.CalendarConnection.Companion.OFF
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

class CalendarEngine(private val store: CalendarStore, private val gateway: (CalendarConnection) -> CalendarGateway) {
    /** Returns true only for retryable failures. No remote field ever writes Appointment. */
    suspend fun drain(provider: String): Boolean = CalendarSyncLock.mutex.withLock {
        val dao = store.dao
        var retry = false
        for (candidate in dao.jobs()) {
            val c = dao.connection(candidate.connectionId) ?: continue
            if(c.provider != provider || c.state !in listOf(ACTIVE, CLEANING)) continue
            if(c.state == CLEANING && c.error.isNotEmpty()) continue
            val job = dao.job(candidate.linkId) ?: continue
            if(job.generation != c.generation || job.blocked) continue
            var link = dao.link(job.linkId) ?: continue
            try {
                val api = gateway(c)
                api.verify(c)
                val found = api.find(c, link)
                if (job.payload == null) {
                    if(found == null && link.attempted && link.remoteId == null && c.provider == CalendarConnection.DEVICE)
                        throw CalendarConflictException("이전 전송의 완료 여부가 불확실해요. 캘린더에서 남은 일정을 확인해 주세요.")
                    if(found != null) api.delete(c, link.copy(remoteId = found))
                    finish(c, job, null, true)
                } else {
                    val payload = codec.decodeFromString<CalendarPayload>(job.payload)
                    var remote = found
                    if(remote != null) {
                        try { api.update(c, link.copy(remoteId = remote), payload) }
                        catch (_: CalendarMissingException) { remote = null }
                    }
                    if(remote == null) {
                        // A previously existing event was deleted outside Perilog.
                        if(link.remoteId != null || link.deleted) {
                            link = changeLink(link.id) { it.copy(remoteId = null, createKey = newId().replace("-", ""), attempted = false, deleted = false) }
                        }
                        val wasAttempted = link.attempted
                        changeLink(link.id) { it.copy(attempted = true) }
                        // Pass the pre-attempt value to detect an uncertain prior native insertion.
                        try { remote = api.create(c, link.copy(attempted = wasAttempted), payload) }
                        catch (_: CalendarMissingException) {
                            // Google retains tombstones, so a deleted creation ID cannot be reused.
                            link = changeLink(link.id) { it.copy(createKey = newId().replace("-", ""), attempted = true) }
                            remote = api.create(c, link.copy(attempted = false), payload)
                        }
                    }
                    finish(c, job, remote, false)
                }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                val access = e is CalendarAccessException || e is SecurityException
                val blocked = e is CalendarConflictException || e is IllegalArgumentException
                val message = when {
                    e is SecurityException -> "캘린더 권한을 다시 허용해 주세요."
                    e is CalendarAccessException || e is CalendarConflictException || e is CalendarRetryException -> e.message!!
                    else -> "캘린더에 반영하지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
                }
                store.db.withTransaction {
                    val fresh = dao.job(job.linkId)
                    if(fresh?.revision == job.revision) dao.put(fresh.copy(attempts = fresh.attempts + 1, error = message, blocked = blocked))
                    if(access) dao.connection(c.id)?.let { dao.put(it.copy(state = if(it.state == CLEANING) CLEANING else AUTH, error = message)) }
                }
                if(!access && !blocked) retry = true
            }
        }
        dao.connections().filter { it.provider == provider && it.state == CLEANING }.forEach { c ->
            if(dao.jobs().none { it.connectionId == c.id }) dao.put(c.copy(state = OFF, error = ""))
        }
        retry
    }

    private suspend fun changeLink(id: String, change: (CalendarLink) -> CalendarLink): CalendarLink = store.db.withTransaction {
        change(requireNotNull(store.dao.link(id))).also { store.dao.put(it) }
    }

    private suspend fun finish(c: CalendarConnection, job: CalendarJob, remoteId: String?, deleted: Boolean) = store.db.withTransaction {
        val dao = store.dao
        val time = System.currentTimeMillis()
        // Preserve a newer local revision written while the remote call was running.
        dao.link(job.linkId)?.let { dao.put(it.copy(remoteId = remoteId, deleted = deleted, attempted = false, lastSuccess = time)) }
        dao.complete(job.linkId, job.revision)
        dao.connection(c.id)?.let { dao.put(it.copy(lastSuccess = time, error = "")) }
    }
}
