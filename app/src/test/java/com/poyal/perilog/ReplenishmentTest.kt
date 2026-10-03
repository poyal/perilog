package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import org.junit.Assert.*
import org.junit.Test

class ReplenishmentTest {
    private val p=Product(id="p",name="물품 A")
    private val q=Product(id="q",name="물품 B")
    private val date="2026-10-03"
    private fun usage(id:String,date:String,quantity:Int,cancelled:Boolean=false)=Usage(id,date,if(quantity==0)emptyList()else listOf(Item(p.id,p.name,quantity)),"MACHINE",1,cancelled)
    private fun fixture()=Snapshot(products=listOf(p,q),usages=listOf(usage("a","2026-10-01",2),usage("b","2026-10-02",4)),counts=listOf(StockCount(productId=p.id,date=date,quantity=50,createdAt=100)))
    private fun input()=ReplenishmentInput(visitDate="2026-10-10",nextVisitDate="2026-10-24",historyFrom="2026-09-05",historyTo="2026-10-02")
    private fun line(s:Snapshot=fixture(),i:ReplenishmentInput=input())=calculateReplenishment(s,i,date).lines.first { it.productId==p.id }
    @Test fun historyUsesConfirmedDaysAndActualItemsNotCurrentTemplates() {
        val s=fixture().copy(usages=fixture().usages+usage("cancel","2026-10-01",100,true)+usage("extra","2026-10-01",2),
            templates=listOf(UsageTemplate(name="바뀐 구성",items=listOf(Item(p.id,p.name,99)))))
        val evidence=usageEvidence(s,"2026-09-05","2026-10-02")
        assertEquals(2,evidence.days);assertEquals(28,evidence.totalDays);assertEquals("4",evidence.rates.getValue(p.id).label())
        val result=line(s);assertEquals("22",result.visitStock!!.label());assertEquals("56",result.demand.label());assertEquals(34,result.requested)
    }
    @Test fun confirmedEmptyDayIsZeroButMissingDatesAreExcluded() {
        val s=fixture().copy(usages=fixture().usages+usage("empty","2026-09-30",0))
        assertEquals("2",usageEvidence(s,"2026-09-05","2026-10-02").rates.getValue(p.id).label())
        assertThrows(IllegalArgumentException::class.java) {line(fixture().copy(usages=emptyList()))}
    }
    @Test fun arrivalProjectionSeparatesShortageAndNeverInflatesLaterDemand() {
        val original=line();assertEquals("29",original.visitStock!!.label());assertEquals("42",original.demand.label());assertEquals(13,original.requested)
        val s=fixture().copy(counts=listOf(StockCount(productId=p.id,date=date,quantity=10,createdAt=100)))
        val low=line(s);assertEquals("0",low.visitStock!!.label());assertEquals("11",low.beforeShortage.label());assertEquals(42,low.requested)
        val unknown=line(s.copy(counts=emptyList()));assertNull(unknown.currentStock);assertNull(unknown.visitStock);assertEquals(42,unknown.requested)
    }
    @Test fun todayAlreadyUsedIsNotSubtractedTwiceOnVisitDayOrBeforeVisit() {
        val s=fixture().copy(usages=fixture().usages+usage("today",date,2),counts=emptyList(),receipts=listOf(Receipt(date="2026-09-01",lines=listOf(ReceiptLine(productId=p.id,quantity=20)),createdAt=0)))
        val result=line(s,input().copy(visitDate=date,nextVisitDate="2026-10-05"))
        assertEquals(12,result.currentStock);assertEquals("4",result.demand.label())
        val tomorrow=line(s,input().copy(visitDate="2026-10-04",nextVisitDate="2026-10-05"))
        assertEquals("11",tomorrow.visitStock!!.label());assertEquals("3",tomorrow.demand.label())
    }
    @Test fun weeklyMixAndExtraUseWorkWithoutHistory() {
        val a=PlanComposition(name="A",items=listOf(Item(p.id,p.name,1)))
        val b=PlanComposition(name="B",items=listOf(Item(p.id,p.name,3),Item(q.id,q.name,1)))
        val pattern=UsagePattern(mode="WEEKLY",base=a,alternatives=listOf(WeeklyComposition(b,2)),extras=listOf(WeeklyComposition(a.copy(id="extra"),1)))
        val result=calculateReplenishment(Snapshot(products=listOf(p,q)),input().copy(visitDate=date,nextVisitDate="2026-10-10",pattern=pattern),date)
        assertEquals(12,result.lines.first { it.productId==p.id }.requested)
        assertEquals(2,result.lines.first { it.productId==q.id }.requested)
    }
    @Test fun partialWeeksAreSummedExactlyBeforeCeiling() {
        val direct=UsagePattern(mode="DIRECT",directItems=listOf(Item(p.id,p.name,1)))
        val s=Snapshot(products=listOf(p))
        assertEquals(1,line(s,input().copy(visitDate=date,nextVisitDate="2026-10-10",pattern=direct)).requested)
        assertEquals(1,line(s,input().copy(visitDate=date,nextVisitDate="2026-10-04",pattern=direct)).requested)
        assertEquals(2,line(s,input().copy(visitDate=date,nextVisitDate="2026-10-11",pattern=direct)).requested)
    }
    @Test fun changesApplyOnDateAndBufferUsesLastPattern() {
        val direct=UsagePattern(mode="DIRECT",directPeriodDays=1,directItems=listOf(Item(p.id,p.name,1)))
        val changed=direct.copy(directItems=listOf(Item(p.id,p.name,3)))
        val i=input().copy(visitDate=date,nextVisitDate="2026-10-10",pattern=direct,changes=listOf(PatternChange(from="2026-10-06",pattern=changed)),bufferDays=2)
        val result=line(Snapshot(products=listOf(p)),i)
        assertEquals("15",result.demand.label());assertEquals("6",result.buffer.label());assertEquals(21,result.requested)
    }
    @Test fun firstDateChangeCanReplaceMissingHistory() {
        val direct=UsagePattern(mode="DIRECT",directItems=listOf(Item(p.id,p.name,7)))
        val i=input().copy(visitDate=date,nextVisitDate="2026-10-10",changes=listOf(PatternChange(from=date,pattern=direct)))
        assertEquals(7,line(Snapshot(products=listOf(p)),i).requested)
    }
    @Test fun observedVisitStockAndManualRequestOverrideAreIndependent() {
        val r=line(i=input().copy(stockOverrides=mapOf(p.id to 5),requestOverrides=mapOf(p.id to 30)))
        assertEquals("5",r.visitStock!!.label());assertEquals(37,r.suggested);assertEquals(30,r.requested)
        assertEquals(0,line(i=input().copy(stockOverrides=mapOf(p.id to 100))).suggested)
    }
    @Test fun frozenBasisIgnoresLaterReceiptsAndCurrentDayUsage() {
        val s=fixture();val first=calculateReplenishment(s,input(),date)
        val withReceipt=s.copy(receipts=listOf(Receipt(date="2026-10-10",lines=listOf(ReceiptLine(productId=p.id,quantity=100)),createdAt=200)))
        val recalculated=calculateReplenishment(withReceipt,input(),"2026-10-15",first.basis)
        assertEquals(first,recalculated)
    }
    @Test fun savedHistoryDoesNotSilentlyChangeUntilNewCalculationIsApplied() {
        val s=fixture();val first=calculateReplenishment(s,input(),date)
        val corrected=s.copy(usages=s.usages.map { it.copy(items=listOf(Item(p.id,p.name,9))) })
        assertEquals(first,calculateReplenishment(corrected,input(),date,first.basis))
        assertNotEquals(first,calculateReplenishment(corrected,input(),date))
    }
    @Test fun progressHandlesPartialExcessAndCancelledReceipts() {
        val i=input().copy(requestOverrides=mapOf(p.id to 30));val p1=ReplenishmentPlan(id="plan",input=i,calculation=calculateReplenishment(fixture(),i,date))
        val s=fixture().copy(replenishmentPlans=listOf(p1),receipts=listOf(
            Receipt(id="one",date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=15)),requestPlanId=p1.id),
            Receipt(id="two",date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=20)),requestPlanId=p1.id),
            Receipt(id="cancel",date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=99)),requestPlanId=p1.id,cancelled=true)))
        val row=requestProgress(s,p1).first { it.productId==p.id }
        assertEquals(35L,row.received);assertEquals(0,row.remaining);assertEquals(5L,row.excess)
        assertTrue(p1.shareText().contains("물품 A: 30 EA"))
    }
    @Test fun invalidDatesPatternsAndQuantitiesAreRejected() {
        assertThrows(Exception::class.java) {line(i=input().copy(nextVisitDate="2026-10-10"))}
        assertThrows(Exception::class.java) {line(i=input().copy(historyTo=date))}
        assertThrows(Exception::class.java) {line(i=input().copy(bufferDays=-1))}
        assertThrows(Exception::class.java) {line(i=input().copy(requestOverrides=mapOf(p.id to -1)))}
        val c=PlanComposition(name="A",items=listOf(Item(p.id,p.name,1)))
        assertThrows(Exception::class.java) {line(i=input().copy(pattern=UsagePattern("WEEKLY",c,listOf(WeeklyComposition(c,5),WeeklyComposition(c.copy(id="different"),3)))))}
    }
}
