package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.InflationRate
import java.math.BigInteger
import java.time.YearMonth

/**
 * Compounding over user-entered monthly CPI rates, integer arithmetic only.
 *
 * A price-level factor is kept as an exact fraction numerator/10000ⁿ and only rounded once when
 * applied to an amount. Months without a rate are filled with the latest earlier known rate and
 * reported as estimated, so the UI can label the result "tahmini".
 */
object InflationRules {
    /** Entered annual vs. compounded 12-month rate may differ by at most this much (0,1 puan). */
    const val CONSISTENCY_TOLERANCE_BP = 10

    data class Factor(
        val numerator: BigInteger,
        val denominator: BigInteger,
        val estimatedMonths: List<YearMonth>,
        val monthCount: Int
    ) {
        val isEstimated: Boolean get() = estimatedMonths.isNotEmpty()

        fun apply(amount: Long): Long = AssetValuationRules.roundHalfUp(
            BigInteger.valueOf(amount).multiply(numerator),
            denominator
        ).toLong()

        /** (factor − 1) in basis points. */
        fun changeBp(): Int = AssetValuationRules.roundHalfUp(
            (numerator - denominator).multiply(BigInteger.valueOf(10_000L)),
            denominator
        ).toInt()
    }

    /**
     * Price-level growth from the end of [from] to the end of [to] (the rates of months
     * `from+1 .. to`). Returns null when no rate at all is known at or before a needed month.
     */
    fun factor(rates: List<InflationRate>, from: YearMonth, to: YearMonth): Factor? {
        if (!to.isAfter(from)) {
            return Factor(BigInteger.ONE, BigInteger.ONE, emptyList(), 0)
        }
        val byMonth = rates.associateBy { it.month }
        val sorted = rates.sortedBy { it.month }
        var numerator = BigInteger.ONE
        var denominator = BigInteger.ONE
        val estimated = mutableListOf<YearMonth>()
        var month = from.plusMonths(1)
        var count = 0
        while (!month.isAfter(to)) {
            val known = byMonth[month]
            val rateBp = if (known != null) {
                known.monthlyRateBp
            } else {
                estimated += month
                sorted.lastOrNull { it.month.isBefore(month) }?.monthlyRateBp ?: return null
            }
            numerator = numerator.multiply(BigInteger.valueOf(10_000L + rateBp))
            denominator = denominator.multiply(BigInteger.valueOf(10_000L))
            month = month.plusMonths(1)
            count++
        }
        return Factor(numerator, denominator, estimated, count)
    }

    /** [amount] spent in [amountMonth], expressed in [targetMonth] money. */
    fun realValue(rates: List<InflationRate>, amount: Long, amountMonth: YearMonth, targetMonth: YearMonth): RealValue? {
        val factor = factor(rates, amountMonth, targetMonth) ?: return null
        return RealValue(factor.apply(amount), factor.isEstimated, factor.changeBp())
    }

    /**
     * Real change of [newAmount] (in [newMonth]) against [oldAmount] (in [oldMonth]) after
     * inflation, in basis points. Example: salary 50.000 → 60.000 while prices rose %25 gives −400.
     */
    fun realChangeBp(rates: List<InflationRate>, oldAmount: Long, oldMonth: YearMonth, newAmount: Long, newMonth: YearMonth): Int? {
        if (oldAmount <= 0L) return null
        val adjustedOld = factor(rates, oldMonth, newMonth)?.apply(oldAmount) ?: return null
        if (adjustedOld <= 0L) return null
        return AssetValuationRules.roundHalfUp(
            BigInteger.valueOf(newAmount - adjustedOld).multiply(BigInteger.valueOf(10_000L)),
            BigInteger.valueOf(adjustedOld)
        ).toInt()
    }

    /** Compounded rate of the twelve months ending at [month]; null unless all twelve are entered. */
    fun compoundedAnnualBp(rates: List<InflationRate>, month: YearMonth): Int? {
        val byMonth = rates.associateBy { it.month }
        val window = (11 downTo 0).map { month.minusMonths(it.toLong()) }
        if (window.any { it !in byMonth }) return null
        return factor(rates, month.minusMonths(12), month)?.changeBp()
    }

    /** Months whose entered annual rate disagrees with the compounded monthly rates. */
    fun inconsistentMonths(rates: List<InflationRate>): List<Inconsistency> {
        return rates.mapNotNull { rate ->
            val entered = rate.annualRateBp ?: return@mapNotNull null
            val compounded = compoundedAnnualBp(rates, rate.month) ?: return@mapNotNull null
            if (kotlin.math.abs(compounded - entered) > CONSISTENCY_TOLERANCE_BP) {
                Inconsistency(rate.month, entered, compounded)
            } else {
                null
            }
        }
    }

    /** Months between the first entered month and [upTo] that have no rate yet. */
    fun missingMonths(rates: List<InflationRate>, upTo: YearMonth): List<YearMonth> {
        val first = rates.minOfOrNull { it.month } ?: return emptyList()
        val known = rates.map { it.month }.toSet()
        val result = mutableListOf<YearMonth>()
        var month = first
        while (!month.isAfter(upTo)) {
            if (month !in known) result += month
            month = month.plusMonths(1)
        }
        return result
    }

    /** "%1,84" style label for basis points. */
    fun formatBp(bp: Int): String {
        val sign = if (bp < 0) "−" else ""
        val abs = kotlin.math.abs(bp)
        return "$sign%${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    /**
     * Parses "1,84", "%1,84", "1.84" or "29,73" into basis points without floating point.
     * At most two decimals are kept; extra digits are rounded half-up.
     */
    fun parseBp(text: String): Int? {
        val cleaned = text.trim().removePrefix("%").removeSuffix("%").trim().replace('−', '-').replace(',', '.')
        if (cleaned.isEmpty()) return null
        val negative = cleaned.startsWith("-")
        val body = cleaned.removePrefix("-").removePrefix("+")
        val parts = body.split('.')
        if (parts.size > 2 || body.isEmpty() || body == ".") return null
        if (parts[0].any { !it.isDigit() }) return null
        val whole = parts[0].ifEmpty { "0" }.toIntOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.any { !it.isDigit() }) return null
        val padded = (fraction + "000").take(3)
        var bp = whole * 100 + padded.take(2).toInt()
        if (padded[2] >= '5') bp += 1
        return if (negative) -bp else bp
    }

    data class RealValue(val amount: Long, val isEstimated: Boolean, val inflationBp: Int)

    data class Inconsistency(val month: YearMonth, val enteredBp: Int, val compoundedBp: Int)

    /** TCMB "Consumer Prices" values for Oct 2025 – Sep 2026 (monthly %, annual %), confirmed 2026-10-13. */
    val SEED: List<InflationRate> = listOf(
        "2025-10" to (255 to 3287),
        "2025-11" to (87 to 3107),
        "2025-12" to (89 to 3089),
        "2026-01" to (484 to 3065),
        "2026-02" to (296 to 3153),
        "2026-03" to (194 to 3087),
        "2026-04" to (418 to 3237),
        "2026-05" to (171 to 3261),
        "2026-06" to (99 to 3211),
        "2026-07" to (178 to 3175),
        "2026-08" to (184 to 3151),
        "2026-09" to (184 to 2973)
    ).map { (month, rates) ->
        InflationRate(
            month = YearMonth.parse(month),
            monthlyRateBp = rates.first,
            annualRateBp = rates.second,
            note = null,
            source = "TCMB (başlangıç verisi)"
        )
    }
}
