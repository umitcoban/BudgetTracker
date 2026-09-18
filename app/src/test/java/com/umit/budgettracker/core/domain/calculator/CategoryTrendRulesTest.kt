package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CategoryTrendRulesTest {
    private val may = YearMonth.of(2026, 5)

    @Test
    fun buildTrends_fillsMissingMonthsWithZeroAndSortsByLatestAmount() {
        val summaries = listOf(
            summary(may.minusMonths(2), "Market" to 30_000L, "Kahve" to 2_000L),
            summary(may.minusMonths(1), "Market" to 40_000L),
            summary(may, "Market" to 50_000L, "Kahve" to 6_000L, "Yakıt" to 90_000L)
        )

        val trends = CategoryTrendRules.buildTrends(summaries)

        assertEquals(listOf("Yakıt", "Market", "Kahve"), trends.map { it.categoryName })
        val kahve = trends.last()
        assertEquals(listOf(2_000L, 0L, 6_000L), kahve.points.map { it.amount })
        assertEquals(null, kahve.changePercent) // previous month was 0 -> no base
        assertEquals(25, trends[1].changePercent) // 40k -> 50k
        assertEquals(null, trends.first().changePercent)
    }

    @Test
    fun detectSpikes_flagsCategoryAtOrAbove150PercentOfThreeMonthAverage() {
        val previous = listOf(
            summary(may.minusMonths(3), "Market" to 30_000L, "Yemek" to 10_000L),
            summary(may.minusMonths(2), "Market" to 30_000L, "Yemek" to 10_000L),
            summary(may.minusMonths(1), "Market" to 30_000L, "Yemek" to 10_000L)
        )
        val current = summary(may, "Market" to 45_000L, "Yemek" to 14_000L)

        val spikes = CategoryTrendRules.detectSpikes(current, previous)

        val spike = spikes.single()
        assertEquals("Market", spike.categoryName)
        assertEquals(30_000L, spike.baselineAmount)
        assertEquals(50, spike.changePercent)
    }

    @Test
    fun detectSpikes_ignoresTinyCategoriesAndIncompleteBaselines() {
        val previous = listOf(
            summary(may.minusMonths(3), "Market" to 100_000L, "Kahve" to 1_000L),
            summary(may.minusMonths(2), "Market" to 100_000L, "Kahve" to 1_000L),
            summary(may.minusMonths(1), "Market" to 100_000L, "Kahve" to 1_000L)
        )
        // Kahve tripled but is 3% of the month: below the 5% materiality floor.
        val current = summary(may, "Market" to 97_000L, "Kahve" to 3_000L)

        assertTrue(CategoryTrendRules.detectSpikes(current, previous).isEmpty())
        assertTrue(CategoryTrendRules.detectSpikes(current, previous.drop(1)).isEmpty())
    }

    @Test
    fun detectSpikes_skipsCategoriesWithNoHistory() {
        val previous = (3 downTo 1).map { summary(may.minusMonths(it.toLong()), "Market" to 30_000L) }
        val current = summary(may, "Market" to 30_000L, "Tatil" to 80_000L)

        assertTrue(CategoryTrendRules.detectSpikes(current, previous).isEmpty())
    }

    private fun summary(month: YearMonth, vararg categories: Pair<String, Long>) = MonthlyBudgetSummary(
        yearMonth = month,
        totalExpenseAmount = categories.sumOf { it.second },
        categorySummaries = categories.map { (name, amount) ->
            CategorySummary(
                categoryId = name.hashCode().toLong(),
                categoryName = name,
                iconName = "category",
                colorValue = 0,
                amount = amount
            )
        }
    )
}
