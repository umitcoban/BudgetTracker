package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.RecurringSuggestion
import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import com.umit.budgettracker.core.util.TextMatching
import java.time.LocalDate
import java.time.YearMonth

/** Same-day, same-amount, similar-title expenses that may have been entered twice. */
data class DuplicateCandidate(val title: String, val date: LocalDate, val amount: Long, val count: Int)

data class SubscriptionPriceIncrease(
    val subscriptionId: Long,
    val title: String,
    val previousAmount: Long,
    val newAmount: Long,
    val currency: String,
    val changePercent: Int
)

/** A category that has fallen for [months] consecutive months. */
data class PositiveTrend(val categoryName: String, val fromAmount: Long, val toAmount: Long, val months: Int)

/** Pure anomaly / trend detection feeding [BudgetWarningRules]. */
object AnomalyRules {
    /** A falling category must have dropped at least this much overall to be worth mentioning. */
    const val POSITIVE_TREND_MIN_DROP_PERCENT = 10L

    fun findDuplicates(monthExpenses: List<Expense>): List<DuplicateCandidate> {
        val candidates = monthExpenses.filter {
            it.subscriptionId == null && it.loanId == null && it.fixedExpenseId == null && it.installmentGroupId == null
        }
        val result = mutableListOf<DuplicateCandidate>()
        candidates.groupBy { it.expenseDate to it.amount }.forEach { (key, group) ->
            if (group.size < 2) return@forEach
            val clusters = mutableListOf<MutableList<Expense>>()
            group.forEach { expense ->
                val cluster = clusters.firstOrNull { c -> TextMatching.isSimilar(c.first().title, expense.title, 60) }
                if (cluster != null) cluster += expense else clusters += mutableListOf(expense)
            }
            clusters.filter { it.size >= 2 }.forEach { cluster ->
                result += DuplicateCandidate(cluster.first().title, key.first, key.second, cluster.size)
            }
        }
        return result.sortedByDescending { it.amount }
    }

    /** Subscriptions whose price entry starting in [month] is higher than the previous one (same currency). */
    fun findSubscriptionIncreases(
        month: YearMonth,
        subscriptions: List<Subscription>,
        priceHistory: List<SubscriptionPriceHistory>
    ): List<SubscriptionPriceIncrease> {
        return subscriptions.mapNotNull { sub ->
            val history = priceHistory.filter { it.subscriptionId == sub.id }.sortedBy { it.effectiveFromMonth }
            val current = history.lastOrNull { it.effectiveFromMonth == month } ?: return@mapNotNull null
            val previous = history.lastOrNull { it.effectiveFromMonth.isBefore(month) } ?: return@mapNotNull null
            val currency = current.originalCurrency ?: sub.originalCurrency ?: "TRY"
            val previousCurrency = previous.originalCurrency ?: sub.originalCurrency ?: "TRY"
            if (currency != previousCurrency || previous.amount <= 0L || current.amount <= previous.amount) return@mapNotNull null
            if (!SubscriptionRules.contributesToMonth(sub, priceHistory, month)) return@mapNotNull null
            SubscriptionPriceIncrease(
                subscriptionId = sub.id,
                title = sub.title,
                previousAmount = previous.amount,
                newAmount = current.amount,
                currency = currency,
                changePercent = CategoryTrendRules.percentChange(current.amount, previous.amount)
            )
        }
    }

    /**
     * Categories whose amount fell strictly every month across [summaries] (chronological, the
     * last is the current month) and by at least [POSITIVE_TREND_MIN_DROP_PERCENT] overall.
     */
    fun findPositiveTrends(summaries: List<MonthlyBudgetSummary>): List<PositiveTrend> {
        if (summaries.size < 4) return emptyList()
        val window = summaries.takeLast(4)
        val current = window.last()
        return current.categorySummaries.mapNotNull { category ->
            val amounts = window.map { month ->
                month.categorySummaries.firstOrNull { it.categoryId == category.categoryId }?.amount ?: 0L
            }
            if (amounts.first() <= 0L || amounts.last() <= 0L) return@mapNotNull null
            val strictlyFalling = amounts.zipWithNext().all { (a, b) -> b < a }
            val bigEnough = (amounts.first() - amounts.last()) * 100L >= amounts.first() * POSITIVE_TREND_MIN_DROP_PERCENT
            if (!strictlyFalling || !bigEnough) return@mapNotNull null
            PositiveTrend(category.categoryName, amounts.first(), amounts.last(), amounts.size - 1)
        }.sortedByDescending { it.fromAmount - it.toAmount }
    }
}

/** Finds expenses typed in by hand every month that could become a subscription / fixed expense. */
object RecurringExpenseDetector {
    const val MIN_MONTHS = 3
    const val LOOKBACK_MONTHS = 6L
    /** Each month's amount must be within this percent of the median. */
    const val AMOUNT_TOLERANCE_PERCENT = 15L

    fun detect(
        expenses: List<Expense>,
        subscriptions: List<Subscription>,
        fixedExpenses: List<FixedExpense>,
        dismissedKeys: Set<String>,
        currentMonth: YearMonth
    ): List<RecurringSuggestion> {
        val firstMonth = currentMonth.minusMonths(LOOKBACK_MONTHS - 1)
        val knownKeys = (subscriptions.map { it.title } + fixedExpenses.map { it.title })
            .map { TextMatching.key(it) }
            .toSet()
        return expenses
            .asSequence()
            .filter {
                it.subscriptionId == null && it.loanId == null && it.fixedExpenseId == null && it.installmentGroupId == null
            }
            .filter { !YearMonth.from(it.expenseDate).isBefore(firstMonth) && !YearMonth.from(it.expenseDate).isAfter(currentMonth) }
            .groupBy { TextMatching.key(it.title) }
            .filter { (key, _) -> key.isNotBlank() && key !in dismissedKeys && key !in knownKeys }
            .mapNotNull { (key, group) ->
                // One amount per month (the largest) so a month with two coffees doesn't skew it.
                val perMonth = group.groupBy { YearMonth.from(it.expenseDate) }.mapValues { (_, list) -> list.maxBy { it.amount } }
                if (perMonth.size < MIN_MONTHS) return@mapNotNull null
                val lastMonth = perMonth.keys.max()
                if (lastMonth.isBefore(currentMonth.minusMonths(1))) return@mapNotNull null
                val amounts = perMonth.values.map { it.amount }.sorted()
                val median = amounts[amounts.size / 2]
                if (median <= 0L) return@mapNotNull null
                val consistent = amounts.all { kotlin.math.abs(it - median) * 100L <= median * AMOUNT_TOLERANCE_PERCENT }
                if (!consistent) return@mapNotNull null
                val latest = perMonth.getValue(lastMonth)
                val days = perMonth.values.map { it.expenseDate.dayOfMonth }.sorted()
                RecurringSuggestion(
                    key = key,
                    title = latest.title,
                    typicalAmount = median,
                    typicalDay = days[days.size / 2],
                    monthCount = perMonth.size,
                    categoryId = latest.categoryId,
                    paymentAccountId = latest.paymentAccountId
                )
            }
            .sortedByDescending { it.typicalAmount }
            .toList()
    }
}
