package com.poyal.perilog.domain

import com.poyal.perilog.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// A legacy booking uses its common time until a department-specific time is saved.
fun Appointment.departmentTime(d:Department): String = departmentTimes[d.id] ?: time
private fun Appointment.scheduledTimes(): List<LocalDateTime> =
    ((if(departments.isEmpty())listOf(time) else departments.map{departmentTime(it)}) + selectedCareItems().mapNotNull{it.time})
        .map{LocalDateTime.of(LocalDate.parse(date),LocalTime.parse(it))}.sorted()
fun Appointment.at(): LocalDateTime = scheduledTimes().first()
fun Appointment.endsAt(): LocalDateTime = scheduledTimes().last()
fun Appointment.nextAt(now:LocalDateTime): LocalDateTime? = scheduledTimes().firstOrNull{!it.isBefore(now)}
fun nextAppointment(appointments: List<Appointment>, now: LocalDateTime): Appointment? =
    appointments.mapNotNull{a->a.nextAt(now)?.let{a to it}}
        .minWithOrNull(compareBy<Pair<Appointment,LocalDateTime>>{it.second}.thenBy{it.first.createdAt}.thenBy{it.first.id})?.first

fun Appointment.dayLabel(now: LocalDateTime): String {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), LocalDate.parse(date))
    return when { days == 0L -> "D-day"; days > 0 -> "D-$days"; else -> "지난 일정" }
}

fun Appointment.nextBooking(): Appointment = copy(id = newId(), date = "", time = "", memo = "",
    care = null, careItems = selectedCareItems().map{it.copy(time=null)}, departmentTimes=emptyMap(), createdAt = System.currentTimeMillis())

/** Expand old bundles without losing saved task names, icons, or their catalogue identity. */
fun CareTemplate.individualItems(): List<CareTemplate> = if(tasks.isEmpty())listOf(this) else
    tasks.mapIndexed { index,task -> CareTemplate(id=if(index==0)id else "$id:${task.id}",name=task.name,iconKey=task.iconKey) }

fun CareTemplate.asCareTask(): CareTask = CareTask(id=id,name=name,iconKey=iconKey)
fun CareTask.displayLabel(): String = time?.let { "$name $it" } ?: name
fun Appointment.selectedCareItems(): List<CareTask> = careItems.ifEmpty {
    care?.individualItems()?.map { it.asCareTask() } ?: emptyList()
}

fun dialNumber(phone: String): String = phone.filter { it.isDigit() || it == '+' }
fun Snapshot.orderedContacts(): List<Contact> {
    val positions=preferences.contactOrder.withIndex().associate { it.value to it.index }
    return contacts.sortedWith(compareBy<Contact> { positions[it.id] ?: Int.MAX_VALUE }.thenBy { it.createdAt }.thenBy { it.id })
}
fun validPhone(phone: String): Boolean = phone.trim().matches(Regex("\\+?[0-9() \\-]+")) &&
    dialNumber(phone).count { it.isDigit() } in 3..20

fun validAppointmentTime(time: String): Boolean = time.matches(Regex("[0-9]{2}:[0-9]{2}")) &&
    runCatching { LocalTime.parse(time) }.isSuccess

fun Appointment.validTimes(): Boolean =
    selectedCareItems().all { it.time == null || validAppointmentTime(it.time) } &&
    departmentTimes.keys.all{key->departments.any{it.id==key}} &&
        (if(departments.isEmpty())validAppointmentTime(time) else departments.all{validAppointmentTime(departmentTime(it))})

fun validateAppointments(s: Snapshot) {
    fun ids(values: List<String>) = require(values.all { it.isNotBlank() } && values.distinct().size == values.size) {
        "중복되거나 빈 ID가 있습니다."
    }
    fun department(d: Department) { require(d.name.isNotBlank()) { "진료과 이름을 입력해 주세요." } }
    fun care(c: CareTemplate) {
        require(c.name.isNotBlank() && c.iconKey.isNotBlank()) { "치료 항목 이름과 아이콘을 입력해 주세요." }
        ids(c.tasks.map { it.id })
        require(c.tasks.all { it.name.isNotBlank() && it.iconKey.isNotBlank() }) { "치료 항목 이름과 아이콘을 확인해 주세요." }
    }
    ids(s.departments.map { it.id }); ids(s.careTemplates.map { it.id })
    ids(s.appointments.map { it.id }); ids(s.contacts.map { it.id })
    s.departments.forEach(::department); s.careTemplates.forEach(::care)
    s.appointments.forEach {
        LocalDate.parse(it.date)
        require(it.validTimes()) { "예약시간과 지정한 치료 시간을 HH:mm 형식으로 입력해 주세요." }
        ids(it.departments.map { d -> d.id }); it.departments.forEach(::department); it.care?.let(::care)
        ids(it.careItems.map { task -> task.id })
        require(it.careItems.all { task -> task.name.isNotBlank() && task.iconKey.isNotBlank() }) { "치료 항목 이름과 아이콘을 확인해 주세요." }
    }
    s.contacts.forEach { require(it.name.isNotBlank() && validPhone(it.phone)) { "연락처 이름과 전화번호를 확인해 주세요." } }
    require(s.preferences.contactOrder.all { it.isNotBlank() } && s.preferences.contactOrder.distinct().size==s.preferences.contactOrder.size) { "연락처 순서가 올바르지 않습니다." }
}
