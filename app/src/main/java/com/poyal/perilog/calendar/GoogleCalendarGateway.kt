package com.poyal.perilog.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class CalendarHttpResponse(val status: Int, val body: JsonObject)
fun interface CalendarHttp { suspend fun call(method: String, path: String, body: JsonObject?): CalendarHttpResponse }

/** Fixed origin, bounded responses, no redirects, no tokens in storage or diagnostics. */
class GoogleCalendarHttp(private val account: String, private val auth: GoogleCalendarAuth): CalendarHttp {
    override suspend fun call(method: String, path: String, body: JsonObject?): CalendarHttpResponse = withContext(Dispatchers.IO) {
        var token = auth.accessToken(account)
        repeat(2) { attempt ->
            val connection = URL("https://www.googleapis.com/calendar/v3/$path").openConnection() as HttpURLConnection
            val response = try {
                connection.requestMethod = method; connection.connectTimeout = 15000; connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                if(body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if(status in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while(true) {
                        val n = input.read(buffer); if(n < 0) break
                        if(output.size() + n > 2 * 1024 * 1024) throw CalendarRetryException("구글 응답을 처리할 수 없어요. 다시 시도해 주세요.")
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                } ?: byteArrayOf()
                val json = if(bytes.isEmpty()) buildJsonObject {} else Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                CalendarHttpResponse(status, json)
            } finally { connection.disconnect() }
            if(response.status != 401 || attempt == 1) return@withContext response
            auth.clear(token); token = auth.accessToken(account)
        }
        error("Unreachable")
    }
}

class GoogleCalendarGateway(private val account: String, private val owner: String, private val http: CalendarHttp): CalendarGateway {
    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun root(c: CalendarConnection) = "calendars/${encode(c.calendarId)}/events"
    private fun JsonObject.text(key: String) = get(key)?.jsonPrimitive?.contentOrNull
    private fun checked(r: CalendarHttpResponse): JsonObject {
        when {
            r.status in 200..299 -> return r.body
            r.status == 401 -> throw CalendarAccessException("구글 계정을 다시 연결해 주세요.")
            r.status == 429 || r.status >= 500 || (r.status == 403 &&
                r.body["error"]?.jsonObject?.get("errors")?.jsonArray?.any {
                    it.jsonObject.text("reason") in listOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded") } == true) ->
                throw CalendarRetryException("구글 요청이 잠시 제한되었어요. 자동으로 다시 시도해요.")
            r.status == 403 || r.status == 404 -> throw CalendarAccessException("구글 캘린더 접근 권한과 API 설정을 확인해 주세요.")
            else -> throw CalendarConflictException("구글 캘린더가 요청을 처리하지 못했어요. 연결과 일정 정보를 확인해 주세요.")
        }
    }

    override suspend fun targets(): List<CalendarTarget> {
        val result = mutableListOf<CalendarTarget>()
        var page: String? = null
        do {
            val body = checked(http.call("GET", "users/me/calendarList?minAccessRole=writer&maxResults=250" +
                (page?.let { "&pageToken=${encode(it)}" } ?: ""), null))
            body["items"]?.jsonArray?.forEach {
                val item = it.jsonObject
                if(item.text("accessRole") in listOf("owner", "writer")) result += CalendarTarget(CalendarConnection.GOOGLE,
                    account, "com.google", requireNotNull(item.text("id")), item.text("summary") ?: "구글 캘린더")
            }
            page = body.text("nextPageToken")
        } while(page != null)
        return result
    }

    override suspend fun verify(connection: CalendarConnection) {
        val body = checked(http.call("GET", "users/me/calendarList/${encode(connection.calendarId)}", null))
        if(body.text("accessRole") !in listOf("owner", "writer")) throw CalendarAccessException("구글 캘린더 쓰기 권한을 확인해 주세요.")
    }

    private fun owned(event: JsonObject, link: CalendarLink): Boolean {
        val p = event["extendedProperties"]?.jsonObject?.get("private")?.jsonObject ?: return false
        return p.text("perilogApp") == owner && p.text("perilogAppointment") == link.appointmentId
    }

    override suspend fun find(connection: CalendarConnection, link: CalendarLink): String? {
        val id = link.remoteId ?: link.createKey
        val response = http.call("GET", "${root(connection)}/${encode(id)}", null)
        if(response.status !in listOf(404, 410)) {
            val event = checked(response)
            if(event.text("status") != "cancelled") {
                if(!owned(event, link)) throw CalendarConflictException("기존 구글 일정의 연결 정보가 다르므로 변경하지 않았어요.")
                return event.text("id") ?: id
            }
        }
        // Stable appointment identity reconnects after reinstall/backup restore.
        val matches = mutableListOf<String>()
        var page: String? = null
        do {
            val responseBody = checked(http.call("GET", "${root(connection)}?showDeleted=false&maxResults=250" +
                "&privateExtendedProperty=${encode("perilogApp=$owner")}" +
                "&privateExtendedProperty=${encode("perilogAppointment=${link.appointmentId}")}" +
                (page?.let { "&pageToken=${encode(it)}" } ?: ""), null))
            responseBody["items"]?.jsonArray?.forEach {
                val event = it.jsonObject
                if(event.text("status") != "cancelled" && owned(event, link)) event.text("id")?.let(matches::add)
            }
            page = responseBody.text("nextPageToken")
        } while(page != null)
        if(matches.size > 1) throw CalendarConflictException("같은 예약의 구글 일정이 여러 개예요. 중복 일정을 정리해 주세요.")
        return matches.singleOrNull()
    }

    private fun payload(link: CalendarLink, value: CalendarPayload, creating: Boolean) = buildJsonObject {
        if(creating) put("id", link.createKey)
        put("summary", value.title); put("description", value.description)
        putJsonObject("start") { put("date", JsonNull); put("dateTime", Instant.ofEpochMilli(value.startMillis).toString()); put("timeZone", value.zoneId) }
        putJsonObject("end") { put("date", JsonNull); put("dateTime", Instant.ofEpochMilli(value.endMillis).toString()); put("timeZone", value.zoneId) }
        put("recurrence", JsonNull)
        putJsonObject("extendedProperties") { putJsonObject("private") {
            put("perilogApp", owner); put("perilogAppointment", link.appointmentId)
        } }
    }

    override suspend fun create(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload): String {
        val response = http.call("POST", root(connection), payload(link, payload, true))
        if(response.status == 409) {
            val previous = http.call("GET", "${root(connection)}/${link.createKey}", null)
            if(previous.status == 410) throw CalendarMissingException()
            val event = checked(previous)
            if(event.text("status") == "cancelled") throw CalendarMissingException()
            if(!owned(event, link)) throw CalendarConflictException("구글 일정 식별자가 중복되어 전송을 멈췄어요.")
            update(connection, link.copy(remoteId = link.createKey), payload)
            return link.createKey
        }
        return checked(response).text("id") ?: throw CalendarRetryException("구글 일정 생성 결과를 확인할 수 없어요.")
    }

    override suspend fun update(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload) {
        val response = http.call("PATCH", "${root(connection)}/${encode(requireNotNull(link.remoteId))}", payload(link, payload, false))
        if(response.status == 404 || response.status == 410) throw CalendarMissingException()
        checked(response)
    }

    override suspend fun delete(connection: CalendarConnection, link: CalendarLink) {
        val response = http.call("DELETE", "${root(connection)}/${encode(requireNotNull(link.remoteId))}", null)
        if(response.status !in listOf(404, 410)) checked(response)
    }
}
