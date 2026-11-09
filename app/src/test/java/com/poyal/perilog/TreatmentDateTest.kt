package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import org.junit.Assert.*
import org.junit.Test

class TreatmentDateTest {
    private val day="2026-11-02"
    @Test fun candidatesKeepDateAndKindSeparateAndOrderNewestFirst() {
        val older=Treatment(id="older",date=day,kind="MANUAL",createdAt=1)
        val newer=older.copy(id="newer",createdAt=2)
        val s=Snapshot(treatments=listOf(older,newer,older.copy(id="other-kind",kind="MACHINE"),
            older.copy(id="other-day",date="2026-11-01")))
        assertEquals(listOf(newer,older),s.treatmentsOnDate(day,"MANUAL"))
        assertEquals(listOf("other-kind"),s.treatmentsOnDate(day,"MACHINE").map{it.id})
        assertTrue(s.treatmentsOnDate("2026-11-03","MANUAL").isEmpty())
    }
    @Test fun savedIdentityWinsOverLegacyDraftEvenIfDraftHasAnotherDate() {
        val saved=Treatment(id="saved",date=day,saved=true,usageConfirmed=true,systolic=120)
        val draft=Treatment(id="draft",date=day,saved=true,usageConfirmed=true,systolic=115)
        val s=Snapshot(treatments=listOf(saved),drafts=listOf(Draft(saved.id,saved.copy(date="2026-11-01",systolic=90)),Draft(draft.id,draft)))
        val candidates=s.treatmentsOnDate(day,"MACHINE")
        assertEquals(saved,candidates.single{it.id==saved.id})
        val legacy=candidates.single{it.id==draft.id}
        assertEquals(115,legacy.systolic);assertFalse(legacy.saved);assertFalse(legacy.usageConfirmed)
        assertTrue(s.treatmentsOnDate("2026-11-01","MACHINE").isEmpty())
        assertEquals(2,s.drafts.size)
    }
    @Test fun equalCreationTimesHaveStableOrderingAcrossSnapshotOrder() {
        val a=Treatment(id="a",date=day,createdAt=1)
        val b=a.copy(id="b")
        assertEquals(listOf(a,b),Snapshot(treatments=listOf(b,a)).treatmentsOnDate(day,"MACHINE"))
        assertEquals(listOf(a,b),Snapshot(treatments=listOf(a,b)).treatmentsOnDate(day,"MACHINE"))
    }
}
