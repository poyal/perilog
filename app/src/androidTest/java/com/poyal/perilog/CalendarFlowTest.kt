package com.poyal.perilog

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.poyal.perilog.calendar.*
import com.poyal.perilog.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** Synthetic data and a dedicated LOCAL calendar. Run only on a disposable emulator. */
class CalendarFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<PerilogApplication>()
    private val resolver get() = app.contentResolver
    private var calendarId = -1L
    private val name = "페리로그 연동 검사"
    private val fixtureAccount = "perilog-calendar-fixture"
    private fun calendarUri() = Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, fixtureAccount)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
    private val visit by lazy { Appointment(id = "calendar-fixture", date = LocalDate.now().plusDays(3).toString(), time = "09:00",
        departments = listOf(Department("kidney", "신장내과"), Department("eye", "안과")),
        departmentTimes = mapOf("kidney" to "09:00", "eye" to "11:00"), memo = "공유하지 않을 메모") }
    private fun click(text: String) {
        val node = ui.onNode(hasText(text) and hasClickAction())
        runCatching {node.performScrollTo()}; node.performClick()
    }
    private fun waitUntil(condition: () -> Boolean) = ui.waitUntil(20000, condition)
    @Before fun prepare() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.READ_CALENDAR)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.WRITE_CALENDAR)
        runBlocking {
            app.calendar.store.dao.connections().forEach {app.calendar.store.disconnect(it.id, false)}
            app.repository.restore(Snapshot(appointments = listOf(visit)))
        }
        calendarId = ContentUris.parseId(requireNotNull(resolver.insert(calendarUri(), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, fixtureAccount); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "perilog-test-${System.nanoTime()}"); put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, 0xFF2476CF.toInt()); put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, fixtureAccount); put(Calendars.CALENDAR_TIME_ZONE, "Asia/Seoul")
            put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
        })))
        waitUntil {ui.onAllNodesWithContentDescription("설정").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun cleanup() {
        runBlocking {app.calendar.store.dao.connections().forEach {app.calendar.store.disconnect(it.id, false)}}
        if(calendarId > 0) resolver.delete(calendarUri(), "${Calendars._ID}=?", arrayOf(calendarId.toString()))
    }
    private fun remote(): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        resolver.query(Events.CONTENT_URI, arrayOf(Events._ID, Events.TITLE), "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0", arrayOf(calendarId.toString()), null)!!.use {
            while(it.moveToNext()) result += it.getLong(0) to it.getString(1)
        }
        return result
    }
    private fun capture(fileName: String) {
        ui.waitForIdle()
        val directory = File(app.filesDir, "calendar-screenshots").apply {mkdirs()}
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val name = if(app.resources.configuration.fontScale > 1.2f) fileName.replace(".png", "-large.png") else fileName
        File(directory, name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)}
    }

    @Test fun connectThroughSettingsAndKeepLocalAppointmentAuthoritative() {
        ui.onNodeWithContentDescription("설정").performClick(); click("캘린더 연동")
        capture("76-calendar-connect.png")
        click("휴대폰 캘린더 연결")
        waitUntil {ui.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText(name).performScrollTo()
        capture("78-calendar-select.png")
        click(name)
        capture("79-calendar-confirm.png")
        // The fixture was restored; a new native connection explicitly acknowledges old copies.
        ui.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
        click("연동 시작")
        waitUntil {remote().size == 1}
        waitUntil {ui.onAllNodesWithText("반영 완료").fetchSemanticsNodes().isNotEmpty()}
        capture("77-calendar-connected.png")
        val remoteId = remote().single().first
        resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, remoteId), arrayOf(Events.DESCRIPTION, Events.DTSTART, Events.DTEND), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertFalse(it.getString(0).contains("공유하지 않을 메모"))
            assertEquals(150 * 60 * 1000L, it.getLong(2) - it.getLong(1))
        }
        // Only external fields change; no inbound writer exists.
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, remoteId), ContentValues().apply {put(Events.TITLE, "외부에서 바꾼 제목")}, null, null)
        runBlocking {app.calendar.engine.drain(CalendarConnection.DEVICE)}
        assertEquals(visit, runBlocking {app.repository.snapshot().appointments.single()})
        assertEquals("외부에서 바꾼 제목", remote().single().second)
        runBlocking {app.repository.appointment(visit.copy(time = "10:00", departmentTimes = mapOf("kidney" to "10:00", "eye" to "12:00"))); app.calendar.engine.drain(CalendarConnection.DEVICE)}
        assertEquals(remoteId, remote().single().first)
        assertEquals("병원 방문 · 신장내과 외 1개", remote().single().second)
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, remoteId), null, null)
        runBlocking {app.calendar.engine.drain(CalendarConnection.DEVICE)}
        assertEquals(1, runBlocking {app.repository.snapshot().appointments.size}); assertTrue(remote().isEmpty())
        click("전체 다시 반영"); click("확인")
        waitUntil {remote().size == 1}
        assertNotEquals(remoteId, remote().single().first)
        runBlocking {app.repository.deleteAppointment(visit.id); app.calendar.engine.drain(CalendarConnection.DEVICE)}
        assertTrue(remote().isEmpty())
    }

    @Test fun providerPreservesUnrelatedEventsAndReminders() = runBlocking {
        val target = DeviceCalendarGateway(app).targets().single {it.id == calendarId.toString()}
        app.calendar.store.connect(target, false, allowNewAfterRestore = true)
        app.calendar.engine.drain(CalendarConnection.DEVICE)
        val ownedId = remote().single().first
        resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, ownedId); put(CalendarContract.Reminders.MINUTES, 60)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        })
        val personalId = ContentUris.parseId(requireNotNull(resolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, "관련 없는 개인 일정")
            put(Events.DTSTART, System.currentTimeMillis()); put(Events.DTEND, System.currentTimeMillis() + 3600000)
            put(Events.EVENT_TIMEZONE, "Asia/Seoul")
        })))
        app.repository.appointment(visit.copy(memo = "변경")); app.calendar.engine.drain(CalendarConnection.DEVICE)
        resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES), "${CalendarContract.Reminders.EVENT_ID}=?", arrayOf(ownedId.toString()), null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(60, it.getInt(0))
        }
        app.calendar.store.disconnect(app.calendar.store.dao.connections().first {it.state == CalendarConnection.ACTIVE}.id, true)
        app.calendar.engine.drain(CalendarConnection.DEVICE)
        assertEquals(listOf(personalId to "관련 없는 개인 일정"), remote())
    }

    @Test fun lostOwnershipAndReadOnlyCalendarStopWritesWithoutLosingAppointments() = runBlocking {
        val target = DeviceCalendarGateway(app).targets().single {it.id == calendarId.toString()}
        app.calendar.store.connect(target, false, allowNewAfterRestore = true)
        app.calendar.engine.drain(CalendarConnection.DEVICE)
        val id = remote().single().first
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), ContentValues().apply {
            putNull(Events.CUSTOM_APP_URI); put(Events.TITLE, "외부에서 연결 정보가 바뀐 일정")
        }, null, null)
        app.repository.appointment(visit.copy(memo = "내용 변경")); app.calendar.engine.drain(CalendarConnection.DEVICE)
        assertEquals(listOf(id to "외부에서 연결 정보가 바뀐 일정"), remote())
        assertTrue(app.calendar.store.dao.jobs().single().blocked)
        val connection = app.calendar.store.dao.connections().first {it.state == CalendarConnection.ACTIVE}
        resolver.update(calendarUri(), ContentValues().apply {put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)}, "${Calendars._ID}=?", arrayOf(calendarId.toString()))
        app.calendar.store.reapply(connection.id); app.calendar.engine.drain(CalendarConnection.DEVICE)
        assertEquals(CalendarConnection.AUTH, app.calendar.store.dao.connection(connection.id)!!.state)
        assertEquals(1, app.repository.snapshot().appointments.size)
        assertEquals(listOf(id to "외부에서 연결 정보가 바뀐 일정"), remote())
    }
}
