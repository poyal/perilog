package com.poyal.perilog.domain

import com.poyal.perilog.data.*

/** The home screen and widgets must agree about saved stages and which record to resume. */
data class DailyProgress(
    val date: String, val vitality: Boolean, val usage: Boolean, val treatment: Boolean,
    val complete: Boolean, val machine: Treatment?, val resume: Treatment?
) {
    val count get() = listOf(vitality, usage, treatment).count { it }
    val action: String get() = when {
        complete -> "기록 확인"
        resume?.kind == "MANUAL" -> "추가투석 이어쓰기"
        machine == null -> "기록 시작"
        else -> "이어서 입력"
    }
}

fun Snapshot.dailyProgress(date: String): DailyProgress {
    val entries = visibleRecords()
    val day = entries.filter { it.date == date }
    val machine = day.find { it.kind == "MACHINE" && !it.complete() } ?: day.find { it.kind == "MACHINE" }
    val resume = if (machine == null || !machine.complete()) machine else day.firstOrNull { !it.complete() } ?: machine
    return DailyProgress(date,
        machine?.let { it.saved && it.weightGrams != null && it.systolic != null && it.diastolic != null } == true,
        machine?.usageConfirmed == true,
        machine?.let { it.saved && it.initialDrain != null && it.machineUf != null } == true,
        entries.dayComplete(date), machine, resume)
}
