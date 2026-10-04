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
    @Test fun forecastAndExtraRequestSettingsRoundTripWithoutChangingLegacyRequests()=runBlocking {
        repo.product(p)
        val pattern=UsagePattern(mode="DIRECT",directPeriodDays=1,directItems=listOf(Item(p.id,p.name,2)))
        val stale=repo.snapshot().preferences
        repo.stockForecastPattern(pattern)
        repo.preferences(stale.copy(darkMode="DARK"))
        assertEquals(pattern,repo.snapshot().preferences.stockForecastPattern)
        val old=ReplenishmentInput(nextVisitDate=java.time.LocalDate.now().plusDays(7).toString(),pattern=pattern,bufferDays=3)
        val legacy=ReplenishmentPlan(id="old",input=old,calculation=calculateReplenishment(repo.snapshot(),old,today()))
        repo.replenishmentPlan(legacy)
        val added=old.copy(calculationVersion=2,extraQuantities=mapOf(p.id to 5))
        repo.replenishmentPlan(ReplenishmentPlan(id="new",input=added,calculation=calculateReplenishment(repo.snapshot(),added,today())))
        val backup=codec.decodeFromString<Snapshot>(codec.encodeToString(Snapshot.serializer(),repo.snapshot()))
        repo.restore(Snapshot());repo.restore(backup)
        assertEquals(legacy,repo.snapshot().replenishmentPlans.first {it.id=="old"})
        assertEquals(25,repo.snapshot().replenishmentPlans.first {it.id=="new"}.calculation.lines.single().requested)
        assertEquals(pattern,repo.snapshot().preferences.stockForecastPattern)
        assertEquals(1,codec.decodeFromString<ReplenishmentInput>("{}").calculationVersion)
        assertEquals("HISTORY",codec.decodeFromString<Preferences>("{}").stockForecastPattern.mode)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.stockForecastPattern(pattern.copy(directItems=listOf(Item("missing","알 수 없음",1))))}}
        Unit
    }
    @Test fun contactOrderSurvivesBackupSettingsEditsAndNewContactsWithoutChangingContactData()=runBlocking {
        val a=Contact(id="a",name="병원",phone="02-123-4567",createdAt=1)
        val b=Contact(id="b",name="간호사",phone="010-1234-5678",createdAt=2,allowCall=false)
        repo.createContact(a);repo.createContact(b)
        val oldSettings=repo.snapshot().preferences
        repo.reorderContacts(listOf("b","a"));repo.preferences(oldSettings.copy(darkMode="DARK"))
        assertEquals(listOf(b,a),repo.snapshot().orderedContacts())
        assertTrue(runCatching {repo.reorderContacts(listOf("b","b"))}.isFailure)
        assertEquals(listOf(b,a),repo.snapshot().orderedContacts())
        val c=Contact(id="c",name="고객센터",phone="1588-1234",createdAt=0)
        repo.createContact(c)
        assertEquals(listOf(b,a,c),repo.snapshot().orderedContacts())
        repo.restore(codec.decodeFromString<Snapshot>(codec.encodeToString(Snapshot.serializer(),repo.snapshot())))
        assertEquals(listOf(b,a,c),repo.snapshot().orderedContacts())
        repo.deleteContact("b")
        assertEquals(listOf(a,c),repo.snapshot().orderedContacts());assertEquals(listOf("a"),repo.snapshot().preferences.contactOrder)
        assertTrue(codec.decodeFromString<Preferences>("{}").contactOrder.isEmpty())
    }
    @Test fun contactCreationCannotOverwriteAndEditingCannotRecreateDeletedContact()=runBlocking {
        val first=Contact(id="first",name="투석실",phone="02-123-4567",emoji="🏥",allowSms=false)
        val second=Contact(id="second",name="간호사",phone="010-1234-5678")
        repo.createContact(first);repo.createContact(second)
        assertTrue(runCatching{repo.createContact(first.copy(name="덮어쓰기"))}.exceptionOrNull() is IllegalArgumentException)
        assertEquals(first,repo.snapshot().contacts.single{it.id==first.id})
        val edited=second.copy(name="수정한 간호사",allowCall=false)
        repo.updateContact(edited)
        assertEquals(setOf(first,edited),repo.snapshot().contacts.toSet())
        repo.deleteContact(second.id)
        assertTrue(runCatching{repo.updateContact(edited)}.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(first),repo.snapshot().contacts)
        assertTrue(runCatching{repo.createContact(Contact(name="빈 번호"))}.isFailure)
        assertEquals(listOf(first),repo.snapshot().contacts)
    }
    @Test fun editingAndBackupRoundTripPreserveLegacyTreatmentTimes()=runBlocking {
        val original=Treatment(id="legacy-times",startTime="22:00",endTime="07:00",dwellMinutes=95,memo="이전 메모")
        repo.save(original,false)
        val stored=repo.snapshot().treatments.single()
        repo.save(stored.copy(memo="수정한 메모"),false)
        val backup=codec.decodeFromString<Snapshot>(codec.encodeToString(Snapshot.serializer(),repo.snapshot()))
        repo.restore(backup)
        val restored=repo.snapshot().treatments.single()
        assertEquals("22:00",restored.startTime);assertEquals("07:00",restored.endTime)
        assertEquals(95,restored.dwellMinutes);assertEquals("수정한 메모",restored.memo)
    }
    @Test fun unfinishedLegacyTimeInputInADraftDoesNotBlockSavingOrBackupRestore()=runBlocking {
        val oldDraft=Treatment(id="partial-time",startTime="22:",endTime="7",memo="작성 중 기록")
        repo.draft(oldDraft)
        repo.save(repo.snapshot().drafts.single().treatment.copy(memo="완료한 기록"),false)
        val backup=repo.snapshot()
        repo.restore(backup)
        val saved=repo.snapshot().treatments.single()
        assertEquals("22:",saved.startTime);assertEquals("7",saved.endTime)
        assertEquals("완료한 기록",saved.memo);assertTrue(repo.snapshot().drafts.isEmpty())
    }
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
        val deleted=repo.deleteTreatment(t.id)!!
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertTrue(repo.snapshot().usages.single().cancelled)
        assertNull(repo.deleteTreatment(t.id))
        repo.undoDelete(deleted)
        assertEquals(7,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertFalse(repo.snapshot().usages.single().cancelled)
        repo.undoDelete(deleted)
        assertEquals(7,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.cancelUsage(t.id)
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertFalse(repo.snapshot().treatments.single().usageConfirmed)
    }
    @Test fun deletingAndUndoingAlreadyCancelledOrMissingUsageCannotAddOrReactivateStock()=runBlocking {
        repo.product(p)
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        val t=Treatment(id="cancelled",items=listOf(Item(p.id,p.name,2)))
        repo.save(t,true);repo.cancelUsage(t.id)
        val cancelled=repo.snapshot().usages.single()
        val deleted=repo.deleteTreatment(t.id)!!
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.undoDelete(deleted)
        assertEquals(cancelled,repo.snapshot().usages.single())
        assertFalse(repo.snapshot().treatments.single().usageConfirmed)
        val withoutUsage=t.copy(id="no-usage",saved=true,usageConfirmed=false)
        repo.restore(repo.snapshot().copy(treatments=listOf(withoutUsage)))
        val deletedMissing=repo.deleteTreatment(withoutUsage.id)!!
        assertNull(deletedMissing.usage)
        repo.undoDelete(deletedMissing)
        assertEquals(withoutUsage,repo.snapshot().treatments.single())
        assertEquals(10,inventory(repo.snapshot()).products.getValue(p.id).balance)
    }
    @Test fun deletingHistoricalUsageDoesNotAddAgainToLaterObservedStockCount()=runBlocking {
        val past=java.time.LocalDate.now().minusDays(1).toString()
        repo.product(p)
        repo.receipt(Receipt(date=past,createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10))))
        val t=Treatment(id="historic",date=past,items=listOf(Item(p.id,p.name,2)))
        repo.save(t,true)
        repo.count(StockCount(productId=p.id,date=today(),quantity=5))
        val deleted=repo.deleteTreatment(t.id)!!
        assertEquals(5,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.undoDelete(deleted)
        assertEquals(5,inventory(repo.snapshot()).products.getValue(p.id).balance)
    }
    @Test fun adjustmentResaveCancelAndBackupRestorePreserveStockAndReason()=runBlocking {
        repo.product(p)
        repo.receipt(Receipt(date=today(),createdAt=1,lines=listOf(ReceiptLine(productId=p.id,quantity=10,expiry=java.time.LocalDate.now().plusDays(2).toString()))))
        val loss=StockAdjustment(id="loss",productId=p.id,delta=-2,memo="포장 손상")
        repo.adjustment(loss);repo.adjustment(loss)
        assertEquals(8,inventory(repo.snapshot()).products.getValue(p.id).balance)
        repo.adjustment(StockAdjustment(id="add",productId=p.id,delta=1,memo="누락 수량 추가"))
        val saved=repo.snapshot()
        val backup=codec.decodeFromString<Snapshot>(codec.encodeToString(saved))
        repo.restore(Snapshot());repo.restore(backup)
        assertEquals(saved.adjustments,repo.snapshot().adjustments)
        assertEquals(9,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertEquals(saved.receipts,repo.snapshot().receipts)
        assertTrue(repo.snapshot().counts.isEmpty() && repo.snapshot().usages.isEmpty())
        repo.adjustment(loss.copy(cancelled=true));repo.adjustment(loss.copy(cancelled=true))
        assertEquals(11,inventory(repo.snapshot()).products.getValue(p.id).balance)
        assertTrue(repo.snapshot().adjustments.single{it.id==loss.id}.cancelled)
        assertEquals("포장 손상",repo.snapshot().adjustments.single{it.id==loss.id}.memo)
        assertEquals(4,repo.snapshot().audit.count{it.targetId==loss.id})
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
    @Test fun appointmentCatalogEditsAndSettingsNeverRewriteSavedBookingAndBackupRestoresAllLists()=runBlocking {
        repo.product(p)
        val d=Department(id="kidney",name="신장내과")
        val c=CareTemplate(id="routine",name="정기 방문",tasks=listOf(CareTask(name="피검사",iconKey="blood")))
        val eye=Department(id="eye",name="안과")
        val a=Appointment(id="reservation",date="2026-10-10",time="09:30",departments=listOf(d,eye),care=c,memo="검사 후 방문",
            departmentTimes=mapOf(d.id to "09:30",eye.id to "11:00"))
        val contact=Contact(name="테스트 연락처",phone="010-0000-0000",emoji="🏥",allowSms=false)
        repo.department(d);repo.careTemplate(c);repo.appointment(a);repo.createContact(contact)
        val preferences=repo.snapshot().preferences
        repo.department(d.copy(name="수정 진료과",color=0xFF47956E))
        repo.careTemplate(c.copy(tasks=listOf(CareTask(name="주사",iconKey="injection"))))
        repo.preferences(preferences.copy(darkMode="DARK"))
        assertEquals(a,repo.snapshot().appointments.single())
        repo.deleteDepartment(d.id);repo.deleteCareTemplate(c.id)
        assertEquals(a,repo.snapshot().appointments.single())
        val backup=codec.decodeFromString<Snapshot>(codec.encodeToJsonElement(repo.snapshot()).toString())
        repo.restore(Snapshot());assertTrue(repo.snapshot().contacts.isEmpty())
        repo.restore(backup)
        val restored=repo.snapshot()
        assertEquals(backup.copy(exportedAt=restored.exportedAt),restored)
        repo.deleteAppointment(a.id);repo.deleteContact(contact.id)
        assertTrue(repo.snapshot().appointments.isEmpty() && repo.snapshot().contacts.isEmpty())
        assertEquals(listOf(p),repo.snapshot().products)
    }
    @Test fun invalidAppointmentRestoreLeavesExistingDataUntouched()=runBlocking {
        repo.product(p)
        assertThrows(IllegalArgumentException::class.java){runBlocking{repo.restore(Snapshot(appointments=listOf(Appointment(date="2026-10-02",time="25:00"))))}}
        assertEquals(listOf(p),repo.snapshot().products)
    }
    @Test fun versionOneMigrationPreservesRecordsUsagesDraftsAndTemplates()=migrationPreservesData(1)
    @Test fun versionThreeMigrationPreservesRecordsUsagesDraftsAndTemplates()=migrationPreservesData(3)
    @Test fun versionFourMigrationPreservesContactsAppointmentsAndConvertsCareItems()=migrationPreservesData(4)
    @Test fun versionFiveMigrationKeepsLegacySharedTimeAndAllExistingData()=migrationPreservesData(5)
    @Test fun versionSixMigrationPreservesDepartmentTimesAndCreatesEmptyAdjustments()=migrationPreservesData(6)
    @Test fun versionSevenMigrationPreservesExistingDataAndAddsRequests()=migrationPreservesData(7)
    private fun migrationPreservesData(version:Int)=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File("schemas/com.poyal.perilog.data.JournalDb/$version.json").takeIf{it.exists()}
            ?: File("app/schemas/com.poyal.perilog.data.JournalDb/$version.json")
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
        val receipt=Receipt(id="legacy-receipt",lines=listOf(ReceiptLine(id="legacy-lot",productId=p.id,quantity=10)))
        val count=StockCount(id="legacy-count",productId=p.id,date=today(),quantity=8)
        val audit=Audit(id="legacy-audit",action="확인",targetId=t.id,before="",after="확인됨")
        val preferences=Preferences(darkMode="DARK",lastDrainUnit="kg")
        val adjustment=StockAdjustment(id="legacy-adjustment",productId=p.id,delta=2,memo="이전 조정")
        val department=Department(id="legacy-dept",name="신장내과",color=0xFF47956E)
        val care=CareTemplate(id="legacy-care",name="정기 방문",tasks=listOf(CareTask(id="blood",name="피검사",iconKey="blood"),CareTask(id="room",name="투석실 방문",iconKey="dialysis")))
        val appointment=Appointment(id="legacy-reservation",date="2026-10-10",time="09:30",departments=listOf(department),care=care,memo="예약 메모",
            departmentTimes=if(version>=6)mapOf(department.id to "11:20")else emptyMap())
        val contact=Contact(id="legacy-contact",name="테스트 연락처",phone="010-0000-0000")
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
            insert("receipts",codec.encodeToJsonElement(receipt).jsonObject)
            insert("counts",codec.encodeToJsonElement(count).jsonObject)
            insert("audit",codec.encodeToJsonElement(audit).jsonObject)
            insert("settings",buildJsonObject{put("id",1);put("payload",codec.encodeToJsonElement(preferences).toString())})
            if(version>=7)insert("adjustments",codec.encodeToJsonElement(adjustment).jsonObject)
            if(version>=4) {
                insert("departments",codec.encodeToJsonElement(department).jsonObject)
                (if(version>=5)care.individualItems() else listOf(care)).forEach { insert("care_templates",codec.encodeToJsonElement(it).jsonObject) }
                insert("appointments",codec.encodeToJsonElement(appointment).jsonObject)
                insert("contacts",codec.encodeToJsonElement(contact).jsonObject)
            }
            legacy.version=version
        }
        val upgraded=JournalDb.open(context)
        try {
            val restored=Repository(upgraded).snapshot()
            assertEquals(8,upgraded.openHelper.readableDatabase.version)
            assertEquals(if(version>=7)listOf(adjustment)else emptyList<StockAdjustment>(),restored.adjustments)
            assertTrue(restored.replenishmentPlans.isEmpty())
            assertEquals(listOf(p),restored.products)
            assertEquals(listOf(t),restored.treatments)
            assertEquals(listOf(u),restored.usages)
            assertEquals(listOf(template),restored.templates)
            assertEquals(listOf(draft),restored.drafts)
            assertEquals(listOf(receipt),restored.receipts);assertEquals(listOf(count),restored.counts)
            assertEquals(listOf(audit),restored.audit);assertEquals(preferences,restored.preferences)
            if(version>=4) {
                assertEquals(listOf(department),restored.departments)
                assertEquals(care.individualItems(),restored.careTemplates)
                assertEquals(listOf(appointment),restored.appointments);assertEquals(listOf(contact),restored.contacts)
                assertEquals("👤",restored.contacts.single().emoji);assertTrue(restored.contacts.single().allowCall && restored.contacts.single().allowSms)
                assertEquals(restored.careTemplates.map{it.asCareTask()},restored.appointments.single().selectedCareItems())
                assertEquals(appointment.departmentTimes,restored.appointments.single().departmentTimes)
                assertEquals(if(version>=6)"11:20"else"09:30",restored.appointments.single().departmentTime(department))
            } else assertTrue(restored.departments.isEmpty() && restored.careTemplates.isEmpty() && restored.appointments.isEmpty() && restored.contacts.isEmpty())
            assertEquals(900,restored.treatments.single().totalUf())
        } finally {upgraded.close();context.deleteDatabase("perilog.db")}
    }
    @Test fun restoringOldBundleBackupKeepsAppointmentsAndAllIndividualItems()=runBlocking {
        val bundle=CareTemplate(name="정기 방문",tasks=listOf(CareTask(name="피검사",iconKey="blood"),CareTask(name="주사",iconKey="injection")))
        val a=Appointment(date="2026-10-10",time="09:00",care=bundle)
        repo.restore(Snapshot(careTemplates=listOf(bundle),appointments=listOf(a)))
        val s=repo.snapshot()
        assertEquals(bundle.individualItems(),s.careTemplates);assertEquals(listOf(a),s.appointments)
        repo.restore(codec.decodeFromString<Snapshot>(codec.encodeToJsonElement(s).toString()))
        assertEquals(s.careTemplates,repo.snapshot().careTemplates)
        assertEquals(s.appointments,repo.snapshot().appointments)
    }
}
