package com.poyal.perilog.data

import android.content.Context
import androidx.room.*
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
}

@Dao interface JournalDao {
    @Query("SELECT * FROM products") suspend fun products(): List<Product>
    @Query("SELECT * FROM templates") suspend fun templates(): List<UsageTemplate>
    @Query("SELECT * FROM treatments") suspend fun treatments(): List<Treatment>
    @Query("SELECT * FROM usages") suspend fun usages(): List<Usage>
    @Query("SELECT * FROM receipts") suspend fun receipts(): List<Receipt>
    @Query("SELECT * FROM counts") suspend fun counts(): List<StockCount>
    @Query("SELECT * FROM audit") suspend fun audit(): List<Audit>
    @Query("SELECT * FROM drafts") suspend fun drafts(): List<Draft>
    @Query("SELECT * FROM settings WHERE id=1") suspend fun settings(): SettingsRow?
    @Upsert suspend fun put(value: Product)
    @Upsert suspend fun put(value: UsageTemplate)
    @Upsert suspend fun put(value: Treatment)
    @Upsert suspend fun put(value: Usage)
    @Upsert suspend fun put(value: Receipt)
    @Upsert suspend fun put(value: StockCount)
    @Upsert suspend fun put(value: Draft)
    @Upsert suspend fun put(value: SettingsRow)
    @Insert suspend fun put(value: Audit)
    @Query("DELETE FROM treatments WHERE id=:id") suspend fun deleteTreatment(id: String)
    @Query("DELETE FROM drafts WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM templates WHERE id=:id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM products") suspend fun clearProducts()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM treatments") suspend fun clearTreatments()
    @Query("DELETE FROM usages") suspend fun clearUsages()
    @Query("DELETE FROM receipts") suspend fun clearReceipts()
    @Query("DELETE FROM counts") suspend fun clearCounts()
    @Query("DELETE FROM audit") suspend fun clearAudit()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
}

@Database(entities=[Product::class,UsageTemplate::class,Treatment::class,Usage::class,Receipt::class,
    StockCount::class,Audit::class,Draft::class,SettingsRow::class],version=1,exportSchema=true)
@TypeConverters(Converters::class)
abstract class JournalDb : RoomDatabase() {
    abstract fun dao(): JournalDao
    companion object { fun open(context: Context) = Room.databaseBuilder(context,JournalDb::class.java,"perilog.db").build() }
}

class Repository(val db: JournalDb) {
    private val d = db.dao()
    val snapshots: Flow<Snapshot> = db.invalidationTracker.createFlow("products","templates","treatments","usages","receipts","counts","audit","settings","drafts").map { snapshot() }
    suspend fun snapshot(): Snapshot = db.withTransaction { read() }
    private suspend fun read() = Snapshot(products=d.products(),templates=d.templates(),treatments=d.treatments(),
        usages=d.usages(),receipts=d.receipts(),counts=d.counts(),audit=d.audit(),drafts=d.drafts(),
        preferences=d.settings()?.let { codec.decodeFromString<Preferences>(it.payload) } ?: Preferences())
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
        val before=d.treatments().find{it.id==id} ?: return@withTransaction
        d.deleteTreatment(id); d.deleteDraft(id)
        log("기록 삭제 · 사용 유지",id,codec.encodeToString(before),"")
    }
    suspend fun undoDelete(t: Treatment) = db.withTransaction {
        val u=d.usages().find{it.id==t.id}
        val restored=t.copy(usageConfirmed=u!=null && !u.cancelled,items=u?.items ?: t.items)
        d.put(restored); log("기록 삭제 취소",t.id,"",codec.encodeToString(restored))
    }
    suspend fun cancelUsage(id: String) = db.withTransaction {
        val u=d.usages().find{it.id==id} ?: return@withTransaction
        d.put(u.copy(cancelled=true))
        d.treatments().find{it.id==id}?.let { d.put(it.copy(usageConfirmed=false)) }
        log("사용 취소",id,codec.encodeToString(u),"")
    }
    suspend fun product(p: Product) = db.withTransaction { val s=read(); validate(s.copy(products=s.products.filterNot{it.id==p.id}+p)); d.put(p) }
    suspend fun template(t: UsageTemplate) = db.withTransaction { val s=read(); validate(s.copy(templates=s.templates.filterNot{it.id==t.id}+t)); d.put(t) }
    suspend fun deleteTemplate(id: String) = d.deleteTemplate(id)
    suspend fun receipt(r: Receipt) = db.withTransaction {
        val s=read(); validate(s.copy(receipts=s.receipts.filterNot{it.id==r.id}+r))
        log("입고 저장",r.id,s.receipts.find{it.id==r.id}?.let{codec.encodeToString(it)} ?: "",codec.encodeToString(r)); d.put(r)
    }
    suspend fun count(c: StockCount) = db.withTransaction {
        val s=read(); validate(s.copy(counts=s.counts+c)); d.put(c); log("재고 실사",c.id,"",codec.encodeToString(c))
    }
    suspend fun preferences(p: Preferences) = db.withTransaction { validate(read().copy(preferences=p)); d.put(SettingsRow(payload=codec.encodeToString(p))) }
    suspend fun restore(s: Snapshot) = db.withTransaction {
        validate(s)
        d.clearDrafts(); d.clearTreatments(); d.clearUsages(); d.clearReceipts(); d.clearCounts(); d.clearTemplates(); d.clearProducts(); d.clearAudit()
        s.products.forEach{d.put(it)}; s.templates.forEach{d.put(it)}; s.treatments.forEach{d.put(it)}; s.usages.forEach{d.put(it)}
        s.receipts.forEach{d.put(it)}; s.counts.forEach{d.put(it)}; s.audit.forEach{d.put(it)}; s.drafts.forEach{d.put(it)}
        d.put(SettingsRow(payload=codec.encodeToString(s.preferences)))
    }
}
