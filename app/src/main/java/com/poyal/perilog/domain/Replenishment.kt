package com.poyal.perilog.domain

import com.poyal.perilog.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val zeroSupply = SupplyAmount()

data class UsageEvidence(val days: Int, val totalDays: Int, val rates: Map<String, SupplyAmount>)

fun usageEvidence(s: Snapshot, from: String, to: String): UsageEvidence {
    val totalDays = (ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1).toInt()
    require(totalDays in 1..3650) { "참고 기간은 1~3,650일로 선택해 주세요." }
    val usages = s.usages.filter { !it.cancelled && it.date in from..to }
    val days = usages.map { it.date }.distinct().size
    val totals = usages.flatMap { it.items }.groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity.toLong() } }
    return UsageEvidence(days, totalDays, if (days == 0) emptyMap() else totals.mapValues { SupplyAmount(it.value, days.toLong()) })
}

fun validatePattern(pattern: UsagePattern, productIds: Set<String>) {
    require(pattern.mode in listOf("HISTORY", "WEEKLY", "DIRECT"))
    fun composition(c: PlanComposition) {
        require(c.id.isNotBlank() && c.name.isNotBlank())
        require(c.items.map { it.productId }.distinct().size == c.items.size)
        c.items.forEach { require(it.productId in productIds && it.quantity in 1..100000 && it.batchId == null) { "구성의 품목과 수량을 확인해 주세요." } }
    }
    pattern.base?.let(::composition)
    val compositionIds=(listOfNotNull(pattern.base)+pattern.alternatives.map { it.composition }+pattern.extras.map { it.composition }).map { it.id }
    require(compositionIds.distinct().size==compositionIds.size) { "구성 줄의 ID가 겹치지 않아야 합니다." }
    require(pattern.alternatives.sumOf { it.count.toLong() } <= 7) { "다른 구성의 사용 일수는 합계 7일 이내로 입력해 주세요." }
    pattern.alternatives.forEach { require(it.count in 1..7); composition(it.composition) }
    pattern.extras.forEach { require(it.count in 1..1000); composition(it.composition) }
    require(pattern.directPeriodDays in listOf(1, 7))
    require(pattern.directItems.map { it.productId }.distinct().size == pattern.directItems.size)
    pattern.directItems.forEach { require(it.productId in productIds && it.quantity in 1..100000 && it.batchId == null) }
    if (pattern.mode == "WEEKLY") require(pattern.base != null) { "기본으로 사용하는 구성을 선택해 주세요." }
    if (pattern.mode == "DIRECT") require(pattern.directItems.isNotEmpty()) { "하루 또는 일주일 사용량을 입력해 주세요." }
}

private fun patternRates(pattern: UsagePattern, evidence: UsageEvidence): Map<String, SupplyAmount> {
    val rates = mutableMapOf<String, SupplyAmount>()
    fun add(items: List<Item>, times: Int, days: Int) = items.forEach {
        rates[it.productId] = (rates[it.productId] ?: zeroSupply) + SupplyAmount(it.quantity.toLong()*times, days.toLong())
    }
    when (pattern.mode) {
        "HISTORY" -> {
            require(evidence.days > 0) { "참고할 사용 기록이 없어요. 평소 사용 바꾸기에서 구성이나 사용량을 입력해 주세요." }
            rates.putAll(evidence.rates)
        }
        "WEEKLY" -> {
            add(pattern.base!!.items, 7-pattern.alternatives.sumOf { it.count }, 7)
            pattern.alternatives.forEach { add(it.composition.items, it.count, 7) }
        }
        "DIRECT" -> add(pattern.directItems, 1, pattern.directPeriodDays)
    }
    pattern.extras.forEach { add(it.composition.items, it.count, 7) }
    return rates
}

fun calculateReplenishment(s: Snapshot, input: ReplenishmentInput, asOf: String,
    frozenBasis: ReplenishmentBasis? = null): ReplenishmentCalculation {
    val basisDate = LocalDate.parse(frozenBasis?.asOf ?: asOf)
    val visit = LocalDate.parse(input.visitDate)
    val next = LocalDate.parse(input.nextVisitDate)
    val days = ChronoUnit.DAYS.between(visit, next).toInt()
    require(!visit.isBefore(basisDate)) { "방문일은 계산 기준일(${basisDate}) 이후로 선택해 주세요." }
    require(days in 1..3650 && ChronoUnit.DAYS.between(basisDate, next) <= 3650) { "방문 사이 기간은 1~3,650일로 선택해 주세요." }
    require(input.historyTo < basisDate.toString()) { "참고 기간은 계산 기준일 전날까지 선택해 주세요." }
    require(input.bufferDays in 0..365) { "여유분은 0~365일로 입력해 주세요." }
    val ids = s.products.map { it.id }.toSet()
    validatePattern(input.pattern, ids)
    require(input.changes.map { it.from }.distinct().size == input.changes.size) { "사용 변경일이 겹치지 않게 선택해 주세요." }
    require(input.changes.map { it.id }.distinct().size == input.changes.size)
    input.changes.forEach {
        require(it.id.isNotBlank())
        require(LocalDate.parse(it.from) in basisDate..next.minusDays(1)) { "사용 변경일은 계산 기준일부터 다음 방문 전날 사이로 선택해 주세요." }
        validatePattern(it.pattern, ids)
    }
    (input.stockOverrides.entries + input.requestOverrides.entries).forEach { (id, q) -> require(id in ids && q in 0..1000000) { "직접 입력한 수량은 0~1,000,000EA로 입력해 주세요." } }
    val evidence = if(frozenBasis?.historyDays!=null && frozenBasis.historyFrom==input.historyFrom && frozenBasis.historyTo==input.historyTo)
        UsageEvidence(frozenBasis.historyDays,(ChronoUnit.DAYS.between(LocalDate.parse(input.historyFrom),LocalDate.parse(input.historyTo))+1).toInt(),frozenBasis.historyRates)
        else usageEvidence(s, input.historyFrom, input.historyTo)
    val stock = inventory(s, basisDate.toString())
    val basis = (frozenBasis ?: ReplenishmentBasis(basisDate.toString(), stock.products.mapValues { if (it.value.registered) it.value.balance else null },
        s.usages.filter { !it.cancelled && it.date == basisDate.toString() }.flatMap { it.items }.groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity } }))
        .copy(historyFrom=input.historyFrom,historyTo=input.historyTo,historyDays=evidence.days,historyRates=evidence.rates)
    // Evaluate only patterns that actually apply; an unused historical starting pattern needs no records.
    val changes = input.changes.sortedBy { it.from }
    val rateCache = mutableMapOf<UsagePattern, Map<String, SupplyAmount>>()
    fun rates(date: LocalDate): Map<String, SupplyAmount> {
        val p = changes.lastOrNull { it.from <= date.toString() }?.pattern ?: input.pattern
        return rateCache.getOrPut(p) { patternRates(p, evidence) }
    }
    fun sum(from: LocalDate, until: LocalDate): Map<String, SupplyAmount> {
        val result = mutableMapOf<String, SupplyAmount>()
        var date = from
        while (date < until) {
            rates(date).forEach { (id, amount) ->
                val remaining = if (date == basisDate) (amount - SupplyAmount((basis.usedToday[id] ?: 0).toLong())).nonNegative() else amount
                result[id] = (result[id] ?: zeroSupply) + remaining
            }
            date = date.plusDays(1)
        }
        return result
    }
    val before = sum(basisDate, visit)
    val demand = sum(visit, next)
    val lastRates = rates(next.minusDays(1))
    val relevant = s.products.filter { it.active || it.id in demand || it.id in input.requestOverrides || it.id in input.stockOverrides }
    val lines = relevant.map { p ->
        val current = basis.stocks[p.id]
        val rawVisit = current?.let { SupplyAmount(it.toLong()) - (before[p.id] ?: zeroSupply) }
        val projected = input.stockOverrides[p.id]?.let { SupplyAmount(it.toLong()) } ?: rawVisit?.nonNegative()
        val needed = demand[p.id] ?: zeroSupply
        val buffer = (lastRates[p.id] ?: zeroSupply)*input.bufferDays
        val suggested = (needed + buffer - (projected ?: zeroSupply)).nonNegative().ceil()
        require(suggested <= 1000000) { "${p.name} 요청량이 너무 큽니다. 기간과 사용량을 확인해 주세요." }
        ReplenishmentLine(p.id, p.name, current, projected,
            rawVisit?.let { (zeroSupply-it).nonNegative() } ?: zeroSupply, needed, buffer, suggested, input.requestOverrides[p.id] ?: suggested)
    }
    return ReplenishmentCalculation(basis, evidence.days, evidence.totalDays, days, lines)
}

data class RequestProgress(val productId: String, val name: String, val requested: Int, val received: Long) {
    val remaining: Int get() = (requested.toLong()-received).coerceAtLeast(0).toInt()
    val excess: Long get() = (received-requested).coerceAtLeast(0)
}
fun requestProgress(s: Snapshot, plan: ReplenishmentPlan): List<RequestProgress> {
    val received = s.receipts.filter { !it.cancelled && it.requestPlanId == plan.id }.flatMap { it.lines }
        .groupBy { it.productId }.mapValues { (_, lines) -> lines.sumOf { it.quantity.toLong() } }
    val requests = plan.calculation.lines.associateBy { it.productId }
    return (requests.keys + received.keys).map { id ->
        RequestProgress(id, s.products.find { it.id == id }?.name ?: requests.getValue(id).name, requests[id]?.requested ?: 0, received[id] ?: 0)
    }
}

fun ReplenishmentPlan.shareText(): String = buildString {
    appendLine("페리로그 입고 요청")
    appendLine("${input.visitDate} ~ ${input.nextVisitDate} 방문 사이 · ${calculation.days}일분")
    calculation.lines.filter { it.requested > 0 }.forEach { appendLine("${it.name}: ${it.requested} EA") }
    if (memo.isNotBlank()) appendLine("메모: $memo")
}.trim()

fun validateReplenishment(s: Snapshot) {
    val products = s.products.map { it.id }.toSet()
    val ids = s.replenishmentPlans.map { it.id }
    require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size)
    s.replenishmentPlans.forEach { p ->
        val c = p.calculation
        val basisDate = LocalDate.parse(c.basis.asOf)
        require(LocalDate.parse(p.input.visitDate) >= basisDate)
        require(c.days in 1..3650 && c.days.toLong() == ChronoUnit.DAYS.between(LocalDate.parse(p.input.visitDate), LocalDate.parse(p.input.nextVisitDate)))
        require(c.historyDays in 0..c.historyTotalDays && c.historyTotalDays in 1..3650)
        require(p.input.historyTo < c.basis.asOf)
        require(ChronoUnit.DAYS.between(LocalDate.parse(p.input.historyFrom), LocalDate.parse(p.input.historyTo))+1 == c.historyTotalDays.toLong())
        require(p.input.bufferDays in 0..365)
        validatePattern(p.input.pattern, products)
        require(p.input.changes.map { it.id }.distinct().size == p.input.changes.size && p.input.changes.map { it.from }.distinct().size == p.input.changes.size)
        p.input.changes.forEach { require(it.id.isNotBlank() && LocalDate.parse(it.from) in basisDate..LocalDate.parse(p.input.nextVisitDate).minusDays(1)); validatePattern(it.pattern, products) }
        (p.input.stockOverrides.entries + p.input.requestOverrides.entries).forEach { (id, q) -> require(id in products && q in 0..1000000) }
        require(c.lines.map { it.productId }.distinct().size == c.lines.size)
        c.lines.forEach {
            require(it.productId in products && it.name.isNotBlank() && it.requested in 0..1000000 && it.suggested in 0..1000000)
            listOfNotNull(it.visitStock, it.beforeShortage, it.demand, it.buffer).forEach { amount -> require(amount.numerator >= 0 && amount.denominator > 0) }
            require(it.requested == (p.input.requestOverrides[it.productId] ?: it.suggested))
        }
        c.basis.stocks.forEach { (id, _) -> require(id in products) }
        c.basis.usedToday.forEach { (id, q) -> require(id in products && q >= 0) }
        c.basis.historyDays?.let { days ->
            require(days==c.historyDays && c.basis.historyFrom==p.input.historyFrom && c.basis.historyTo==p.input.historyTo)
            c.basis.historyRates.forEach { (id, amount)->require(id in products && amount.numerator>=0 && amount.denominator>0) }
        }
    }
    s.receipts.forEach { r -> require(r.requestPlanId == null || r.requestPlanId in ids) { "연결된 입고 요청을 찾을 수 없습니다." } }
}
