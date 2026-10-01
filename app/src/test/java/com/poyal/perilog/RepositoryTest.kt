package com.poyal.perilog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
        repo.template(UsageTemplate(id="night",name="밤",items=listOf(Item(p.id,p.name,2)),color=0xFF8772B5))
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        repo.save(Treatment(id="t",items=listOf(Item(p.id,p.name,3))),true)
        repo.product(p.copy(color=0xFF123456),listOf(0xFF123456))
        repo.template(UsageTemplate(id="night",name="밤 수정",items=listOf(Item(p.id,p.name,4)),color=0xFF5B9FA6))
        val s=repo.snapshot()
        assertEquals(7,inventory(s).products.getValue(p.id).balance)
        assertEquals(3,s.treatments.single().items.single().quantity)
        assertEquals(4,s.templates.single().items.single().quantity)
        assertEquals(0xFF5B9FA6,s.templates.single().color)
        assertTrue(0xFF5B9FA6 in s.preferences.palette)
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
    @Test fun versionOneMigrationPreservesRecordsUsagesDraftsAndTemplates()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File("schemas/com.poyal.perilog.data.JournalDb/1.json").takeIf{it.exists()}
            ?: File("app/schemas/com.poyal.perilog.data.JournalDb/1.json")
        val schema=codec.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
        val entities=schema.getValue("entities").jsonArray.associateBy{it.jsonObject.getValue("tableName").jsonPrimitive.content}
        fun columns(table:String)=entities.getValue(table).jsonObject.getValue("fields").jsonArray.map{it.jsonObject.getValue("columnName").jsonPrimitive.content}.toSet()
        context.deleteDatabase("perilog.db")
        context.getDatabasePath("perilog.db").parentFile!!.mkdirs()
        val t=Treatment(id="legacy",items=listOf(Item(p.id,p.name,2)),saved=true,usageConfirmed=true,
            weightGrams=62300,systolic=120,diastolic=80,initialDrain=2300,machineUf=600)
        val u=Usage(t.id,t.date,t.items,t.kind,t.createdAt)
        val template=UsageTemplate(id="night",name="밤 구성",items=t.items)
        val draft=Draft("draft",t.copy(id="draft",saved=false,usageConfirmed=false))
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("perilog.db"),null).use{legacy->
            entities.forEach{(table,entity)->legacy.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))}
            schema.getValue("setupQueries").jsonArray.forEach{legacy.execSQL(it.jsonPrimitive.content)}
            fun insert(table:String,row:JsonObject) {
                val values=ContentValues()
                row.filterKeys{it in columns(table)}.forEach{(name,value)->
                    when {
                        value==JsonNull->values.putNull(name)
                        value is JsonPrimitive && value.booleanOrNull!=null->values.put(name,if(value.boolean)1 else 0)
                        value is JsonPrimitive && !value.isString && value.longOrNull!=null->values.put(name,value.long)
                        value is JsonPrimitive->values.put(name,value.content)
                        else->values.put(name,value.toString())
                    }
                }
                legacy.insertOrThrow(table,null,values)
            }
            insert("products",codec.encodeToJsonElement(p).jsonObject)
            insert("templates",codec.encodeToJsonElement(template).jsonObject)
            insert("treatments",codec.encodeToJsonElement(t).jsonObject)
            insert("usages",codec.encodeToJsonElement(u).jsonObject)
            val draftRow=codec.encodeToJsonElement(draft).jsonObject.toMutableMap()
            draftRow["treatment"]=JsonObject(codec.encodeToJsonElement(draft.treatment).jsonObject.filterKeys{it in columns("treatments")})
            insert("drafts",JsonObject(draftRow))
            legacy.version=1
        }
        val upgraded=JournalDb.open(context)
        try {
            val restored=Repository(upgraded).snapshot()
            assertEquals(3,upgraded.openHelper.readableDatabase.version)
            assertEquals(listOf(p),restored.products)
            assertEquals(listOf(t),restored.treatments)
            assertEquals(listOf(u),restored.usages)
            assertEquals(listOf(template),restored.templates)
            assertEquals(listOf(draft),restored.drafts)
            assertEquals(900,restored.treatments.single().totalUf())
        } finally {upgraded.close();context.deleteDatabase("perilog.db")}
    }
}
