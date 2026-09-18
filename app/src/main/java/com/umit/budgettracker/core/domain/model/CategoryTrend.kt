package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

/** One category's spending across consecutive months, oldest first. */
data class CategoryTrend(
    val categoryId: Long,
    val categoryName: String,
    val iconName: String,
    val colorValue: Int,
    val points: List<CategoryTrendPoint>,
    /** Change of the last month versus the one before, in whole percent; null when there is no base. */
    val changePercent: Int?
) {
    val currentAmount: Long get() = points.lastOrNull()?.amount ?: 0L
    val previousAmount: Long get() = points.getOrNull(points.size - 2)?.amount ?: 0L
}

data class CategoryTrendPoint(
    val month: YearMonth,
    val amount: Long
)

/** A category whose spending in a month is far above its recent monthly average. */
data class CategorySpike(
    val categoryId: Long,
    val categoryName: String,
    val amount: Long,
    val baselineAmount: Long,
    /** How far above the baseline, in whole percent (e.g. 80 means 1.8x). */
    val changePercent: Int
)
