package com.poyal.perilog.widget

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import java.time.*

fun upcomingWidgetAppointments(s: Snapshot, now: LocalDateTime): List<Appointment> =
    s.appointments.filter { it.nextAt(now) != null }
        .sortedWith(compareBy<Appointment> { it.nextAt(now) }.thenBy { it.createdAt }.thenBy { it.id })

fun nextWidgetBoundary(s: Snapshot, now: ZonedDateTime): ZonedDateTime {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    val appointment = s.appointments.mapNotNull { it.nextAt(now.toLocalDateTime()) }
        .minOrNull()?.atZone(now.zone)?.plusSeconds(1)
    return if (appointment != null && appointment < midnight) appointment else midnight
}

/** Ignore unrelated inventory and audit changes when scheduling widget refreshes. */
data class WidgetDataKey(val today: DailyProgress, val yesterday: DailyProgress,
    val appointments: List<Appointment>, val mode: String)
fun widgetDataKey(s: Snapshot, date: LocalDate = LocalDate.now()) = WidgetDataKey(
    s.dailyProgress(date.toString()), s.dailyProgress(date.minusDays(1).toString()), s.appointments,
    s.preferences.darkMode)
