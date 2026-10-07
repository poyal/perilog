package com.poyal.perilog.calendar

import androidx.room.*
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.Serializable
import java.time.ZoneId

/** Device-only state: deliberately excluded from Snapshot and JSON backups. */
@Entity(tableName = "calendar_connections")
data class CalendarConnection(
    @PrimaryKey val id: String = newId(),
    val provider: String, val account: String, val accountType: String,
    val calendarId: String, val calendarName: String,
    val zoneId: String = ZoneId.systemDefault().id, val includeMemo: Boolean = false,
    val state: String = ACTIVE, val generation: Long = 1,
    val lastSuccess: Long? = null, val error: String = ""
) {
    companion object {
        const val DEVICE = "DEVICE"; const val GOOGLE = "GOOGLE"
        const val ACTIVE = "ACTIVE"; const val PAUSED = "PAUSED"
        const val AUTH = "AUTH"; const val CLEANING = "CLEANING"; const val OFF = "OFF"
    }
}

@Entity(tableName = "calendar_links", indices = [Index(value = ["connectionId", "appointmentId"], unique = true)])
data class CalendarLink(
    @PrimaryKey val id: String,
    val connectionId: String, val appointmentId: String,
    val remoteId: String? = null, val createKey: String = newId().replace("-", ""),
    val attempted: Boolean = false, val revision: Long = 0,
    val lastSuccess: Long? = null, val deleted: Boolean = false
)

@Entity(tableName = "calendar_jobs")
data class CalendarJob(
    @PrimaryKey val linkId: String, val connectionId: String,
    val generation: Long, val revision: Long,
    /** null means delete; payload is captured in the appointment's transaction. */
    val payload: String?, val attempts: Int = 0,
    val error: String = "", val blocked: Boolean = false
)

@Entity(tableName = "calendar_device_state")
data class CalendarDeviceState(@PrimaryKey val id: Int = 1, val restored: Boolean = false)

@Serializable
data class CalendarPayload(val appointmentId: String, val title: String, val description: String,
    val startMillis: Long, val endMillis: Long, val zoneId: String)

@Serializable
data class CalendarTarget(val provider: String, val account: String, val accountType: String,
    val id: String, val name: String)

fun Appointment.calendarPayload(connection: CalendarConnection): CalendarPayload {
    val zone = ZoneId.of(connection.zoneId)
    fun instant(local: java.time.LocalDateTime): Long {
        require(zone.rules.getValidOffsets(local).isNotEmpty()) { "일광 절약 시간으로 존재하지 않는 예약시간이에요. 시간을 확인해 주세요." }
        return local.atZone(zone).toInstant().toEpochMilli()
    }
    val lines = (if (departments.isEmpty()) listOf(time to "진료 예약")
        else departments.map { departmentTime(it) to it.name }) +
        selectedCareItems().map { (it.time ?: "") to it.name }
    val description = lines.sortedBy { if (it.first.isEmpty()) "99:99" else it.first }
        .joinToString("\n") { (time, name) -> if (time.isEmpty()) name else "$time · $name" } +
        (if (connection.includeMemo && memo.isNotBlank()) "\n\n메모\n$memo" else "") +
        "\n\n페리로그에서 관리하는 병원 일정입니다. 변경은 페리로그에서 해 주세요."
    return CalendarPayload(id,
        if (departments.isEmpty()) "병원 방문" else "병원 방문 · ${departments.joinToString(", ") { it.name }}",
        description, instant(at()), instant(endsAt()) + 30 * 60 * 1000L, zone.id)
}

interface CalendarGateway {
    suspend fun targets(): List<CalendarTarget>
    suspend fun verify(connection: CalendarConnection)
    /** Returns only events positively identified as belonging to this appointment. */
    suspend fun find(connection: CalendarConnection, link: CalendarLink): String?
    suspend fun create(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload): String
    suspend fun update(connection: CalendarConnection, link: CalendarLink, payload: CalendarPayload)
    suspend fun delete(connection: CalendarConnection, link: CalendarLink)
}

class CalendarAccessException(message: String): Exception(message)
class CalendarConflictException(message: String): Exception(message)
class CalendarMissingException: Exception("캘린더 일정이 삭제되었어요.")
class CalendarRetryException(message: String): Exception(message)
