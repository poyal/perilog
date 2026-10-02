package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime
import kotlinx.serialization.encodeToString

class AppointmentTest {
    private val now=LocalDateTime.parse("2026-10-02T10:00:00")
    private fun booking(id:String,time:String,date:String="2026-10-02",createdAt:Long=1)=Appointment(id,date,time,createdAt=createdAt)

    @Test fun closestUpcomingAdvancesAtTimeBoundaryAndRetainsPastEntries() {
        val earlier=booking("past","09:59")
        val current=booking("current","10:00")
        val later=booking("later","14:00")
        val list=listOf(later,earlier,current)
        assertEquals(current,nextAppointment(list,now))
        assertEquals(later,nextAppointment(list,now.plusSeconds(1)))
        assertNull(nextAppointment(list,now.plusDays(1)))
        assertNull(nextAppointment(emptyList(),now))
        assertEquals(3,list.size)
    }
    @Test fun sameTimeUsesStableOrderAndDayLabelUsesCalendarDates() {
        val first=booking("a","11:00")
        assertEquals(first,nextAppointment(listOf(booking("z","11:00",createdAt=2),booking("b","11:00"),first),now))
        assertEquals("D-day",first.dayLabel(now))
        val tomorrow=booking("tomorrow","00:01","2026-10-03")
        assertEquals("D-1",tomorrow.dayLabel(now.withHour(23).withMinute(59)))
        assertEquals("D-day",tomorrow.dayLabel(now.plusDays(1).withHour(0)))
        assertEquals("D-30",booking("nextMonth","08:00","2026-11-01").dayLabel(now))
        assertEquals("지난 일정",first.dayLabel(now.plusDays(1)))
    }
    @Test fun repeatingBookingCopiesSelectionsAndClearsChangingFields() {
        val original=booking("a","11:00").copy(departments=listOf(Department(name="신장내과"),Department(name="내분비내과",color=0xFF47956E)),
            care=CareTemplate(name="정기 방문",tasks=listOf(CareTask(name="피검사",iconKey="blood"),CareTask(name="투석실 방문",iconKey="dialysis"))),memo="원본 메모")
        val next=original.nextBooking()
        assertNotEquals(original.id,next.id)
        assertEquals(original.departments,next.departments);assertEquals(original.selectedCareItems(),next.careItems);assertNull(next.care)
        assertEquals("",next.date);assertEquals("",next.time);assertEquals("",next.memo)
        assertTrue(next.departmentTimes.isEmpty())
        assertEquals("원본 메모",original.memo)
    }
    @Test fun invalidTimeContactsAndDuplicateSelectionsAreRejected() {
        listOf("24:00","09:60","9:30","12:30:01","").forEach{assertFalse(validAppointmentTime(it))}
        assertTrue(validAppointmentTime("00:00"));assertTrue(validAppointmentTime("23:59"))
        assertTrue(validPhone("032-000-0000"));assertTrue(validPhone("+82 (10) 0000-0000"))
        listOf("","12","010oops1234","++8210").forEach{assertFalse(validPhone(it))}
        val d=Department(name="신장내과")
        assertThrows(IllegalArgumentException::class.java){validate(Snapshot(appointments=listOf(booking("a","10:00").copy(departments=listOf(d,d)))))}
        assertThrows(IllegalArgumentException::class.java){validate(Snapshot(contacts=listOf(Contact(name="연락처",phone="bad"))))}
    }
    @Test fun oldBackupsDefaultToEmptyListsAndUnknownIconKeysCanBeRetained() {
        val old=codec.decodeFromString<Snapshot>("""{"formatVersion":1,"appVersion":"1.0.4"}""")
        assertTrue(old.departments.isEmpty() && old.careTemplates.isEmpty() && old.appointments.isEmpty() && old.contacts.isEmpty())
        val s=Snapshot(careTemplates=listOf(CareTemplate(name="검사",tasks=listOf(CareTask(name="검사 항목",iconKey="future-icon")))))
        validate(s);assertEquals(s,codec.decodeFromString<Snapshot>(codec.encodeToString(s)))
    }
    @Test fun oldContactBackupDefaultsAndEmojiPermissionsRoundTrip() {
        val old=codec.decodeFromString<Contact>("""{"id":"old","name":"테스트","phone":"010-0000-0000","createdAt":1}""")
        assertEquals("👤",old.emoji);assertTrue(old.allowCall && old.allowSms)
        val edited=old.copy(emoji="🧑‍⚕️",allowCall=false,allowSms=true)
        assertEquals(edited,codec.decodeFromString<Contact>(codec.encodeToString(edited)))
    }
    @Test fun legacyBundlesExpandIntoStableIndividualItemsAndAppointmentSnapshots() {
        val bundle=CareTemplate(id="legacy",name="정기 방문",tasks=listOf(CareTask(id="blood",name="피검사",iconKey="blood"),CareTask(id="injection",name="주사",iconKey="injection")))
        val items=bundle.individualItems()
        assertEquals(listOf("legacy","legacy:injection"),items.map{it.id})
        assertEquals(listOf("피검사","주사"),items.map{it.name});assertTrue(items.all{it.tasks.isEmpty()})
        val original=booking("a","10:00").copy(care=bundle)
        assertEquals(items.map{it.asCareTask()},original.selectedCareItems())
        val next=original.nextBooking()
        assertEquals(original.selectedCareItems(),next.careItems);assertEquals(bundle,original.care)
    }
    @Test fun departmentTimesAdvanceWithinVisitAndDoNotHideRemainingDepartments() {
        val kidney=Department(id="kidney",name="신장내과")
        val eye=Department(id="eye",name="안과")
        val visit=booking("visit","09:00").copy(departments=listOf(eye,kidney),departmentTimes=mapOf("kidney" to "09:00","eye" to "11:00"))
        val other=booking("other","10:30")
        assertEquals(LocalDateTime.parse("2026-10-02T09:00"),visit.at())
        assertEquals(LocalDateTime.parse("2026-10-02T11:00"),visit.endsAt())
        assertEquals(other,nextAppointment(listOf(visit,other),now))
        assertEquals(visit,nextAppointment(listOf(visit,other),now.withHour(10).withMinute(31)))
        assertEquals(visit,nextAppointment(listOf(visit),now.withHour(11)))
        assertNull(nextAppointment(listOf(visit),now.withHour(11).plusSeconds(1)))
        assertEquals("11:00",visit.nextAt(now)?.toLocalTime().toString())
        val repeated=visit.nextBooking()
        assertEquals(visit.departments,repeated.departments)
        assertTrue(repeated.departmentTimes.isEmpty());assertTrue(repeated.departments.all{repeated.departmentTime(it).isEmpty()})
        assertEquals(mapOf("kidney" to "09:00","eye" to "11:00"),visit.departmentTimes)
    }
    @Test fun oldAppointmentBackupKeepsSharedTimeAndNewDepartmentTimesRoundTrip() {
        val old=codec.decodeFromString<Appointment>("""{"id":"legacy","date":"2026-10-02","time":"09:30","departments":[{"id":"kidney","name":"신장내과"},{"id":"eye","name":"안과"}]}""")
        assertTrue(old.departmentTimes.isEmpty());assertTrue(old.departments.all{old.departmentTime(it)=="09:30"})
        val updated=old.copy(departmentTimes=mapOf("kidney" to "08:30","eye" to "11:10"))
        assertEquals(updated,codec.decodeFromString<Appointment>(codec.encodeToString(updated)))
        assertEquals("신장내과",updated.departments.first().name)
        assertEquals("11:10",updated.nextAt(now)?.toLocalTime().toString())
    }
    @Test fun missingInvalidAndUnselectedDepartmentTimesCannotBeSaved() {
        val d=Department(id="kidney",name="신장내과")
        val visit=booking("visit","").copy(departments=listOf(d),departmentTimes=mapOf(d.id to "09:30"))
        validate(Snapshot(appointments=listOf(visit)))
        listOf(visit.copy(departmentTimes=emptyMap()),visit.copy(departmentTimes=mapOf(d.id to "24:00")),
            visit.copy(departmentTimes=mapOf(d.id to "09:30","unknown" to "11:00")),
            visit.copy(departments=emptyList())).forEach {
            assertThrows(IllegalArgumentException::class.java){validate(Snapshot(appointments=listOf(it)))}
        }
    }
}
