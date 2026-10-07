package com.poyal.perilog.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceCalendarGateway(private val context: Context): CalendarGateway {
    private val resolver get() = context.contentResolver
    private fun marker(link: CalendarLink) = "perilog://appointments/${android.net.Uri.encode(link.appointmentId)}"

    override suspend fun targets(): List<CalendarTarget> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CalendarTarget>()
        val columns = arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_DISPLAY_NAME)
        val cursor = resolver.query(Calendars.CONTENT_URI, columns,
            "${Calendars.CALENDAR_ACCESS_LEVEL}>=?", arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)
            ?: throw CalendarAccessException("휴대폰 캘린더에 접근할 수 없어요.")
        cursor.use { while(it.moveToNext()) result += CalendarTarget(CalendarConnection.DEVICE,
            it.getString(1) ?: "", it.getString(2) ?: "", it.getLong(0).toString(), it.getString(3) ?: "캘린더") }
        result.sortedWith(compareBy<CalendarTarget> {it.account}.thenBy {it.name})
    }

    override suspend fun verify(connection: CalendarConnection) {
        if(targets().none { it.id == connection.calendarId && it.account == connection.account && it.accountType == connection.accountType })
            throw CalendarAccessException("연결한 캘린더가 없거나 쓰기 권한이 없어요. 캘린더를 다시 선택해 주세요.")
    }

    override suspend fun find(connection: CalendarConnection, link: CalendarLink): String? = withContext(Dispatchers.IO) {
        val columns = arrayOf(Events._ID, Events.CALENDAR_ID, Events.CUSTOM_APP_PACKAGE, Events.CUSTOM_APP_URI, Events.DELETED)
        // Never trust a device-local numeric ID on its own (accounts can be removed/re-added).
        link.remoteId?.let { id ->
            val cursor = resolver.query(Events.CONTENT_URI, columns, "${Events._ID}=?", arrayOf(id), null)
                ?: throw CalendarRetryException("캘린더 일정 조회에 실패했어요.")
            cursor.use {
                if(it.moveToFirst() && it.getInt(4) == 0) {
                    if(it.getLong(1).toString() != connection.calendarId || it.getString(2) != context.packageName || it.getString(3) != marker(link))
                        throw CalendarConflictException("기존 일정의 연결 정보를 확인할 수 없어요. 캘린더에서 기존 일정을 확인해 주세요.")
                    return@withContext id
                }
            }
        }
        val matches = mutableListOf<String>()
        val cursor = resolver.query(Events.CONTENT_URI, columns,
            "${Events.CALENDAR_ID}=? AND ${Events.CUSTOM_APP_PACKAGE}=? AND ${Events.CUSTOM_APP_URI}=? AND ${Events.DELETED}=0",
            arrayOf(connection.calendarId, context.packageName, marker(link)), null)
            ?: throw CalendarRetryException("캘린더 일정 조회에 실패했어요.")
        cursor.use { while(it.moveToNext()) matches += it.getLong(0).toString() }
        if(matches.size > 1) throw CalendarConflictException("같은 예약에 연결된 일정이 여러 개 있어요. 캘린더에서 중복 일정을 정리해 주세요.")
        matches.singleOrNull()
    }

    private fun values(payload: CalendarPayload) = ContentValues().apply {
        put(Events.TITLE, payload.title); put(Events.DESCRIPTION, payload.description)
        put(Events.DTSTART, payload.startMillis); put(Events.DTEND, payload.endMillis)
        put(Events.EVENT_TIMEZONE, payload.zoneId); put(Events.EVENT_END_TIMEZONE, payload.zoneId)
        put(Events.ALL_DAY, 0); putNull(Events.RRULE); putNull(Events.RDATE); putNull(Events.DURATION)
        putNull(Events.EXRULE); putNull(Events.EXDATE)
    }

    override suspend fun create(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload): String = withContext(Dispatchers.IO) {
        if(link.attempted) throw CalendarConflictException("이전 전송의 완료 여부가 불확실해요. 캘린더에서 중복 여부를 확인한 뒤 새 전송을 선택해 주세요.")
        val values = values(payload).apply {
            put(Events.CALENDAR_ID, connection.calendarId.toLong())
            put(Events.CUSTOM_APP_PACKAGE, context.packageName); put(Events.CUSTOM_APP_URI, marker(link))
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: throw CalendarRetryException("캘린더 일정 생성 결과를 확인할 수 없어요.")
        ContentUris.parseId(uri).toString()
    }

    override suspend fun update(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload) = withContext(Dispatchers.IO) {
        // Keep ownership checks in the write selection as well as find(), guarding ID reuse.
        val changed = resolver.update(Events.CONTENT_URI, values(payload), ownedSelection, ownedArgs(connection, link))
        if(changed == 0) {
            if(find(connection, link) != null) throw CalendarRetryException("일정을 갱신하지 못했어요. 다시 시도해 주세요.")
            throw CalendarMissingException()
        }
        Unit
    }

    override suspend fun delete(connection: CalendarConnection, link: CalendarLink) = withContext(Dispatchers.IO) {
        resolver.delete(Events.CONTENT_URI, ownedSelection, ownedArgs(connection, link))
        Unit
    }
    private val ownedSelection = "${Events._ID}=? AND ${Events.CALENDAR_ID}=? AND ${Events.CUSTOM_APP_PACKAGE}=? AND ${Events.CUSTOM_APP_URI}=? AND ${Events.DELETED}=0"
    private fun ownedArgs(c: CalendarConnection, link: CalendarLink) = arrayOf(requireNotNull(link.remoteId), c.calendarId, context.packageName, marker(link))
}
