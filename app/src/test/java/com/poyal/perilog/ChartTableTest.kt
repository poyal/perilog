package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.ui.*
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ChartTableTest {
    @Test fun dateRangesKeepCalendarDatesAcrossTimeZonesAndBoundaries() {
        val original=TimeZone.getDefault()
        try {
            listOf("Asia/Seoul","America/Los_Angeles","Pacific/Kiritimati").forEach{zone->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf("2024-02-29","2026-12-31","2027-01-01").forEach{date->
                    val day=LocalDate.parse(date)
                    assertEquals(day,pickerDate(day.pickerMillis()))
                }
            }
        } finally {TimeZone.setDefault(original)}
        assertTrue(rangeLabel(LocalDate.parse("2024-02-28"),LocalDate.parse("2024-03-01")).endsWith("3일"))
        assertTrue(rangeLabel(LocalDate.parse("2026-12-31"),LocalDate.parse("2027-01-01")).endsWith("2일"))
        assertTrue(rangeLabel(LocalDate.parse("2026-10-03"),LocalDate.parse("2026-10-03")).endsWith("1일"))
        assertEquals(LocalDate.parse("2024-02-29"),inputDate("20240229"))
        assertEquals(LocalDate.parse("2026-12-31"),inputDate("2026-12-31"))
        assertNull(inputDate("20260229"));assertNull(inputDate("20261301"));assertNull(inputDate("2026123"))
        assertNull(inputDate("x20261231"));assertNull(inputDate("-20261231"))
    }
    @Test fun chartAxesIncludeZeroAndNeverCollapse() {
        listOf(listOf(0.0),listOf(900.0),listOf(-200.0),listOf(-200.0,0.0,900.0)).forEach{values->
            val axis=chartDomain(values,true)
            assertTrue(axis.lower<=0 && axis.upper>=0 && axis.upper>axis.lower)
            assertTrue(values.all{it in axis.lower..axis.upper})
        }
        val weight=chartDomain(listOf(62.1,62.3),false)
        assertTrue(weight.lower>61 && weight.upper<63)
        assertTrue(chartDomain(listOf(62.0),false).upper>62)
    }
    @Test fun duplicateDatesStaySeparateAndMissingDaysBreakLines() {
        val a=Treatment(id="a",date="2026-10-01")
        val b=a.copy(id="b")
        val c=a.copy(id="c",date="2026-10-03")
        val x=chartPositions(listOf(a,b,c),LocalDate.parse(a.date),LocalDate.parse(c.date))
        assertTrue(x[0]<x[1] && x[1]<x[2]);assertTrue(x.all{it in 0.0..1.0})
        assertFalse(chartConnects(a.date,c.date));assertFalse(chartConnects(null,a.date))
        assertTrue(chartConnects(a.date,b.date));assertTrue(chartConnects(a.date,"2026-10-02"))
        assertEquals(.5,chartPositions(listOf(a),LocalDate.parse(a.date),LocalDate.parse(a.date)).single(),.0001)
    }
    @Test fun tablePreservesUnitsNullZeroNegativeAndDrafts() {
        val machine=Treatment(id="machine",date="2026-10-01",weightGrams=62250,initialDrain=1800,machineUf=0,basisMl=2000)
        val values=recordValues(machine)
        assertEquals("62.25",values[1]);assertEquals("—/—",values[2]);assertEquals("0",values[4]);assertEquals("-200",values[5])
        assertEquals("2.195 kg",recordValues(Treatment(kind="MANUAL",manualDrain=2195,drainUnit="kg"))[6])
        assertEquals("2195 g",recordValues(Treatment(kind="MANUAL",manualDrain=2195,drainUnit="g"))[6])
        assertEquals("2195 mL",recordValues(Treatment(kind="MANUAL",manualDrain=2195,drainUnit="mL"))[6])
        val f=RecordFilters().apply{period.value=true;from.value="2026-10-01";to.value="2026-10-01"}
        val manual=Treatment(id="manual",date=machine.date,kind="MANUAL",saved=true)
        val s=Snapshot(treatments=listOf(machine,manual,machine.copy(id="outside",date="2026-09-30")),
            drafts=listOf(Draft("draft",machine.copy(id="draft"))))
        assertEquals(setOf("machine","manual","draft"),filterRecords(s,f).map{it.id}.toSet())
        f.type.value="추가투석";assertEquals(listOf("manual"),filterRecords(s,f).map{it.id})
    }
}
