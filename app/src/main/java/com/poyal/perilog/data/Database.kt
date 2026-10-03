package com.poyal.perilog.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import com.poyal.perilog.domain.*

class Converters {
    @TypeConverter fun items(value: List<Item>): String = codec.encodeToString(value)
    @TypeConverter fun items(value: String): List<Item> = codec.decodeFromString(value)
    @TypeConverter fun lines(value: List<ReceiptLine>): String = codec.encodeToString(value)
    @TypeConverter fun lines(value: String): List<ReceiptLine> = codec.decodeFromString(value)
    @TypeConverter fun treatment(value: Treatment): String = codec.encodeToString(value)
    @TypeConverter fun treatment(value: String): Treatment = codec.decodeFromString(value)
    @TypeConverter fun departments(value: List<Department>): String = codec.encodeToString(value)
    @TypeConverter fun departments(value: String): List<Department> = codec.decodeFromString(value)
    @TypeConverter fun departmentTimes(value: Map<String,String>): String = codec.encodeToString(value)
    @TypeConverter fun departmentTimes(value: String): Map<String,String> = codec.decodeFromString(value)
    @TypeConverter fun careTasks(value: List<CareTask>): String = codec.encodeToString(value)
    @TypeConverter fun careTasks(value: String): List<CareTask> = codec.decodeFromString(value)
    @TypeConverter fun care(value: CareTemplate?): String? = value?.let { codec.encodeToString(it) }
    @TypeConverter fun care(value: String?): CareTemplate? = value?.let { codec.decodeFromString<CareTemplate>(it) }
}

@Dao interface JournalDao {
    @Query("SELECT * FROM products") suspend fun products(): List<Product>
    @Query("SELECT * FROM templates") suspend fun templates(): List<UsageTemplate>
    @Query("SELECT * FROM treatments") suspend fun treatments(): List<Treatment>
    @Query("SELECT * FROM usages") suspend fun usages(): List<Usage>
    @Query("SELECT * FROM receipts") suspend fun receipts(): List<Receipt>
    @Query("SELECT * FROM counts") suspend fun counts(): List<StockCount>
    @Query("SELECT * FROM adjustments") suspend fun adjustments(): List<StockAdjustment>
    @Query("SELECT * FROM audit") suspend fun audit(): List<Audit>
    @Query("SELECT * FROM drafts") suspend fun drafts(): List<Draft>
    @Query("SELECT * FROM settings WHERE id=1") suspend fun settings(): SettingsRow?
    @Query("SELECT * FROM departments") suspend fun departments(): List<Department>
    @Query("SELECT * FROM care_templates") suspend fun careTemplates(): List<CareTemplate>
    @Query("SELECT * FROM appointments") suspend fun appointments(): List<Appointment>
    @Query("SELECT * FROM contacts") suspend fun contacts(): List<Contact>
    @Upsert suspend fun put(value: Product)
    @Upsert suspend fun put(value: UsageTemplate)
    @Upsert suspend fun put(value: Treatment)
    @Upsert suspend fun put(value: Usage)
    @Upsert suspend fun put(value: Receipt)
    @Upsert suspend fun put(value: StockCount)
    @Upsert suspend fun put(value: StockAdjustment)
    @Upsert suspend fun put(value: Draft)
    @Upsert suspend fun put(value: SettingsRow)
    @Insert suspend fun put(value: Audit)
    @Upsert suspend fun put(value: Department)
    @Upsert suspend fun put(value: CareTemplate)
    @Upsert suspend fun put(value: Appointment)
    @Upsert suspend fun put(value: Contact)
    @Query("DELETE FROM departments WHERE id=:id") suspend fun deleteDepartment(id: String)
    @Query("DELETE FROM care_templates WHERE id=:id") suspend fun deleteCareTemplate(id: String)
    @Query("DELETE FROM appointments WHERE id=:id") suspend fun deleteAppointment(id: String)
    @Query("DELETE FROM contacts WHERE id=:id") suspend fun deleteContact(id: String)
    @Query("DELETE FROM departments") suspend fun clearDepartments()
    @Query("DELETE FROM care_templates") suspend fun clearCareTemplates()
    @Query("DELETE FROM appointments") suspend fun clearAppointments()
    @Query("DELETE FROM contacts") suspend fun clearContacts()
    @Query("DELETE FROM treatments WHERE id=:id") suspend fun deleteTreatment(id: String)
    @Query("DELETE FROM drafts WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM templates WHERE id=:id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM products") suspend fun clearProducts()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM treatments") suspend fun clearTreatments()
    @Query("DELETE FROM usages") suspend fun clearUsages()
    @Query("DELETE FROM receipts") suspend fun clearReceipts()
    @Query("DELETE FROM counts") suspend fun clearCounts()
    @Query("DELETE FROM adjustments") suspend fun clearAdjustments()
    @Query("DELETE FROM audit") suspend fun clearAudit()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
}

@Database(entities=[Product::class,UsageTemplate::class,Treatment::class,Usage::class,Receipt::class,
    StockCount::class,Audit::class,Draft::class,SettingsRow::class,Department::class,CareTemplate::class,
    Appointment::class,Contact::class,StockAdjustment::class],version=7,exportSchema=true)
@TypeConverters(Converters::class)
abstract class JournalDb : RoomDatabase() {
    abstract fun dao(): JournalDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE treatments ADD COLUMN usageTemplateId TEXT")
                db.execSQL("ALTER TABLE treatments ADD COLUMN usageTemplateName TEXT")
            }
        }
        val MIGRATION_2_3=object:Migration(2,3) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN color INTEGER NOT NULL DEFAULT 4280379320")
                db.execSQL("ALTER TABLE treatments ADD COLUMN usageTemplateColor INTEGER")
            }
        }
        val MIGRATION_3_4=object:Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS departments (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS care_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, tasks TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS appointments (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, time TEXT NOT NULL, departments TEXT NOT NULL, care TEXT, memo TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contacts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_4_5=object:Migration(4,5) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN emoji TEXT NOT NULL DEFAULT '👤'")
                db.execSQL("ALTER TABLE contacts ADD COLUMN allowCall INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE contacts ADD COLUMN allowSms INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE care_templates ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'medical'")
                db.execSQL("ALTER TABLE appointments ADD COLUMN careItems TEXT NOT NULL DEFAULT '[]'")
                val legacy=mutableListOf<CareTemplate>()
                db.query("SELECT id,name,tasks FROM care_templates").use { cursor ->
                    while(cursor.moveToNext())legacy+=CareTemplate(id=cursor.getString(0),name=cursor.getString(1),
                        tasks=codec.decodeFromString<List<CareTask>>(cursor.getString(2)))
                }
                legacy.filter{it.tasks.isNotEmpty()}.forEach { bundle ->
                    bundle.individualItems().forEachIndexed { index,item ->
                        if(index==0)db.execSQL("UPDATE care_templates SET name=?,tasks='[]',iconKey=? WHERE id=?",arrayOf(item.name,item.iconKey,item.id))
                        else db.execSQL("INSERT INTO care_templates (id,name,tasks,iconKey) VALUES (?,?,'[]',?)",arrayOf(item.id,item.name,item.iconKey))
                    }
                }
            }
        }
        val MIGRATION_5_6=object:Migration(5,6) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE appointments ADD COLUMN departmentTimes TEXT NOT NULL DEFAULT '{}'")
            }
        }
        val MIGRATION_6_7=object:Migration(6,7) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS adjustments (id TEXT NOT NULL PRIMARY KEY, productId TEXT NOT NULL, date TEXT NOT NULL, delta INTEGER NOT NULL, memo TEXT NOT NULL, createdAt INTEGER NOT NULL, cancelled INTEGER NOT NULL)")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context,JournalDb::class.java,"perilog.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7).build()
    }
}

data class DeletedTreatment(val record:Treatment,val usage:Usage?)

class Repository(val db: JournalDb) {
    private val d = db.dao()
    val snapshots: Flow<Snapshot> = db.invalidationTracker.createFlow("products","templates","treatments","usages","receipts","counts","adjustments","audit","settings","drafts","departments","care_templates","appointments","contacts").map { snapshot() }
    suspend fun snapshot(): Snapshot = db.withTransaction { read() }
    private suspend fun read() = Snapshot(products=d.products(),templates=d.templates(),treatments=d.treatments(),
        usages=d.usages(),receipts=d.receipts(),counts=d.counts(),audit=d.audit(),drafts=d.drafts(),
        preferences=d.settings()?.let { codec.decodeFromString<Preferences>(it.payload) } ?: Preferences(),
        departments=d.departments(),careTemplates=d.careTemplates(),appointments=d.appointments(),contacts=d.contacts(),adjustments=d.adjustments())
    private suspend fun log(action:String,id:String,before:String,after:String) = d.put(Audit(action=action,targetId=id,before=before,after=after))
    suspend fun draft(t: Treatment) = d.put(Draft(t.id,t))
    suspend fun discardDraft(id: String) = d.deleteDraft(id)
    suspend fun save(t: Treatment, confirmUsage: Boolean) = db.withTransaction {
        val s=read()
        val previous=s.treatments.find{it.id==t.id}
        val existing=s.usages.find{it.id==t.id}
        val value=t.copy(saved=true,usageConfirmed=confirmUsage,updatedAt=System.currentTimeMillis())
        val usage=Usage(t.id,t.date,t.items,t.kind,existing?.createdAt ?: System.currentTimeMillis(),cancelled=!confirmUsage)
        val next=s.copy(treatments=s.treatments.filterNot{it.id==t.id}+value,
            usages=s.usages.filterNot{it.id==t.id}+usage)
        validate(next)
        d.put(value); d.put(usage); d.deleteDraft(t.id)
        log("치료 저장",t.id,previous?.let{codec.encodeToString(it)} ?: "",codec.encodeToString(value))
        if(t.kind=="MANUAL") d.put(SettingsRow(payload=codec.encodeToString(s.preferences.copy(lastDrainUnit=t.drainUnit))))
    }
    suspend fun deleteTreatment(id: String) = db.withTransaction {
        val before=d.treatments().find{it.id==id} ?: return@withTransaction null
        val usage=d.usages().find{it.id==id}
        if(usage!=null && !usage.cancelled)d.put(usage.copy(cancelled=true))
        d.deleteTreatment(id); d.deleteDraft(id)
        log("기록 삭제 · 사용 취소",id,codec.encodeToString(before),"")
        DeletedTreatment(before,usage)
    }
    suspend fun undoDelete(deleted: DeletedTreatment) = db.withTransaction {
        val t=deleted.record
        if(d.treatments().any{it.id==t.id})return@withTransaction
        val u=deleted.usage
        u?.let{d.put(it)}
        val restored=t.copy(usageConfirmed=u!=null && !u.cancelled,items=u?.items ?: t.items)
        d.put(restored); log("기록 삭제 취소",t.id,"",codec.encodeToString(restored))
    }
    suspend fun cancelUsage(id: String) = db.withTransaction {
        val u=d.usages().find{it.id==id} ?: return@withTransaction
        d.put(u.copy(cancelled=true))
        d.treatments().find{it.id==id}?.let { d.put(it.copy(usageConfirmed=false)) }
        log("사용 취소",id,codec.encodeToString(u),"")
    }
    suspend fun product(p: Product, extraColors: List<Long> = emptyList()) = db.withTransaction {
        val s=read(); validate(s.copy(products=s.products.filterNot{it.id==p.id}+p)); d.put(p)
        if(extraColors.isNotEmpty())d.put(SettingsRow(payload=codec.encodeToString(s.preferences.copy(palette=(s.preferences.palette+extraColors).distinct()))))
    }
    suspend fun markCelebrated(date: String) = db.withTransaction {
        val p=read().preferences
        if(date !in p.celebratedDates)d.put(SettingsRow(payload=codec.encodeToString(p.copy(celebratedDates=p.celebratedDates+date))))
    }
    suspend fun template(t: UsageTemplate) = db.withTransaction {
        val s=read();validate(s.copy(templates=s.templates.filterNot{it.id==t.id}+t));d.put(t)
        d.put(SettingsRow(payload=codec.encodeToString(s.preferences.copy(palette=(s.preferences.palette+t.color).distinct()))))
    }
    suspend fun deleteTemplate(id: String) = d.deleteTemplate(id)
    suspend fun receipt(r: Receipt) = db.withTransaction {
        val s=read(); validate(s.copy(receipts=s.receipts.filterNot{it.id==r.id}+r))
        log("입고 저장",r.id,s.receipts.find{it.id==r.id}?.let{codec.encodeToString(it)} ?: "",codec.encodeToString(r)); d.put(r)
    }
    suspend fun count(c: StockCount) = db.withTransaction {
        val s=read(); validate(s.copy(counts=s.counts+c)); d.put(c); log("재고 실사",c.id,"",codec.encodeToString(c))
    }
    suspend fun adjustment(value: StockAdjustment) = db.withTransaction {
        val s=read();val previous=s.adjustments.find{it.id==value.id}
        validate(s.copy(adjustments=s.adjustments.filterNot{it.id==value.id}+value))
        d.put(value)
        log(if(value.cancelled)"재고 조정 취소"else"재고 조정",value.id,
            previous?.let{codec.encodeToString(it)} ?: "",codec.encodeToString(value))
    }
    suspend fun preferences(p: Preferences) = db.withTransaction { validate(read().copy(preferences=p)); d.put(SettingsRow(payload=codec.encodeToString(p))) }
    suspend fun department(value: Department) = db.withTransaction {
        val s=read(); validate(s.copy(departments=s.departments.filterNot{it.id==value.id}+value)); d.put(value)
    }
    suspend fun careTemplate(value: CareTemplate) = db.withTransaction {
        val s=read(); val items=value.individualItems()
        validate(s.copy(careTemplates=s.careTemplates.filterNot{old->items.any{it.id==old.id}}+items)); items.forEach{d.put(it)}
    }
    suspend fun appointment(value: Appointment) = db.withTransaction {
        val s=read(); validate(s.copy(appointments=s.appointments.filterNot{it.id==value.id}+value)); d.put(value)
    }
    suspend fun createContact(value: Contact) = saveContact(value,creating=true)
    suspend fun updateContact(value: Contact) = saveContact(value,creating=false)
    private suspend fun saveContact(value: Contact,creating:Boolean) = db.withTransaction {
        val exists=d.contacts().any{it.id==value.id}
        require(if(creating)!exists else exists) {
            if(creating)"이미 저장한 연락처예요. 목록에서 새 연락처 등록을 열어 주세요."
            else "연락처가 삭제되었어요. 목록에서 다시 선택해 주세요."
        }
        val s=read(); validate(s.copy(contacts=s.contacts.filterNot{it.id==value.id}+value)); d.put(value)
    }
    suspend fun deleteDepartment(id: String) = d.deleteDepartment(id)
    suspend fun deleteCareTemplate(id: String) = d.deleteCareTemplate(id)
    suspend fun deleteAppointment(id: String) = d.deleteAppointment(id)
    suspend fun deleteContact(id: String) = d.deleteContact(id)
    suspend fun restore(s: Snapshot) = db.withTransaction {
        validate(s)
        val careItems=s.careTemplates.flatMap{it.individualItems()}
        validate(s.copy(careTemplates=careItems))
        d.clearDrafts(); d.clearTreatments(); d.clearUsages(); d.clearReceipts(); d.clearCounts(); d.clearAdjustments(); d.clearTemplates(); d.clearProducts(); d.clearAudit()
        d.clearAppointments(); d.clearDepartments(); d.clearCareTemplates(); d.clearContacts()
        s.products.forEach{d.put(it)}; s.templates.forEach{d.put(it)}; s.treatments.forEach{d.put(it)}; s.usages.forEach{d.put(it)}
        s.receipts.forEach{d.put(it)}; s.counts.forEach{d.put(it)}; s.adjustments.forEach{d.put(it)}; s.audit.forEach{d.put(it)}; s.drafts.forEach{d.put(it)}
        s.departments.forEach{d.put(it)}; careItems.forEach{d.put(it)}; s.appointments.forEach{d.put(it)}; s.contacts.forEach{d.put(it)}
        d.put(SettingsRow(payload=codec.encodeToString(s.preferences)))
    }
}
