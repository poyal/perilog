package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val p=Product(id="p",name="투석액 1.5%")
    private fun usage(id:String,date:String,q:Int,at:Long=10)=Usage(id,date,listOf(Item(p.id,p.name,q)),"MACHINE",at)
    @Test fun yesterdayUsesCalendarBoundariesAndNeverOlderMissingDays() {
        listOf("2027-01-01" to "2026-12-31","2026-10-01" to "2026-09-30","2024-03-01" to "2024-02-29").forEach{(now,previous)->
            val s=Snapshot(treatments=listOf(Treatment(date="2024-01-01",saved=true)))
            assertEquals(previous,s.yesterdaySummary(now).date)
            assertTrue(s.yesterdaySummary(now).needsMachine)
            assertTrue(s.yesterdaySummary(now).pending.isEmpty())
        }
    }
    @Test fun yesterdayHidesOnlyWhenAllEntriesAreCompleteAndListsMultipleDrafts() {
        val machine=Treatment(id="machine",date="2026-09-30",saved=true,usageConfirmed=true,
            weightGrams=62000,systolic=120,diastolic=80,initialDrain=2300,machineUf=600)
        val manual=Treatment(id="manual",date=machine.date,kind="MANUAL",saved=true,usageConfirmed=true)
        val complete=Snapshot(treatments=listOf(machine,manual))
        assertFalse(complete.yesterdaySummary("2026-10-01").visible)
        val first=manual.copy(id="draft-1",saved=false,createdAt=1)
        val second=manual.copy(id="draft-2",saved=false,createdAt=2)
        val s=complete.copy(drafts=listOf(Draft(first.id,first),Draft(second.id,second)))
        assertEquals(listOf(first.id,second.id),s.yesterdaySummary("2026-10-01").pending.map{it.id})
        assertFalse(s.yesterdaySummary("2026-10-01").needsMachine)
        assertTrue(s.yesterdaySummary("2026-10-02").needsMachine)
    }
    @Test fun versionOneBackupWithoutNewVersionMetadataStillLoads() {
        val legacy="""{"formatVersion":1,"appVersion":"1.0.0","products":[{"id":"p","name":"옛 품목"}],"templates":[{"id":"t","name":"밤 구성","items":[{"productId":"p","name":"옛 품목","quantity":2}]}]}"""
        val s=codec.decodeFromString<Snapshot>(legacy)
        validate(s)
        assertEquals("1.0.0",s.appVersion)
        assertEquals(2,s.templates.single().items.single().quantity)
        assertEquals(2000,s.preferences.basisOn("2026-10-01"))
    }
    @Test fun expiryAlertBoundariesRespectZeroAndUndated() {
        assertEquals("사용기한 7일 남음",expiryState("2026-10-08",1,"2026-10-01",7))
        assertNull(expiryState("2026-10-09",1,"2026-10-01",7))
        assertEquals("오늘까지",expiryState("2026-10-01",1,"2026-10-01",0))
        assertEquals("사용기한 지남",expiryState("2026-09-30",1,"2026-10-01",7))
        assertNull(expiryState(null,10,"2026-10-01",7))
        assertNull(expiryState("2026-10-01",0,"2026-10-01",7))
    }
    @Test fun totalsUsePreviousSettingAndPreserveMissingAndNegative() {
        val t=Treatment(initialDrain=2300,machineUf=600,basisMl=2000)
        assertEquals(900,t.totalUf())
        assertEquals(-200,t.copy(initialDrain=1800,machineUf=0).totalUf())
        assertNull(t.copy(machineUf=null).totalUf())
        assertNull(t.copy(basisMl=null).totalUf())
        assertEquals(200,t.copy(kind="MANUAL",manualDrain=2200,previousFill=2000,drainUnit="mL").totalUf())
        assertNull(t.copy(kind="MANUAL",manualDrain=2200,previousFill=2000,drainUnit="g").totalUf())
    }
    @Test fun manualInventoryOnlyCompletesButDoesNotCompleteMachineDay() {
        val t=Treatment(kind="MANUAL",saved=true,usageConfirmed=true)
        assertTrue(t.complete());assertFalse(listOf(t).dayComplete(t.date))
        val machine=Treatment(date=t.date,saved=true,usageConfirmed=true,weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600)
        assertTrue(listOf(t,machine).dayComplete(t.date))
        assertFalse(listOf(t,machine,t.copy(id="draft",saved=false)).dayComplete(t.date))
    }
    @Test fun previousCompositionUsesActualSameTypeAndNeverFuture() {
        val old=Treatment(date="2026-09-29",saved=true,usageConfirmed=true,items=listOf(Item("p","edited",3)),weightGrams=61000)
        val future=old.copy(id="future",date="2026-10-01",items=listOf(Item("p","future",9)))
        val manual=old.copy(id="manual",kind="MANUAL",date="2026-09-30")
        val s=Snapshot(treatments=listOf(old,future,manual))
        assertEquals(3,newTreatment(s,"MACHINE","2026-09-30").items.single().quantity)
        assertTrue(newTreatment(s,"MACHINE","2026-09-28").items.isEmpty())
        assertNull(newTreatment(s,"MACHINE","2026-09-30").initialDrain)
        assertEquals(manual.items,newTreatment(s,"MANUAL","2026-09-30").items)
    }
    @Test fun fefoExcludesExpiredAndKeepsUndatedLast() {
        val r=Receipt(date="2026-09-01",createdAt=1,lines=listOf(
            ReceiptLine("expired","p",2,"2026-09-30"),ReceiptLine("later","p",2,"2026-10-10"),
            ReceiptLine("earlier","p",2,"2026-10-05"),ReceiptLine("undated","p",2)))
        val result=inventory(Snapshot(products=listOf(p),receipts=listOf(r),usages=listOf(usage("u","2026-10-01",3))),"2026-10-01")
        assertEquals(listOf("earlier","later"),result.allocations.map{it.lotId})
        assertEquals(5,result.products.getValue("p").balance)
        assertEquals(2,result.products.getValue("p").lots.find{it.id=="expired"}!!.remaining)
    }
    @Test fun usageBeforeStockCountCannotDebitObservedCurrentStock() {
        val s=Snapshot(products=listOf(p),counts=listOf(StockCount(productId="p",date="2026-10-01",quantity=10,createdAt=20)),
            usages=listOf(usage("historic","2026-09-30",9),usage("later","2026-10-02",2)))
        assertEquals(8,inventory(s,"2026-10-02").products.getValue("p").balance)
        assertEquals(0,inventory(s,"2026-10-02").products.getValue("p").unallocated)
    }
    @Test fun shortageDoesNotInventReceiptOrNegativeBatch() {
        val s=Snapshot(products=listOf(p),receipts=listOf(Receipt(date="2026-10-01",createdAt=1,lines=listOf(ReceiptLine(productId="p",quantity=1)))),usages=listOf(usage("u","2026-10-01",3)))
        val stock=inventory(s,"2026-10-01").products.getValue("p")
        assertEquals(-2,stock.balance);assertEquals(2,stock.unallocated);assertEquals(0,stock.lots.single().remaining)
        val unknown=inventory(Snapshot(products=listOf(p),usages=s.usages),"2026-10-01").products.getValue("p")
        assertFalse(unknown.registered)
    }
    @Test fun cancelledUsageAndReceiptAreAuditableButDoNotMoveStock() {
        val r=Receipt(date="2026-10-01",createdAt=1,lines=listOf(ReceiptLine(productId="p",quantity=4)))
        val u=usage("u","2026-10-01",2)
        assertEquals(4,inventory(Snapshot(products=listOf(p),receipts=listOf(r),usages=listOf(u.copy(cancelled=true))),"2026-10-01").products.getValue("p").balance)
        assertEquals(-2,inventory(Snapshot(products=listOf(p),receipts=listOf(r.copy(cancelled=true)),usages=listOf(u)),"2026-10-01").products.getValue("p").balance)
    }
    @Test fun backupRoundTripKeepsOriginalUnitsAndBasis() {
        val t=Treatment(kind="MANUAL",date="2026-10-01",manualDrain=2195,drainUnit="kg",basisMl=null)
        val s=Snapshot(products=listOf(p),treatments=listOf(t),drafts=listOf(Draft("draft",t.copy(id="draft"))))
        validate(s)
        assertEquals(s,codec.decodeFromString<Snapshot>(codec.encodeToString(s)))
        assertEquals(2000,Preferences(basis=listOf(Basis("1970-01-01",2000),Basis("2026-10-02",1800))).basisOn("2026-10-01"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsBrokenReferences() { validate(Snapshot(usages=listOf(usage("u","2026-10-01",1)))) }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnsupportedBackupVersion() { validate(Snapshot(formatVersion=2)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsDuplicateIdentifiers() { validate(Snapshot(products=listOf(p,p))) }
}
