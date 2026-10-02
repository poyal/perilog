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
        assertEquals(0xFF2167B8,s.templates.single().color)
        assertEquals(2000,s.preferences.basisOn("2026-10-01"))
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
    @Test fun newRecordsStayEmptyWithPastSameDayFutureRecordsAndDrafts() {
        val old=Treatment(date="2026-09-29",saved=true,usageConfirmed=true,items=listOf(Item("p","edited",3)),
            weightGrams=61000,systolic=120,diastolic=80,initialDrain=2300,machineUf=600,
            usageTemplateId="night",usageTemplateName="밤 구성",usageTemplateColor=0xFF8772B5)
        val future=old.copy(id="future",date="2026-10-01",items=listOf(Item("p","future",9)))
        val manual=old.copy(id="manual",kind="MANUAL",date="2026-09-30")
        val draft=old.copy(id="draft",date="2026-09-30",saved=false)
        val s=Snapshot(treatments=listOf(old,future,manual),drafts=listOf(Draft(draft.id,draft)),
            preferences=Preferences(basis=listOf(Basis("1970-01-01",2000),Basis("2026-09-30",2100)),lastDrainUnit="kg"))
        listOf("MACHINE","MANUAL").forEach{kind->
            listOf("2026-09-28","2026-09-30","2026-10-02").forEach{date->
                val t=newTreatment(s,kind,date)
                assertEquals(date,t.date);assertEquals(kind,t.kind)
                assertNull(t.weightGrams);assertNull(t.systolic);assertNull(t.diastolic)
                assertNull(t.initialDrain);assertNull(t.machineUf);assertNull(t.manualDrain)
                assertNull(t.previousFill);assertNull(t.dwellMinutes);assertNull(t.sourceDate)
                assertTrue(t.items.isEmpty());assertFalse(t.usageConfirmed);assertFalse(t.saved)
                assertNull(t.usageTemplateId);assertNull(t.usageTemplateName);assertNull(t.usageTemplateColor)
                assertEquals(if(kind=="MACHINE")s.preferences.basisOn(date)else null,t.basisMl)
                assertEquals(if(kind=="MANUAL")"kg"else"mL",t.drainUnit)
            }
        }
    }
    @Test fun chosenCompositionSurvivesQuantityChangesDuplicateTemplatesAndBackup() {
        val first=UsageTemplate(id="first",name="첫 구성",items=listOf(Item(p.id,p.name,2)))
        val chosen=first.copy(id="chosen",name="밤 구성",color=0xFF8772B5)
        val t=Treatment(date="2026-09-30",saved=true,usageConfirmed=true,
            items=listOf(Item(p.id,p.name,3)),usageTemplateId=chosen.id,usageTemplateName=chosen.name,usageTemplateColor=chosen.color)
        val s=Snapshot(templates=listOf(first,chosen),treatments=listOf(t))
        assertEquals(chosen.name,t.compositionName(s))
        assertEquals(chosen.color,t.compositionColor(s))
        assertEquals(chosen.name,t.compositionName(Snapshot()))
        assertEquals(chosen.color,t.compositionColor(Snapshot()))
        assertEquals("새 이름",t.compositionName(s.copy(templates=listOf(chosen.copy(name="새 이름")))))
        assertEquals(0xFF47956E,t.compositionColor(s.copy(templates=listOf(chosen.copy(color=0xFF47956E)))))
        assertEquals(t,codec.decodeFromString<Treatment>(codec.encodeToString(t)))
    }
    @Test fun legacyCompositionMatchesProductQuantitiesWithoutNamesOrderOrLots() {
        val t=codec.decodeFromString<Treatment>("""{"id":"legacy","items":[{"productId":"p","name":"옛 이름","quantity":2,"batchId":"lot"},{"productId":"q","name":"카세트","quantity":1}]}""")
        val template=UsageTemplate(id="night",name="밤 구성",items=listOf(Item("q","카세트",1),Item(p.id,p.name,2)))
        val s=Snapshot(templates=listOf(template))
        assertEquals(template.name,t.compositionName(s))
        assertEquals(template.color,t.compositionColor(s))
        assertEquals(template.id,t.withUsageTemplate(s).usageTemplateId)
        assertEquals("개별 사용 구성",t.copy(items=listOf(Item(p.id,p.name,1))).compositionName(s))
        assertEquals("사용 없음",t.copy(items=emptyList()).compositionName(s))
    }
    @Test fun stockUsesReceiptOrderAndIgnoresLegacyExpiry() {
        val r=Receipt(date="2026-09-01",createdAt=1,lines=listOf(
            ReceiptLine("expired","p",2,"2026-09-30"),ReceiptLine("later","p",2,"2026-10-10"),
            ReceiptLine("earlier","p",2,"2026-10-05"),ReceiptLine("undated","p",2)))
        val result=inventory(Snapshot(products=listOf(p),receipts=listOf(r),usages=listOf(usage("u","2026-10-01",3))),"2026-10-01")
        assertEquals(listOf("expired","later"),result.allocations.map{it.lotId})
        assertEquals(5,result.products.getValue("p").balance)
        assertEquals(0,result.products.getValue("p").lots.find{it.id=="expired"}!!.remaining)
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
    @Test fun adjustmentsKeepReceiptLotsAndIgnoreLegacyExpiryMetadata() {
        val receipt=Receipt(date="2026-09-01",createdAt=1,lines=listOf(
            ReceiptLine("expired","p",2,"2026-09-30"),ReceiptLine("dated","p",3,"2026-10-10"),ReceiptLine("undated","p",4)))
        val loss=StockAdjustment(id="loss",productId="p",date="2026-10-01",delta=-3,memo="포장 손상",createdAt=2)
        val add=StockAdjustment(id="add",productId="p",date="2026-10-01",delta=2,memo="누락 수량 추가",createdAt=3)
        val s=Snapshot(products=listOf(p),receipts=listOf(receipt),adjustments=listOf(loss,add))
        validate(s)
        val result=inventory(s,"2026-10-01")
        assertEquals(8,result.products.getValue("p").balance)
        val lots=result.products.getValue("p").lots.associateBy{it.id}
        assertEquals(0,lots.getValue("expired").remaining)
        assertEquals(2,lots.getValue("dated").remaining);assertEquals("2026-10-10",lots.getValue("dated").expiry)
        assertEquals(4,lots.getValue("undated").remaining);assertNull(lots.getValue("add").expiry)
        assertTrue(result.allocations.isEmpty())
        assertEquals(11,inventory(s.copy(adjustments=listOf(loss.copy(cancelled=true),add)),"2026-10-01").products.getValue("p").balance)
        assertEquals(s,codec.decodeFromString<Snapshot>(codec.encodeToString(s)))
    }
    @Test fun adjustmentsFollowEventDateAndObservedCountWithoutClampingShortages() {
        val older=StockAdjustment(id="old",productId="p",date="2026-09-30",delta=-20,memo="과거 손실",createdAt=1)
        val later=StockAdjustment(id="later",productId="p",date="2026-10-02",delta=-2,memo="포장 손상",createdAt=30)
        val future=StockAdjustment(id="future",productId="p",date="2026-10-03",delta=100,memo="추가",createdAt=40)
        val s=Snapshot(products=listOf(p),counts=listOf(StockCount(id="count",productId="p",date="2026-10-01",quantity=5,createdAt=20)),
            adjustments=listOf(future,later,older))
        assertEquals(5,inventory(s,"2026-10-01").products.getValue("p").balance)
        assertEquals(3,inventory(s,"2026-10-02").products.getValue("p").balance)
        assertEquals(-2,inventory(s.copy(counts=emptyList(),adjustments=listOf(later)),"2026-10-02").products.getValue("p").balance)
    }
    @Test fun invalidAdjustmentCannotEnterBackupOrInventory() {
        val valid=StockAdjustment(id="adjust",productId="p",date="2026-10-01",delta=1,memo="추가")
        listOf(valid.copy(delta=0),valid.copy(delta=100001),valid.copy(delta=-100001),valid.copy(memo=" "),valid.copy(productId="missing")).forEach{bad->
            try{validate(Snapshot(products=listOf(p),adjustments=listOf(bad)));fail("invalid adjustment must be rejected")}
            catch(_:IllegalArgumentException){}
        }
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
