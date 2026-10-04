package com.poyal.perilog.domain

import com.poyal.perilog.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Current stock already includes today's confirmed usage. */
internal fun supplyBasis(s: Snapshot, asOf: String): ReplenishmentBasis = ReplenishmentBasis(
    asOf,
    inventory(s, asOf).products.mapValues { if (it.value.registered) it.value.balance else null },
    s.usages.filter { !it.cancelled && it.date == asOf }.flatMap { it.items }
        .groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity } }
)

/** Shared by live forecasts and frozen requests; only final requests round to EA. */
internal class UsageProjection(
    private val basis: ReplenishmentBasis,
    private val pattern: UsagePattern,
    private val evidence: UsageEvidence,
    changes: List<PatternChange> = emptyList()
) {
    private val changes = changes.sortedBy { it.from }
    private val cache = mutableMapOf<UsagePattern, Map<String, SupplyAmount>>()
    fun rates(date: LocalDate): Map<String, SupplyAmount> {
        val active = changes.lastOrNull { it.from <= date.toString() }?.pattern ?: pattern
        return cache.getOrPut(active) { patternRates(active, evidence) }
    }

    fun sum(from: LocalDate, until: LocalDate): Map<String, SupplyAmount> {
        val result = mutableMapOf<String, SupplyAmount>()
        var date = from
        while (date < until) {
            rates(date).forEach { (id, amount) ->
                val remaining = if (date.toString() == basis.asOf)
                    (amount - SupplyAmount((basis.usedToday[id] ?: 0).toLong())).nonNegative() else amount
                result[id] = (result[id] ?: SupplyAmount()) + remaining
            }
            date = date.plusDays(1)
        }
        return result
    }
}

data class StockForecastLine(
    val productId: String, val name: String, val currentStock: Int?,
    val dailyUse: SupplyAmount?, val expectedUse: SupplyAmount?, val balance: SupplyAmount?
) {
    val shortage: SupplyAmount? get() = balance?.let { (SupplyAmount() - it).nonNegative() }
}

data class StockForecast(
    val asOf: String, val targetDate: String, val days: Int,
    val historyFrom: String, val historyTo: String, val evidence: UsageEvidence,
    val pattern: UsagePattern, val lines: List<StockForecastLine>
)

fun forecastStock(s: Snapshot, targetDate: String, asOf: String,
    pattern: UsagePattern = s.preferences.stockForecastPattern): StockForecast {
    val start = LocalDate.parse(asOf)
    val end = LocalDate.parse(targetDate)
    val span = ChronoUnit.DAYS.between(start, end)
    require(span in 0..3650) { "예상 재고는 오늘부터 3,650일 이내의 방문일에 계산할 수 있어요." }
    validatePattern(pattern, s.products.map { it.id }.toSet())
    val from = start.minusDays(28).toString()
    val to = start.minusDays(1).toString()
    val evidence = usageEvidence(s, from, to)
    val basis = supplyBasis(s, asOf)
    val projection = UsageProjection(basis, pattern, evidence)
    val hasEvidence = pattern.mode != "HISTORY" || evidence.days > 0
    val rates = if (hasEvidence) projection.rates(start) else emptyMap()
    val use = if (hasEvidence) projection.sum(start, end) else emptyMap()
    val lines = s.products.filter { it.active || it.id in rates }.map { p ->
        // Absence from history is not evidence that a newly registered product is unused.
        val knownRate = hasEvidence && (pattern.mode != "HISTORY" || p.id in rates)
        val rate = if (knownRate) rates[p.id] ?: SupplyAmount() else null
        val expected = if (span == 0L) SupplyAmount() else if (knownRate) use[p.id] ?: SupplyAmount() else null
        val current = basis.stocks[p.id]
        StockForecastLine(p.id, p.name, current, rate, expected,
            if (current != null && expected != null) SupplyAmount(current.toLong()) - expected else null)
    }
    return StockForecast(asOf, targetDate, span.toInt(), from, to, evidence, pattern, lines)
}
