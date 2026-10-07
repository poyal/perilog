package com.poyal.perilog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.poyal.perilog.calendar.*
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = android.app.Application::class)
class CalendarSyncTest {
    private lateinit var db: JournalDb
    private lateinit var repo: Repository
    private lateinit var store: CalendarStore
    private lateinit var remote: FakeCalendar
    private lateinit var engine: CalendarEngine
    private val target = CalendarTarget(CalendarConnection.DEVICE, "test", "LOCAL", "1", "검사용")
    private fun booking(id: String = "visit") = Appointment(id = id, date = LocalDate.now().plusDays(2).toString(), time = "09:00", memo = "비공개 메모")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), JournalDb::class.java).allowMainThreadQueries().build()
        repo = Repository(db); store = CalendarStore(db); remote = FakeCalendar(); engine = CalendarEngine(store) {remote}
    }
    @After fun close() {db.close()}
    private suspend fun connect() {store.connect(target, false)}
    private suspend fun drain() = engine.drain(CalendarConnection.DEVICE)
    private suspend fun connection() = store.dao.connections().single()

    @Test fun singleVisitProjectionOrdersTimesKeepsLegacyAndExcludesMemoByDefault() {
        val c = CalendarConnection(provider = "DEVICE", account = "", accountType = "LOCAL", calendarId = "1", calendarName = "", zoneId = "Asia/Seoul")
        val a = Appointment(id = "projection", date = "2026-10-12", time = "09:00",
            departments = listOf(Department("eye", "안과"), Department("kidney", "신장내과")),
            departmentTimes = mapOf("eye" to "11:00", "kidney" to "09:00"),
            careItems = listOf(CareTask("blood", "피검사", time = "08:30"), CareTask("room", "투석실")), memo = "비공개")
        val p = a.calendarPayload(c)
        assertEquals("병원 방문 · 안과, 신장내과", p.title)
        assertTrue(p.description.startsWith("08:30 · 피검사\n09:00 · 신장내과\n11:00 · 안과\n투석실"))
        assertFalse(p.description.contains("비공개"))
        assertTrue(a.calendarPayload(c.copy(includeMemo = true)).description.contains("비공개"))
        assertEquals(Instant.parse("2026-10-11T23:30:00Z").toEpochMilli(), p.startMillis)
        assertEquals(Instant.parse("2026-10-12T02:30:00Z").toEpochMilli(), p.endMillis)
        val late = a.copy(departments = emptyList(), departmentTimes = emptyMap(), careItems = emptyList(), time = "23:50")
        assertEquals(30 * 60 * 1000L, late.calendarPayload(c).let {it.endMillis - it.startMillis})
        val legacy = a.copy(departmentTimes = emptyMap()).calendarPayload(c)
        assertTrue(legacy.description.contains("09:00 · 안과"))
        assertEquals("Asia/Seoul", p.zoneId)
        val beforeGap = late.copy(date = "2030-03-10", time = "01:45").calendarPayload(c.copy(zoneId = "America/New_York"))
        assertEquals(30 * 60 * 1000L, beforeGap.endMillis - beforeGap.startMillis)
    }

    @Test fun daylightGapBlocksOnlyCalendarAndAppointmentStillSaves() = runBlocking {
        connect()
        store.dao.put(connection().copy(zoneId = "America/New_York"))
        val a = booking().copy(date = "2030-03-10", time = "02:30")
        repo.appointment(a)
        assertEquals(a, repo.snapshot().appointments.single())
        assertTrue(store.dao.jobs().single().blocked)
        drain(); assertTrue(remote.events.isEmpty())
    }

    @Test fun registrationUpdatesAndDeletionAreQueuedAndNeverImportExternalChanges() = runBlocking {
        connect(); val a = booking(); repo.appointment(a)
        assertEquals(1, store.dao.jobs().size); assertTrue(remote.events.isEmpty())
        drain(); val key = remote.events.keys.single()
        assertFalse(remote.events.getValue(key).description.contains(a.memo))
        remote.events[key] = remote.events.getValue(key).copy(title = "캘린더에서 수정")
        drain(); assertEquals(a, repo.snapshot().appointments.single())
        assertEquals("캘린더에서 수정", remote.events.getValue(key).title)
        repo.appointment(a.copy(time = "10:00")); drain()
        assertEquals(setOf(key), remote.events.keys)
        assertEquals(1, remote.created)
        assertEquals("병원 방문", remote.events.getValue(key).title)
        repo.deleteAppointment(a.id)
        assertTrue(repo.snapshot().appointments.isEmpty()); assertEquals(1, store.dao.jobs().size)
        drain(); assertTrue(remote.events.isEmpty()); assertTrue(store.dao.jobs().isEmpty())
    }

    @Test fun externalDeletionWaitsForExplicitReapplyAndRegeneratesOnce() = runBlocking {
        connect(); repo.appointment(booking()); drain()
        val key = remote.events.keys.single(); remote.events.clear(); drain()
        assertEquals(1, repo.snapshot().appointments.size); assertTrue(remote.events.isEmpty())
        store.reapply(connection().id); drain(); drain()
        assertEquals(1, remote.events.size); assertNotEquals(key, remote.events.keys.single()); assertEquals(2, remote.created)
    }

    @Test fun firstConnectionSkipsPastButKeepsAlreadyLinkedPastAppointments() = runBlocking {
        repo.appointment(booking("past").copy(date = LocalDate.now().minusDays(1).toString()))
        repo.appointment(booking()); connect(); drain()
        assertEquals(1, remote.events.size)
        repo.appointment(booking().copy(date = LocalDate.now().minusDays(2).toString())); drain()
        store.reapply(connection().id); drain()
        assertEquals(1, remote.events.size)
    }

    @Test fun failedInsertionResponseAndProcessRestartReuseTheSameEvent() = runBlocking {
        connect(); repo.appointment(booking()); remote.failAfterCreate = true
        assertTrue(drain()); assertEquals(1, remote.events.size)
        val restarted = CalendarEngine(CalendarStore(db)) {remote}
        assertFalse(restarted.drain(CalendarConnection.DEVICE))
        assertEquals(1, remote.created); assertTrue(store.dao.jobs().isEmpty())
    }

    @Test fun newerLocalUpdateDuringRemoteWriteIsNotAcknowledgedEarly() = runBlocking {
        connect(); val a = booking(); repo.appointment(a)
        remote.duringCreate = {repo.appointment(a.copy(time = "13:00"))}
        drain(); assertEquals(1, store.dao.jobs().size)
        assertEquals("13:00", repo.snapshot().appointments.single().time)
        drain(); assertTrue(store.dao.jobs().isEmpty()); assertEquals(1, remote.created)
        assertEquals(a.copy(time = "13:00").calendarPayload(connection()).startMillis, remote.events.values.single().startMillis)
    }

    @Test fun deletionDuringRemoteCreationIsEventuallyDeletedNotResurrected() = runBlocking {
        connect(); repo.appointment(booking())
        remote.duringCreate = {repo.deleteAppointment("visit")}
        drain(); assertEquals(1, remote.events.size)
        drain(); assertTrue(remote.events.isEmpty()); assertTrue(repo.snapshot().appointments.isEmpty())
    }

    @Test fun accessRevocationKeepsLocalDataAndResumesQueuedWork() = runBlocking {
        connect(); repo.appointment(booking()); remote.access = false
        assertFalse(drain()); assertEquals(CalendarConnection.AUTH, connection().state)
        repo.appointment(booking().copy(time = "11:00"))
        assertEquals(1, store.dao.jobs().size); remote.access = true
        store.resume(connection().id); drain(); assertEquals(1, remote.events.size)
        assertEquals(CalendarConnection.ACTIVE, connection().state)
    }

    @Test fun restorePausesAndDoesNotDeleteMissingRemoteAppointments() = runBlocking {
        connect(); repo.appointment(booking()); drain()
        val snapshot = repo.snapshot()
        val json = codec.encodeToString(snapshot)
        assertFalse(json.contains("calendar_connections")); assertFalse(json.contains("createKey"))
        repo.restore(Snapshot()); drain()
        assertEquals(CalendarConnection.PAUSED, connection().state)
        assertEquals(1, remote.events.size); assertTrue(store.dao.jobs().isEmpty())
        repo.restore(snapshot); store.resume(connection().id); drain()
        assertEquals(1, remote.events.size); assertEquals(1, remote.created)
    }

    @Test fun disconnectKeepsEventsOrDeletesOnlyOwnedOnExplicitRequest() = runBlocking {
        connect(); repo.appointment(booking()); drain()
        store.disconnect(connection().id, false); repo.deleteAppointment("visit"); drain()
        assertEquals(1, remote.events.size); assertEquals(CalendarConnection.OFF, connection().state)
        store.connect(target, false); store.disconnect(connection().id, true); drain()
        assertTrue(remote.events.isEmpty()); assertEquals(CalendarConnection.OFF, connection().state)
    }

    @Test fun onlyOneDestinationCanBeActiveAndNativeRestoreRequiresAcknowledgement() = runBlocking {
        repo.restore(Snapshot(appointments = listOf(booking())))
        assertTrue(runCatching {store.connect(target, false)}.isFailure)
        store.connect(target, false, allowNewAfterRestore = true)
        assertTrue(runCatching {store.connect(target.copy(id = "2"), false)}.isFailure)
        assertEquals(1, store.dao.connections().size)
    }

    @Test fun memoChangeQueuesAnUpdateAndPreservesCalendarOnlyFields() = runBlocking {
        connect(); repo.appointment(booking()); drain()
        store.reapply(connection().id, true); drain()
        assertTrue(remote.events.values.single().description.contains("비공개 메모"))
        store.reapply(connection().id, false); drain()
        assertFalse(remote.events.values.single().description.contains("비공개 메모"))
        assertEquals(1, remote.created)
    }

    private class FakeCalendar: CalendarGateway {
        val events = linkedMapOf<String, CalendarPayload>()
        var created = 0; var access = true; var failAfterCreate = false
        var duringCreate: (suspend () -> Unit)? = null
        override suspend fun targets() = emptyList<CalendarTarget>()
        override suspend fun verify(connection: CalendarConnection) {if(!access) throw CalendarAccessException("권한 없음")}
        override suspend fun find(connection: CalendarConnection, link: CalendarLink) = events.entries.singleOrNull {it.value.appointmentId == link.appointmentId}?.key
        override suspend fun create(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload): String {
            events[link.createKey] = payload; created++
            duringCreate?.also {duringCreate = null}?.invoke()
            if(failAfterCreate) {failAfterCreate = false; throw CalendarRetryException("응답 유실")}
            return link.createKey
        }
        override suspend fun update(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload) {events[link.remoteId!!] = payload}
        override suspend fun delete(connection: CalendarConnection, link: CalendarLink) {events.remove(link.remoteId)}
    }
}
