package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import org.junit.Assert.*
import org.junit.Test

class StockForecastTest {
    private val p=Product(id="p",name="물품 A")
    private val q=Product(id="q",name="물품 B")
    private val now="2026-10-04"
    private val visit="2026-10-14"
    private fun fixture(quantity:Int=30)=Snapshot(products=listOf(p,q),
        counts=listOf(StockCount(id="stock",productId=p.id,date=now,quantity=quantity,createdAt=1)),
        usages=(1..4).map {Usage(id="u$it",date="2026-09-${20+it}",items=listOf(Item(p.id,p.name,2)),kind="MACHINE",createdAt=0)})
    private fun direct(qty:Int=2)=UsagePattern(mode="DIRECT",directPeriodDays=1,directItems=listOf(Item(p.id,p.name,qty)))
    @Test fun trendUsesRecordedDaysAndMatchesRequestProjectionExactly() {
        val s=fixture()
        val forecast=forecastStock(s,visit,now)
        assertEquals(4,forecast.evidence.days);assertEquals(28,forecast.evidence.totalDays)
        val row=forecast.lines.first()
        assertEquals("2",row.dailyUse!!.label());assertEquals("20",row.expectedUse!!.label());assertEquals("10",row.balance!!.label())
        val request=calculateReplenishment(s,ReplenishmentInput(visitDate=visit,nextVisitDate="2026-10-21",
            historyFrom="2026-09-06",historyTo="2026-10-03"),now)
        assertEquals(request.lines.first().visitStock,row.balance)
        assertEquals(request.lines.first().beforeShortage,row.shortage)
    }
    @Test fun shortageZeroAndUnknownAreDifferentAndCancelledUseDoesNotChangeTrend() {
        val s=fixture(10)
        val cancelled=Usage(id="cancelled",date="2026-09-21",items=listOf(Item(p.id,p.name,100)),kind="MANUAL",createdAt=0,cancelled=true)
        val a=forecastStock(s.copy(usages=s.usages+cancelled),visit,now)
        assertEquals("10",a.lines.first().shortage!!.label())
        assertNull(a.lines.last().currentStock);assertNull(a.lines.last().balance)
        val zero=forecastStock(fixture(20),visit,now).lines.first()
        assertEquals(SupplyAmount(),zero.balance);assertEquals(SupplyAmount(),zero.shortage)
        val registeredUnused=fixture().copy(counts=fixture().counts+StockCount(productId=q.id,date=now,quantity=8))
        assertNull(forecastStock(registeredUnused,visit,now).lines.last().balance)
    }
    @Test fun todayUsageIsNotSubtractedTwiceAndVisitDayDoesNotAddUnrecordedUse() {
        val s=fixture().copy(usages=fixture().usages+Usage(id="today",date=now,items=listOf(Item(p.id,p.name,1)),kind="MACHINE",createdAt=2))
        val today=forecastStock(s,now,now).lines.first()
        assertEquals(29,today.currentStock);assertEquals(SupplyAmount(29),today.balance)
        val future=forecastStock(s,visit,now).lines.first()
        assertEquals(SupplyAmount(19),future.expectedUse);assertEquals(SupplyAmount(10),future.balance)
    }
    @Test fun missingHistoryCanUseDirectAndWeeklyPatternsWithoutInventingZero() {
        val s=fixture().copy(usages=emptyList())
        assertNull(forecastStock(s,visit,now).lines.first().balance)
        assertEquals(SupplyAmount(10),forecastStock(s,visit,now,direct()).lines.first().balance)
        val weekly=UsagePattern(mode="WEEKLY",base=PlanComposition(name="기본",items=listOf(Item(p.id,p.name,1))),
            alternatives=listOf(WeeklyComposition(PlanComposition(name="다른",items=listOf(Item(p.id,p.name,3))),2)))
        assertEquals(SupplyAmount(11,7),forecastStock(s,visit,now,weekly).lines.first().dailyUse)
        assertEquals(SupplyAmount(30),forecastStock(s,now,now).lines.first().balance)
    }
    @Test fun additionalUseIsIncludedAndLiveReceiptsAdjustmentsAndCountsRefreshForecast() {
        val s=fixture()
        val extra=Usage(id="extra",date="2026-09-21",items=listOf(Item(p.id,p.name,4)),kind="MANUAL",createdAt=0)
        assertEquals("3",forecastStock(s.copy(usages=s.usages+extra),visit,now).lines.first().dailyUse!!.label())
        val receipt=Receipt(id="received",date=now,lines=listOf(ReceiptLine(productId=p.id,quantity=5)),createdAt=2)
        assertEquals(SupplyAmount(15),forecastStock(s.copy(receipts=listOf(receipt)),visit,now).lines.first().balance)
        assertEquals(SupplyAmount(10),forecastStock(s.copy(receipts=listOf(receipt.copy(cancelled=true))),visit,now).lines.first().balance)
        assertEquals(SupplyAmount(10),forecastStock(s.copy(receipts=listOf(receipt.copy(date="2026-10-10"))),visit,now).lines.first().balance)
        val adjusted=s.copy(adjustments=listOf(StockAdjustment(productId=p.id,date=now,delta=-3,memo="손실",createdAt=3)))
        assertEquals(SupplyAmount(7),forecastStock(adjusted,visit,now).lines.first().balance)
        assertEquals(SupplyAmount(20),forecastStock(adjusted.copy(counts=s.counts+StockCount(productId=p.id,date=now,quantity=40,createdAt=4)),visit,now).lines.first().balance)
    }
    @Test fun savedRequestsNeverCountAsStockAndPastForecastIsRejected() {
        val s=fixture()
        val input=ReplenishmentInput(visitDate=now,nextVisitDate=visit,historyFrom="2026-09-06",historyTo="2026-10-03",requestOverrides=mapOf(p.id to 100))
        val plan=ReplenishmentPlan(input=input,calculation=calculateReplenishment(s,input,now))
        assertEquals(forecastStock(s,visit,now),forecastStock(s.copy(replenishmentPlans=listOf(plan)),visit,now))
        assertThrows(IllegalArgumentException::class.java) {forecastStock(s,"2026-10-03",now)}
    }
}
