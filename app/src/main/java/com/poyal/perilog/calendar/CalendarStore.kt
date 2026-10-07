package com.poyal.perilog.calendar

import androidx.room.withTransaction
import com.poyal.perilog.data.*
import com.poyal.perilog.calendar.CalendarConnection.Companion.ACTIVE
import com.poyal.perilog.calendar.CalendarConnection.Companion.AUTH
import com.poyal.perilog.calendar.CalendarConnection.Companion.CLEANING
import com.poyal.perilog.calendar.CalendarConnection.Companion.OFF
import com.poyal.perilog.calendar.CalendarConnection.Companion.PAUSED
import com.poyal.perilog.calendar.CalendarConnection.Companion.DEVICE
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/** Serializes external side effects with connection changes, restore and reset. */
object CalendarSyncLock { val mutex = Mutex() }

class CalendarStore(val db: JournalDb) {
    val dao = db.calendarDao()

    /** Projection errors block delivery, while the appointment transaction can still commit. */
    suspend fun appointmentChanged(value: Appointment?, id: String) {
        dao.connections().filter { it.state == ACTIVE || it.state == AUTH }.forEach { c ->
            val existing = dao.link("${c.id}:$id")
            if (value != null && existing == null && value.date < today()) return@forEach
            if (value == null && existing == null) return@forEach
            queue(c, value, existing ?: CalendarLink("${c.id}:$id", c.id, id))
        }
    }

    private suspend fun queue(c: CalendarConnection, value: Appointment?, previous: CalendarLink) {
        val link = previous.copy(revision = previous.revision + 1)
        val payload = value?.let { runCatching { codec.encodeToString(it.calendarPayload(c)) } }
        dao.put(link)
        dao.put(CalendarJob(link.id, c.id, c.generation, link.revision,
            payload?.getOrNull(), error = payload?.exceptionOrNull()?.message ?: "",
            blocked = payload?.isFailure == true))
    }

    suspend fun connect(target: CalendarTarget, includeMemo: Boolean, allowNewAfterRestore: Boolean = false) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            require(dao.connections().none { it.state != OFF }) { "기존 캘린더 연결을 먼저 해제해 주세요." }
            val previous = dao.connections().find { it.provider == target.provider && it.account == target.account &&
                it.accountType == target.accountType && it.calendarId == target.id }
            val c = previous?.copy(state = ACTIVE, generation = previous.generation + 1,
                calendarName = target.name, includeMemo = includeMemo, error = "") ?: CalendarConnection(
                provider = target.provider, account = target.account, accountType = target.accountType,
                calendarId = target.id, calendarName = target.name, includeMemo = includeMemo)
            // On a new device native custom fields may not have survived cloud transfer.
            require(target.provider != DEVICE || dao.deviceState()?.restored != true || previous != null || allowNewAfterRestore) {
                "복원한 예약이 캘린더에 이미 있을 수 있어요. 기존 일정을 정리하거나 새 전송을 확인해 주세요."
            }
            dao.put(c)
            enqueueAll(c)
        }
    }

    private suspend fun enqueueAll(c: CalendarConnection) {
        val appointments = db.dao().appointments().associateBy { it.id }
        val links = dao.links(c.id).associateBy { it.appointmentId }
        appointments.values.filter { it.date >= today() || links[it.id]?.deleted == false }.forEach {
            queue(c, it, links[it.id] ?: CalendarLink("${c.id}:${it.id}", c.id, it.id))
        }
        // Missing local appointments after restore are deliberately not deleted remotely.
    }

    suspend fun reapply(id: String, includeMemo: Boolean? = null) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            val c = requireNotNull(dao.connection(id))
            require(c.state == ACTIVE) { "캘린더를 다시 연결해 주세요." }
            val next = c.copy(includeMemo = includeMemo ?: c.includeMemo)
            dao.put(next); enqueueAll(next)
        }
    }

    suspend fun retry(id: String) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            val c = requireNotNull(dao.connection(id))
            require(c.state == ACTIVE || c.state == CLEANING) { "캘린더를 다시 연결해 주세요." }
            dao.jobs().filter { it.connectionId == id && !it.blocked }.forEach { dao.put(it.copy(error = "", attempts = 0)) }
        }
    }

    suspend fun resume(id: String) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            val c = requireNotNull(dao.connection(id))
            require(c.state == AUTH || c.state == PAUSED || c.state == CLEANING)
            val next = c.copy(state = if (c.state == CLEANING) CLEANING else ACTIVE, error = "")
            dao.put(next)
            if(c.state == PAUSED) enqueueAll(next)
            else dao.jobs().filter { it.connectionId == id && !it.blocked }.forEach { dao.put(it.copy(error = "")) }
        }
    }

    suspend fun disconnect(id: String, deleteEvents: Boolean) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            val c = requireNotNull(dao.connection(id))
            val next = c.copy(state = if (deleteEvents) CLEANING else OFF, generation = c.generation + 1, error = "")
            dao.clearJobs(id); dao.put(next)
            if(deleteEvents) dao.links(id).filter { !it.deleted }.forEach { queue(next, null, it) }
            if(deleteEvents && dao.jobs().none {it.connectionId == id}) dao.put(next.copy(state = OFF))
        }
    }

    suspend fun allowFreshCreation(linkId: String) = CalendarSyncLock.mutex.withLock {
        db.withTransaction {
            val link = requireNotNull(dao.link(linkId))
            val c = requireNotNull(dao.connection(link.connectionId))
            require(c.state == ACTIVE)
            val appointment = db.dao().appointments().find { it.id == link.appointmentId }
                ?: error("삭제한 예약은 다시 전송할 수 없어요.")
            queue(c, appointment, link.copy(remoteId = null, createKey = newId().replace("-", ""), attempted = false, deleted = false))
        }
    }

    /** Caller holds CalendarSyncLock and the same transaction as data replacement. */
    suspend fun pauseForRestore() {
        dao.put(CalendarDeviceState(restored = true))
        dao.connections().forEach { c ->
            dao.clearJobs(c.id)
            if(c.state != OFF) dao.put(c.copy(state = PAUSED, generation = c.generation + 1,
                error = "데이터를 복원하거나 초기화하여 연동을 일시 정지했어요. 다시 연결하면 현재 예약을 반영해요."))
        }
    }
}
