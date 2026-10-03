package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import com.poyal.perilog.widget.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class WidgetDataTest {
    private val date="2026-10-03"
    private val complete=Treatment(id="machine",date=date,saved=true,usageConfirmed=true,weightGrams=62000,
        systolic=120,diastolic=80,initialDrain=0,machineUf=0)
    @Test fun onlySavedStagesCountAndNewDraftResumesWithoutConfirmingUsage() {
        assertEquals(0,Snapshot().dailyProgress(date).count)
        val draft=complete.copy(id="draft",saved=false)
        val progress=Snapshot(drafts=listOf(Draft(draft.id,draft))).dailyProgress(date)
        assertEquals(0,progress.count);assertEquals("draft",progress.resume?.id)
        assertEquals(2,Snapshot(treatments=listOf(complete.copy(initialDrain=null))).dailyProgress(date).count)
        val done=Snapshot(treatments=listOf(complete)).dailyProgress(date)
        assertEquals(3,done.count);assertTrue(done.complete);assertEquals("기록 확인",done.action)
    }
    @Test fun unfinishedManualResumesWithoutReducingMachineThreeStages() {
        val manual=Treatment(id="manual",date=date,kind="MANUAL",saved=true,usageConfirmed=false)
        val day=Snapshot(treatments=listOf(complete,manual)).dailyProgress(date)
        assertEquals(3,day.count);assertFalse(day.complete);assertEquals("manual",day.resume?.id)
        assertEquals("추가투석 이어쓰기",day.action)
        val previous=complete.copy(id="yesterday",date="2026-10-02",machineUf=null)
        assertEquals("yesterday",Snapshot(treatments=listOf(complete,previous)).dailyProgress(previous.date).resume?.id)
    }
    @Test fun pendingEditDoesNotUndoSavedStages() {
        val s=Snapshot(treatments=listOf(complete),drafts=listOf(Draft(complete.id,complete.copy(weightGrams=null))))
        assertEquals(3,s.dailyProgress(date).count)
    }
    @Test fun appointmentsStayUntilLastDepartmentAndReorderByRemainingTime() {
        val a=Department(id="a",name="신장내과");val b=Department(id="b",name="내분비내과")
        val multi=Appointment(id="multi",date=date,departments=listOf(a,b),departmentTimes=mapOf("a" to "09:00","b" to "14:00"))
        val single=Appointment(id="single",date=date,time="11:00")
        val s=Snapshot(appointments=listOf(multi,single))
        assertEquals(listOf("multi","single"),upcomingWidgetAppointments(s,LocalDateTime.parse("${date}T08:59")).map{it.id})
        assertEquals(listOf("single","multi"),upcomingWidgetAppointments(s,LocalDateTime.parse("${date}T09:01")).map{it.id})
        assertEquals(listOf("multi"),upcomingWidgetAppointments(s,LocalDateTime.parse("${date}T14:00")).map{it.id})
        assertTrue(upcomingWidgetAppointments(s,LocalDateTime.parse("${date}T14:00:01")).isEmpty())
        val now=ZonedDateTime.parse("${date}T08:00:00+09:00[Asia/Seoul]")
        assertEquals(now.withHour(9).plusSeconds(1),nextWidgetBoundary(s,now))
    }
    @Test fun midnightUsesLocalCalendarAcrossYearAndDaylightSaving() {
        listOf("2026-12-31T23:59:00+09:00[Asia/Seoul]","2026-03-08T00:30:00-05:00[America/New_York]").forEach {
            val now=ZonedDateTime.parse(it)
            assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(now.zone),nextWidgetBoundary(Snapshot(),now))
        }
    }
    @Test fun widgetRoutesRejectFutureOrMalformedDateAndKeepDisplayedOlderDate() {
        val today=LocalDate.parse(date)
        assertTrue(WidgetTarget("record","2026-10-02").valid(today))
        assertFalse(WidgetTarget("record","2026-10-04").valid(today))
        assertFalse(WidgetTarget("record","2026-2-31").valid(today))
        assertFalse(WidgetTarget("appointment","").valid(today))
        assertFalse(WidgetTarget("unknown").valid(today))
    }
}
