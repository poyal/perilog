package com.poyal.perilog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Exact quantities: rounding is deferred until the final EA request. */
@Serializable
data class SupplyAmount(val numerator: Long = 0, val denominator: Long = 1) : Comparable<SupplyAmount> {
    init { require(denominator > 0) }
    private fun reduced(n: BigInteger, d: BigInteger): SupplyAmount {
        val gcd = n.gcd(d)
        return SupplyAmount((n / gcd).longValueExact(), (d / gcd).longValueExact())
    }
    operator fun plus(other: SupplyAmount) = reduced(numerator.toBigInteger()*other.denominator.toBigInteger()+other.numerator.toBigInteger()*denominator.toBigInteger(), denominator.toBigInteger()*other.denominator.toBigInteger())
    operator fun minus(other: SupplyAmount) = this + other.copy(numerator = -other.numerator)
    operator fun times(days: Int) = reduced(numerator.toBigInteger()*days.toBigInteger(), denominator.toBigInteger())
    override fun compareTo(other: SupplyAmount) = (numerator.toBigInteger()*other.denominator.toBigInteger()).compareTo(other.numerator.toBigInteger()*denominator.toBigInteger())
    fun nonNegative() = if (numerator < 0) SupplyAmount() else this
    fun ceil(): Int = BigDecimal(numerator).divide(BigDecimal(denominator), 0, RoundingMode.CEILING).intValueExact()
    fun label(): String = BigDecimal(numerator).divide(BigDecimal(denominator), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

@Serializable
data class PlanComposition(val id: String = newId(), val name: String, val items: List<Item>, val color: Long = 0xFF2167B8)
fun UsageTemplate.forPlan() = PlanComposition(name = name, items = items.map { it.copy(batchId = null) }, color = color)

@Serializable
data class WeeklyComposition(val composition: PlanComposition, val count: Int = 1)

@Serializable
data class UsagePattern(
    val mode: String = "HISTORY",
    val base: PlanComposition? = null,
    val alternatives: List<WeeklyComposition> = emptyList(),
    val extras: List<WeeklyComposition> = emptyList(),
    val directItems: List<Item> = emptyList(),
    val directPeriodDays: Int = 7
)

@Serializable
data class PatternChange(val id: String = newId(), val from: String, val pattern: UsagePattern)

@Serializable
data class ReplenishmentInput(
    val visitDate: String = today(), val nextVisitDate: String = "",
    val historyFrom: String = LocalDate.now().minusDays(28).toString(),
    val historyTo: String = LocalDate.now().minusDays(1).toString(),
    val pattern: UsagePattern = UsagePattern(), val changes: List<PatternChange> = emptyList(),
    val bufferDays: Int = 0,
    val stockOverrides: Map<String, Int> = emptyMap(),
    val requestOverrides: Map<String, Int> = emptyMap(),
    // Missing in older backups: retain the original buffer-before-stock formula.
    val calculationVersion: Int = 1,
    val extraQuantities: Map<String, Int> = emptyMap()
)

/** Captured before any delivery: later receipts never enter this calculation twice. */
@Serializable
data class ReplenishmentBasis(val asOf: String, val stocks: Map<String, Int?>, val usedToday: Map<String, Int>,
    val historyFrom: String = "", val historyTo: String = "", val historyDays: Int? = null,
    val historyRates: Map<String, SupplyAmount> = emptyMap())

@Serializable
data class ReplenishmentLine(
    val productId: String, val name: String, val currentStock: Int?,
    val visitStock: SupplyAmount?, val beforeShortage: SupplyAmount,
    val demand: SupplyAmount, val buffer: SupplyAmount,
    val suggested: Int, val requested: Int
)

@Serializable
data class ReplenishmentCalculation(
    val basis: ReplenishmentBasis, val historyDays: Int, val historyTotalDays: Int,
    val days: Int, val lines: List<ReplenishmentLine>
)

@Entity(tableName = "replenishment_plans") @Serializable
data class ReplenishmentPlan(
    @PrimaryKey val id: String = newId(), val input: ReplenishmentInput,
    val calculation: ReplenishmentCalculation, val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt
)
