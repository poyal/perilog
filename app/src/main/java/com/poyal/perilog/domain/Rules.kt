package com.poyal.perilog.domain

import com.poyal.perilog.data.*
import java.time.LocalDate

fun Treatment.totalUf(): Int? = if (kind == "MACHINE") {
    if (initialDrain != null && machineUf != null && basisMl != null) initialDrain - basisMl + machineUf else null
} else if (drainUnit == "mL" && manualDrain != null && previousFill != null) manualDrain - previousFill else null

fun Treatment.missing(): List<String> = buildList {
    if (!saved) add("기록 저장")
    if (kind == "MACHINE") {
        if (weightGrams == null) add("몸무게")
        if (systolic == null || diastolic == null) add("혈압")
        if (initialDrain == null) add("초기배액량")
        if (machineUf == null) add("제수량")
    }
    if (!usageConfirmed) add("사용 구성 확인")
}
fun Treatment.complete(): Boolean = missing().isEmpty()
private fun Treatment.matchingTemplate(s:Snapshot):UsageTemplate? = if(items.isEmpty())null else
    s.templates.firstOrNull{template->template.items.associate{it.productId to it.quantity}==items.associate{it.productId to it.quantity}}
fun Treatment.withUsageTemplate(s:Snapshot):Treatment {
    val template=(s.templates.find{it.id==usageTemplateId}
        ?: if(usageTemplateId==null && usageTemplateName==null)matchingTemplate(s)else null) ?: return this
    return copy(usageTemplateId=usageTemplateId ?: template.id,usageTemplateName=usageTemplateName ?: template.name,
        usageTemplateColor=usageTemplateColor ?: template.color)
}
fun Treatment.compositionName(s:Snapshot):String =
    s.templates.find{it.id==usageTemplateId}?.name ?: usageTemplateName ?: matchingTemplate(s)?.name
        ?: if(items.isEmpty())"사용 없음"else"개별 사용 구성"
fun Treatment.compositionColor(s:Snapshot):Long? = s.templates.find{it.id==usageTemplateId}?.color ?: usageTemplateColor
    ?: if(usageTemplateId==null && usageTemplateName==null)matchingTemplate(s)?.color else null
fun Snapshot.visibleRecords()=treatments + drafts.filter { draft -> treatments.none{it.id==draft.id} }
    .map { it.treatment.copy(saved=false,usageConfirmed=false) }

fun List<Treatment>.dayComplete(date: String): Boolean {
    val entries = filter { it.date == date }
    return entries.any { it.kind == "MACHINE" && it.complete() } && entries.all { it.complete() }
}
data class YesterdaySummary(val date:String,val pending:List<Treatment>,val needsMachine:Boolean) {
    val visible:Boolean get()=needsMachine || pending.isNotEmpty()
}
fun Snapshot.yesterdaySummary(on:String):YesterdaySummary {
    val date=LocalDate.parse(on).minusDays(1).toString()
    val records=visibleRecords().filter{it.date==date}
    return YesterdaySummary(date,records.filterNot{it.complete()}.sortedBy{it.createdAt},records.none{it.kind=="MACHINE"})
}

fun Preferences.basisOn(date: String) = basis.filter { it.from <= date }.maxByOrNull { it.from }?.ml ?: 2000

fun newTreatment(s: Snapshot, kind: String, date: String): Treatment {
    return Treatment(date = date, kind = kind,
        basisMl = if(kind == "MACHINE") s.preferences.basisOn(date) else null,
        drainUnit = if(kind == "MANUAL") s.preferences.lastDrainUnit else "mL")
}

data class Lot(val id: String, val productId: String, val expiry: String?, val date: String, var remaining: Int)
data class Allocation(val usageId: String, val productId: String, val lotId: String?, val quantity: Int)
data class Stock(val registered: Boolean, val balance: Int, val lots: List<Lot>, val unallocated: Int)
data class StockResult(val products: Map<String, Stock>, val allocations: List<Allocation>)

/** Rebuild by event date. A stock count starts a new observed interval; older events cannot debit it. */
fun inventory(s: Snapshot, through: String = today()): StockResult {
    data class Event(val date: String, val at: Long, val id: String, val action: () -> Unit)
    val lots = mutableListOf<Lot>()
    val known = mutableSetOf<String>()
    val debt = mutableMapOf<String, Int>()
    val allocations = mutableListOf<Allocation>()
    val events = mutableListOf<Event>()
    fun consume(productId:String,quantity:Int,batchId:String?=null,usageId:String?=null) {
        var need=quantity
        // Lots already follow event date and entry order. Legacy expiry metadata is ignored.
        val candidates=lots.filter{it.productId==productId && it.remaining>0 && (batchId==null || it.id==batchId)}
        candidates.forEach{lot->
            val take=minOf(need,lot.remaining)
            if(take>0){lot.remaining-=take;need-=take;usageId?.let{allocations+=Allocation(it,productId,lot.id,take)}}
        }
        if(need>0){debt[productId]=(debt[productId] ?: 0)+need;usageId?.let{allocations+=Allocation(it,productId,null,need)}}
    }
    s.receipts.filter { !it.cancelled && it.date <= through }.forEach { r -> events += Event(r.date,r.createdAt,r.id) {
        r.lines.forEach { line ->
            known += line.productId
            lots += Lot(line.id,line.productId,line.expiry,r.date,line.quantity)
        }
    } }
    s.counts.filter { it.date <= through }.forEach { c -> events += Event(c.date,c.createdAt,c.id) {
        known += c.productId
        lots.removeAll { it.productId == c.productId }
        debt[c.productId] = 0
        lots += Lot(c.id,c.productId,null,c.date,c.quantity)
    } }
    s.usages.filter { !it.cancelled && it.date <= through }.forEach { u -> events += Event(u.date,u.createdAt,u.id) {
        u.items.forEach { item ->
            consume(item.productId,item.quantity,item.batchId,u.id)
        }
    } }
    s.adjustments.filter{!it.cancelled && it.date<=through}.forEach{a->events+=Event(a.date,a.createdAt,a.id) {
        known+=a.productId
        if(a.delta>0)lots+=Lot(a.id,a.productId,null,a.date,a.delta)
        else consume(a.productId,-a.delta)
    }}
    events.sortedWith(compareBy<Event>{it.date}.thenBy{it.at}.thenBy{it.id}).forEach { it.action() }
    return StockResult(s.products.associate { p ->
        val pLots = lots.filter { it.productId == p.id }
        p.id to Stock(p.id in known,pLots.sumOf { it.remaining } - (debt[p.id] ?: 0),pLots,debt[p.id] ?: 0)
    }, allocations)
}

fun validate(s: Snapshot) {
    require(s.formatVersion == 1) { "지원하지 않는 백업 버전입니다." }
    validateAppointments(s)
    validateReplenishment(s)
    fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "중복되거나 빈 ID가 있습니다." } }
    unique(s.products.map{it.id}); unique(s.templates.map{it.id}); unique(s.treatments.map{it.id})
    unique(s.usages.map{it.id}); unique(s.receipts.map{it.id}); unique(s.counts.map{it.id}); unique(s.audit.map{it.id})
    unique(s.adjustments.map{it.id})
    unique(s.drafts.map{it.id})
    unique(s.receipts.flatMap{it.lines}.map{it.id} + s.counts.map{it.id} + s.adjustments.map{it.id})
    val products = s.products.map { it.id }.toSet()
    val batches = s.receipts.flatMap{it.lines}.associate{it.id to it.productId} + s.counts.associate{it.id to it.productId} +
        s.adjustments.filter{it.delta>0}.associate{it.id to it.productId}
    fun date(d: String) { LocalDate.parse(d) }
    fun items(lines: List<Item>) {
        unique(lines.map{it.productId})
        lines.forEach { require(it.productId in products && it.quantity in 1..100000) { "품목 또는 수량이 올바르지 않습니다." }
            require(it.batchId == null || batches[it.batchId] == it.productId) { "재고 연결이 올바르지 않습니다." } }
    }
    s.products.forEach { require(it.name.isNotBlank() && (it.lowStock == null || it.lowStock >= 0)) }
    s.templates.forEach { require(it.name.isNotBlank()); items(it.items) }
    s.drafts.forEach { require(it.id==it.treatment.id); date(it.treatment.date); items(it.treatment.items) }
    s.treatments.forEach { t ->
        date(t.date); require(t.kind in listOf("MACHINE","MANUAL")); require(t.drainUnit in listOf("mL","g","kg"))
        listOf(t.weightGrams,t.initialDrain,t.basisMl,t.manualDrain,t.previousFill,t.dwellMinutes,t.systolic,t.diastolic).forEach { require(it == null || it in 0..10000000) { "입력값을 확인해 주세요." } }
        require(t.machineUf == null || t.machineUf in -10000000..10000000); items(t.items)
        // Legacy time text is preserved, including unfinished input in old drafts.
        // These fields are no longer editable and must not block saving a record.
        if(t.usageConfirmed) require(s.usages.any { u -> u.id == t.id && !u.cancelled && u.items == t.items && u.date == t.date }) { "사용 내역이 일치하지 않습니다." }
    }
    s.usages.forEach { date(it.date); items(it.items) }
    s.receipts.forEach { r -> date(r.date); r.lines.forEach { require(it.productId in products && it.quantity in 1..100000); it.expiry?.let(::date) } }
    s.counts.forEach { date(it.date); require(it.productId in products && it.quantity in 0..1000000) }
    s.adjustments.forEach{date(it.date);require(it.productId in products && it.delta in -100000..100000 && it.delta!=0 && it.memo.isNotBlank()){"조정 수량과 사유를 확인해 주세요."}}
    val p=s.preferences
    validatePattern(p.stockForecastPattern, products)
    require(p.expiryDays in 0..3650 && p.backupDays in listOf(1,7) && p.keepBackups in 1..365)
    require(p.reminderHour in 0..23 && p.reminderMinute in 0..59 && p.darkMode in listOf("SYSTEM","LIGHT","DARK"))
    require(p.basis.isNotEmpty()); p.basis.forEach{date(it.from); require(it.ml in 0..100000)}
}
