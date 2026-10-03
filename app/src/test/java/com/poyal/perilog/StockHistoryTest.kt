package com.poyal.perilog

import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import org.junit.Assert.*
import org.junit.Test

class StockHistoryTest {
    private val p=Product(id="p",name="투석액")
    private val q=Product(id="q",name="라인")
    private fun fixture():Snapshot {
        val t=Treatment(id="use",date="2026-10-02",items=listOf(Item(p.id,p.name,2)),saved=true,usageConfirmed=true,memo="기록 메모")
        return Snapshot(products=listOf(p,q),receipts=listOf(Receipt(id="receipt",date="2026-10-01",createdAt=1,lines=listOf(
            ReceiptLine("lot1",p.id,3),ReceiptLine("lot2",p.id,2),ReceiptLine("lot3",q.id,4)))),
            treatments=listOf(t),usages=listOf(Usage(t.id,t.date,t.items,t.kind,2)),
            adjustments=listOf(StockAdjustment("loss",p.id,"2026-10-02",-1,"포장 손상",3,true),
                StockAdjustment("add",p.id,"2026-10-02",2,"누락 추가",4)),
            counts=listOf(StockCount("count",p.id,"2026-10-02",0,createdAt=5)),
            drafts=listOf(Draft("draft",t.copy(id="draft",saved=false,usageConfirmed=false))))
    }
    @Test fun historyUnifiesStoredEventsAndDistinguishesCountsFromSignedChanges() {
        val s=fixture();validate(s)
        val history=stockHistory(s)
        assertEquals(listOf("count","add","loss","use","receipt"),history.map{it.sourceId})
        assertEquals(StockHistoryType.entries.toSet(),history.map{it.type}.toSet())
        val receipt=history.last()
        assertEquals(2,receipt.lines.size);assertEquals(5,receipt.lines.first().quantity)
        assertEquals("+5 EA",receipt.quantityLabel(receipt.lines.first()))
        assertEquals("기준 0 EA",history.first().quantityLabel(history.first().lines.single()))
        val use=history.single{it.type==StockHistoryType.USAGE}
        assertEquals("−2 EA",use.quantityLabel(use.lines.single()));assertEquals("use",use.treatmentId)
        assertEquals("기록 메모",use.memo)
        assertTrue(use.description.contains("기계투석"));assertFalse(use.description.contains("기록 메모"))
    }
    @Test fun filtersUseProductIdsInclusiveDatesAndCancellationWithoutChangingSources() {
        val history=stockHistory(fixture())
        val onlyQ=history.filterStockHistory(productId=q.id)
        assertEquals(listOf("receipt"),onlyQ.map{it.sourceId});assertEquals(listOf(q.id),onlyQ.single().lines.map{it.productId})
        assertEquals(2,history.last().lines.size)
        val cancelled=history.filterStockHistory(from="2026-10-02",to="2026-10-02",cancelled=true)
        assertEquals(listOf("loss"),cancelled.map{it.sourceId})
        assertEquals(4,history.filterStockHistory(cancelled=false).size)
        assertTrue(history.filterStockHistory(type=StockHistoryType.RECEIPT,from="2026-10-02").isEmpty())
        assertTrue(history.filterStockHistory(from="2026-10-03",to="2026-10-01").isEmpty())
    }
    @Test fun deletedAndCancelledUsageRemainsVisibleWithoutPhantomRecordLink() {
        val s=fixture()
        val history=stockHistory(s.copy(treatments=emptyList(),usages=s.usages.map{it.copy(cancelled=true)}))
        val use=history.single{it.type==StockHistoryType.USAGE}
        assertNull(use.treatmentId);assertTrue(use.cancelled);assertTrue(use.description.contains("연결된 투석 기록이 없어요."))
        assertEquals("",use.memo)
        assertEquals("−2 EA",use.quantityLabel(use.lines.single()))
    }
}
