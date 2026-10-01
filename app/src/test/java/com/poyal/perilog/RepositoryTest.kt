package com.poyal.perilog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[31,35],application=android.app.Application::class)
class RepositoryTest {
    private lateinit var db:JournalDb
    private lateinit var repo:Repository
    private val p=Product(id="p",name="테스트 물품")
    @Before fun setup() {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),JournalDb::class.java).allowMainThreadQueries().build();repo=Repository(db)}
    @After fun close() {db.close()}
    @Test fun productColorAndTemplateChangesDoNotRewriteActualUsage()=runBlocking {
        repo.product(p)
        repo.template(UsageTemplate(id="night",name="밤",items=listOf(Item(p.id,p.name,2))))
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        repo.save(Treatment(id="t",items=listOf(Item(p.id,p.name,3))),true)
        repo.product(p.copy(color=0xFF123456),listOf(0xFF123456))
        repo.template(UsageTemplate(id="night",name="밤 수정",items=listOf(Item(p.id,p.name,4))))
        val s=repo.snapshot()
        assertEquals(7,inventory(s).products.getValue(p.id).balance)
        assertEquals(3,s.treatments.single().items.single().quantity)
        assertEquals(4,s.templates.single().items.single().quantity)
        assertEquals(0xFF123456,s.products.single().color)
        assertTrue(0xFF123456 in s.preferences.palette)
        repo.preferences(s.preferences.copy(darkMode="DARK"));repo.markCelebrated(today())
        assertEquals("DARK",repo.snapshot().preferences.darkMode)
    }
    @Test fun repeatedSaveEditDeleteUndoAndCancelDoNotDoubleDebit()=runBlocking {
        repo.product(p)
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        val t=Treatment(id="t",items=listOf(Item(p.id,p.name,2)))
        repo.save(t,true);repo.save(t.copy(initialDrain=2300,machineUf=600),true)
        assertEquals(8,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.save(t.copy(items=listOf(Item(p.id,p.name,3))),true)
        assertEquals(7,inventory(repo.snapshot()).products.getValue(p.id).balance)
        val saved=repo.snapshot().treatments.single()
        repo.deleteTreatment(t.id)
        assertEquals(7,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.undoDelete(saved);repo.cancelUsage(t.id)
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertFalse(repo.snapshot().treatments.single().usageConfirmed)
    }
    @Test fun draftDoesNotConsumeAndRestoreDoesNotReplayUses()=runBlocking {
        repo.product(p)
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        val t=Treatment(id="t",items=listOf(Item(p.id,p.name,2)))
        repo.draft(t)
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.save(t,true)
        assertTrue(repo.snapshot().drafts.isEmpty())
        val backup=repo.snapshot()
        repo.restore(backup);repo.restore(backup)
        assertEquals(8,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertEquals(backup.audit,repo.snapshot().audit)
    }
    @Test fun failedImportLeavesExistingDataUntouched()=runBlocking {
        repo.product(p)
        try {repo.restore(Snapshot(products=listOf(p,p)));fail("invalid import must fail")}catch(_:IllegalArgumentException){}
        assertEquals(listOf(p),repo.snapshot().products)
    }
    @Test fun newPreferenceCannotRecalculateHistoricalTotals()=runBlocking {
        val t=Treatment(initialDrain=2300,machineUf=600,basisMl=2000)
        repo.save(t,false)
        repo.preferences(Preferences(basis=listOf(Basis("1970-01-01",1800))))
        assertEquals(900,repo.snapshot().treatments.single().totalUf())
    }
}
