package com.poyal.perilog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=android.app.Application::class)
class ReplenishmentRepositoryTest {
    private lateinit var db:JournalDb
    private lateinit var repo:Repository
    private val p=Product(id="p",name="물품")
    private val date="2026-10-03"
    @Before fun setup() {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),JournalDb::class.java).allowMainThreadQueries().build();repo=Repository(db)}
    @After fun close() {db.close()}
    private suspend fun prepare():ReplenishmentPlan {
        repo.product(p)
        val pattern=UsagePattern(mode="DIRECT",directItems=listOf(Item(p.id,p.name,7)))
        val input=ReplenishmentInput(visitDate=date,nextVisitDate="2026-11-02",historyFrom="2026-09-05",historyTo="2026-10-02",pattern=pattern)
        val plan=ReplenishmentPlan(id="plan",input=input,calculation=calculateReplenishment(repo.snapshot(),input,date))
        repo.replenishmentPlan(plan)
        return plan
    }
    @Test fun requestNeverChangesStockAndLinkedReceiptsAreIdempotent()=runBlocking {
        val plan=prepare()
        assertFalse(inventory(repo.snapshot(),date).products.getValue(p.id).registered)
        val one=Receipt(id="one",date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=15)),requestPlanId=plan.id)
        repo.receipt(one);repo.receipt(one)
        assertEquals(15,inventory(repo.snapshot(),date).products.getValue(p.id).balance)
        assertEquals(15,requestProgress(repo.snapshot(),plan).single().remaining)
        repo.receipt(one.copy(id="two",lines=listOf(ReceiptLine(productId=p.id,quantity=15))))
        assertEquals(0,requestProgress(repo.snapshot(),plan).single().remaining)
        repo.receipt(one.copy(cancelled=true))
        assertEquals(15,inventory(repo.snapshot(),date).products.getValue(p.id).balance)
        assertEquals(15,requestProgress(repo.snapshot(),plan).single().remaining)
    }
    @Test fun editingRequestAfterReceivingDoesNotAlterActualReceipts()=runBlocking {
        val plan=prepare()
        val receipt=Receipt(date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=15)),requestPlanId=plan.id)
        repo.receipt(receipt)
        val input=plan.input.copy(requestOverrides=mapOf(p.id to 10))
        val changed=plan.copy(input=input,calculation=calculateReplenishment(repo.snapshot(),input,date,plan.calculation.basis))
        repo.replenishmentPlan(changed)
        assertEquals(listOf(receipt),repo.snapshot().receipts)
        assertEquals(5L,requestProgress(repo.snapshot(),changed).single().excess)
        assertEquals(15,inventory(repo.snapshot(),date).products.getValue(p.id).balance)
    }
    @Test fun backupRoundTripPreservesRequestSnapshotsAndLinks()=runBlocking {
        val plan=prepare()
        repo.receipt(Receipt(date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=15)),requestPlanId=plan.id))
        val source=repo.snapshot()
        val restored=codec.decodeFromString<Snapshot>(codec.encodeToString(source))
        repo.restore(Snapshot());repo.restore(restored)
        assertEquals(source.replenishmentPlans,repo.snapshot().replenishmentPlans)
        assertEquals(source.receipts,repo.snapshot().receipts)
        assertEquals(15,requestProgress(repo.snapshot(),plan).single().remaining)
        val old=codec.decodeFromString<Snapshot>("""{"products":[{"id":"p","name":"물품"}]}""")
        repo.restore(old);assertTrue(repo.snapshot().replenishmentPlans.isEmpty())
    }
    @Test fun brokenReceiptLinkOrMalformedPlanCannotReplaceData()=runBlocking {
        prepare();val before=repo.snapshot()
        assertTrue(runCatching {repo.receipt(Receipt(date=date,lines=listOf(ReceiptLine(productId=p.id,quantity=1)),requestPlanId="missing"))}.isFailure)
        assertEquals(before.receipts,repo.snapshot().receipts)
        val malformed=before.copy(replenishmentPlans=before.replenishmentPlans.map { it.copy(calculation=it.calculation.copy(days=0)) })
        assertTrue(runCatching {repo.restore(malformed)}.isFailure)
        assertEquals(before.replenishmentPlans,repo.snapshot().replenishmentPlans)
    }
}
