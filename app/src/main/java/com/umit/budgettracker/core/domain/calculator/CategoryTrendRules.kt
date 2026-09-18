package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.CategorySpike
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.CategoryTrend
import com.umit.budgettracker.core.domain.model.CategoryTrendPoint
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary

/** Pure, integer-only derivations over per-month [CategorySummary] lists. */
object CategoryTrendRules {
    /** Months averaged to form the spike baseline. */
    const val BASELINE_MONTHS = 3

    /** A month must reach this percent of its baseline to count as a spike (150 = 1.5x). */
    const val SPIKE_THRESHOLD_PERCENT = 150L

    /** Small categories are ignored: the spike month must be at least this share of total spending. */
    const val SPIKE_MIN_SHARE_PERCENT = 5L

    /**
     * One trend per category seen in any of [summaries] (chronological), with a zero point for
     * months where the category had no spending. Sorted by the latest month's amount, largest first.
     */
    fun buildTrends(summaries: List<MonthlyBudgetSummary>): List<CategoryTrend> {
        if (summaries.isEmpty()) return emptyList()
        val byCategory = summaries
            .flatMap { it.categorySummaries }
            .groupBy { it.categoryId }

        return byCategory.map { (categoryId, occurrences) ->
            val descriptor = occurrences.first()
            val amountsByMonth = summaries.associate { summary ->
                summary.yearMonth to (summary.categorySummaries.firstOrNull { it.categoryId == categoryId }?.amount ?: 0L)
            }
            val points = summaries.map { CategoryTrendPoint(it.yearMonth, amountsByMonth.getValue(it.yearMonth)) }
            val current = points.last().amount
            val previous = points.getOrNull(points.size - 2)?.amount
            CategoryTrend(
                categoryId = categoryId,
                categoryName = descriptor.categoryName,
                iconName = descriptor.iconName,
                colorValue = descriptor.colorValue,
                points = points,
                changePercent = previous?.takeIf { it > 0L }?.let { percentChange(current, it) }
            )
        }.sortedWith(compareByDescending<CategoryTrend> { it.currentAmount }.thenBy { it.categoryName })
    }

    /**
     * Categories in [current] spending at least [SPIKE_THRESHOLD_PERCENT] of their average over
     * [previous] (the [BASELINE_MONTHS] months before, any order). Requires a full baseline so a
     * brand-new data set does not flag everything, and skips categories below
     * [SPIKE_MIN_SHARE_PERCENT] of the month's total.
     */
    fun detectSpikes(
        current: MonthlyBudgetSummary,
        previous: List<MonthlyBudgetSummary>
    ): List<CategorySpike> {
        if (previous.size < BASELINE_MONTHS || current.totalExpenseAmount <= 0L) return emptyList()
        val baselineMonths = previous.takeLast(BASELINE_MONTHS)

        return current.categorySummaries.mapNotNull { category ->
            val baseline = baselineMonths.sumOf { month ->
                month.categorySummaries.firstOrNull { it.categoryId == category.categoryId }?.amount ?: 0L
            } / BASELINE_MONTHS
            if (baseline <= 0L) return@mapNotNull null

            val reachesThreshold = category.amount * 100L >= baseline * SPIKE_THRESHOLD_PERCENT
            val isMaterial = category.amount * 100L >= current.totalExpenseAmount * SPIKE_MIN_SHARE_PERCENT
            if (!reachesThreshold || !isMaterial) return@mapNotNull null

            CategorySpike(
                categoryId = category.categoryId,
                categoryName = category.categoryName,
                amount = category.amount,
                baselineAmount = baseline,
                changePercent = percentChange(category.amount, baseline)
            )
        }.sortedByDescending { it.changePercent }
    }

    /** `(current - base) / base` in whole percent, integer arithmetic only; [base] must be > 0. */
    fun percentChange(current: Long, base: Long): Int {
        return ((current - base) * 100L / base).toInt()
    }
}
