package com.poyal.perilog

import com.poyal.perilog.calendar.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GoogleCalendarTest {
    private val c = CalendarConnection(provider = "GOOGLE", account = "test@example.invalid", accountType = "com.google", calendarId = "calendar@example.invalid", calendarName = "검사")
    private val link = CalendarLink("link", c.id, "appointment", remoteId = "remote", createKey = "1234567890abcdef1234567890abcdef")
    private val payload = CalendarPayload("appointment", "병원 방문", "검사용", 1791790200000, 1791801000000, "Asia/Seoul")
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun owned(id: String = "remote") = json("""{"id":"$id","status":"confirmed","extendedProperties":{"private":{"perilogApp":"test.app","perilogAppointment":"appointment"}}}""")
    private class FakeHttp(val responses: MutableList<CalendarHttpResponse>): CalendarHttp {
        val calls = mutableListOf<Triple<String, String, JsonObject?>>()
        override suspend fun call(method: String, path: String, body: JsonObject?): CalendarHttpResponse {
            calls += Triple(method, path, body)
            check(responses.isNotEmpty()) {"Unexpected request: $method $path"}
            return responses.removeAt(0)
        }
    }
    private fun response(code: Int, body: JsonObject = buildJsonObject {}) = CalendarHttpResponse(code, body)

    @Test fun updatesOnlyManagedFieldsPreservingRemindersAndLocation() = runBlocking {
        val http = FakeHttp(mutableListOf(response(200, owned())))
        GoogleCalendarGateway(c.account, "test.app", http).update(c, link, payload)
        val request = http.calls.single()
        assertEquals("PATCH", request.first)
        assertTrue(request.second.contains("calendar%40example.invalid"))
        val body = request.third!!
        assertFalse(body.containsKey("reminders")); assertFalse(body.containsKey("location")); assertFalse(body.containsKey("attendees"))
        assertEquals("병원 방문", body["summary"]!!.jsonPrimitive.content)
        assertEquals("Asia/Seoul", body["start"]!!.jsonObject["timeZone"]!!.jsonPrimitive.content)
    }

    @Test fun duplicateInsertUsesSameIdAndUpdatesTheExistingManagedEvent() = runBlocking {
        val http = FakeHttp(mutableListOf(response(409), response(200, owned(link.createKey)), response(200, owned(link.createKey))))
        assertEquals(link.createKey, GoogleCalendarGateway(c.account, "test.app", http).create(c, link, payload))
        assertEquals(listOf("POST", "GET", "PATCH"), http.calls.map {it.first})
        assertEquals(link.createKey, http.calls.first().third!!["id"]!!.jsonPrimitive.content)
    }

    @Test fun cancelledIdRequiresNewPersistedCreationId() = runBlocking {
        val http = FakeHttp(mutableListOf(response(409), response(200, json("""{"id":"${link.createKey}","status":"cancelled"}"""))))
        assertTrue(runCatching {GoogleCalendarGateway(c.account, "test.app", http).create(c, link, payload)}.exceptionOrNull() is CalendarMissingException)
        val gone = FakeHttp(mutableListOf(response(409), response(410)))
        assertTrue(runCatching {GoogleCalendarGateway(c.account, "test.app", gone).create(c, link, payload)}.exceptionOrNull() is CalendarMissingException)
    }

    @Test fun restoreFindsOnlyPrivateOwnedIdentityAcrossPages() = runBlocking {
        val http = FakeHttp(mutableListOf(response(404), response(200, json("""{"items":[],"nextPageToken":"page two"}""")),
            response(200, buildJsonObject {putJsonArray("items") {add(owned())}})))
        assertEquals("remote", GoogleCalendarGateway(c.account, "test.app", http).find(c, link.copy(remoteId = null)))
        assertTrue(http.calls[1].second.contains("privateExtendedProperty=perilogAppointment%3Dappointment"))
        assertTrue(http.calls[2].second.contains("pageToken=page%20two"))
    }

    @Test fun mismatchedOwnershipStopsWithoutAnyWrite() = runBlocking {
        val http = FakeHttp(mutableListOf(response(200, json("""{"id":"remote","summary":"다른 개인 일정"}"""))))
        assertTrue(runCatching {GoogleCalendarGateway(c.account, "test.app", http).find(c, link)}.exceptionOrNull() is CalendarConflictException)
        assertEquals(listOf("GET"), http.calls.map {it.first})
    }

    @Test fun accountAccessFailureIsNotTreatedAsAnEventDeletion() = runBlocking {
        val http = FakeHttp(mutableListOf(response(403)))
        assertTrue(runCatching {GoogleCalendarGateway(c.account, "test.app", http).verify(c)}.exceptionOrNull() is CalendarAccessException)
        assertEquals(1, http.calls.size)
    }

    @Test fun rateLimitsRemainRetryableWhileDeleteOfAnAbsentEventSucceeds() = runBlocking {
        val http = FakeHttp(mutableListOf(response(403, json("""{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""")), response(410)))
        val gateway = GoogleCalendarGateway(c.account, "test.app", http)
        assertTrue(runCatching {gateway.update(c, link, payload)}.exceptionOrNull() is CalendarRetryException)
        gateway.delete(c, link)
    }

    @Test fun listsWritableCalendarsAcrossPages() = runBlocking {
        val http = FakeHttp(mutableListOf(response(200, json("""{"items":[{"id":"one","summary":"첫번째","accessRole":"owner"},{"id":"readonly","accessRole":"reader"}],"nextPageToken":"two"}""")),
            response(200, json("""{"items":[{"id":"two","summary":"두번째","accessRole":"writer"}]}"""))))
        assertEquals(listOf("one", "two"), GoogleCalendarGateway(c.account, "test.app", http).targets().map {it.id})
    }
}
