package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import java.time.YearMonth

/** A read-only "what if" change. Nothing here touches stored records. */
sealed interface Scenario {
    val label: String

    data class CancelSubscription(val subscriptionId: Long, val title: String, val fromMonth: YearMonth) : Scenario {
        override val label get() = "$title iptal"
    }

    /** [payoffAmount] null = pay the remaining instalments as they are (no discount). */
    data class CloseLoanEarly(val loanId: Long, val title: String, val month: YearMonth, val payoffAmount: Long?) : Scenario {
        override val label get() = "$title erken kapama"
    }

    data class CutCategory(val categoryId: Long, val name: String, val percent: Int) : Scenario {
        override val label get() = "$name %$percent azalt"
    }

    data class SalaryChange(val percent: Int, val fromMonth: YearMonth) : Scenario {
        override val label get() = "Maaş %$percent zam"
    }

    data class FixedExpenseRaise(val fixedExpenseId: Long, val title: String, val percent: Int, val fromMonth: YearMonth) : Scenario {
        override val label get() = "$title %$percent zam"
    }

    /** Positive [amount] is a new monthly cost, negative a new monthly income. */
    data class ExtraMonthly(val title: String, val amount: Long, val fromMonth: YearMonth) : Scenario {
        override val label get() = title
    }
}

data class SimulationMonth(
    val month: YearMonth,
    val baselineNet: Long,
    val scenarioNet: Long,
    val baselineCumulative: Long,
    val scenarioCumulative: Long
) {
    val difference: Long get() = scenarioNet - baselineNet
}

data class SimulationResult(
    val months: List<SimulationMonth>,
    /** Average variable (hand-entered) spending assumed for future months. */
    val assumedVariableSpending: Long
) {
    val totalDifference: Long get() = months.sumOf { it.difference }
    val baselineTotal: Long get() = months.lastOrNull()?.baselineCumulative ?: 0L
    val scenarioTotal: Long get() = months.lastOrNull()?.scenarioCumulative ?: 0L
}

/**
 * 12-month savings projection built on [MonthlyBudgetSummary] (salary rules, planned
 * subscriptions/loans/fixed expenses, already-generated installments) plus an assumed
 * variable spending equal to the recent average, so future months are not unrealistically empty.
 */
object SimulationRules {
    fun averageVariableSpending(past: List<MonthlyBudgetSummary>): Long {
        val months = past.filter { it.variableExpenseAmount > 0L }
        if (months.isEmpty()) return 0L
        return months.sumOf { it.variableExpenseAmount } / months.size
    }

    fun averageCategorySpending(past: List<MonthlyBudgetSummary>, categoryId: Long): Long {
        if (past.isEmpty()) return 0L
        return past.sumOf { month -> month.categorySummaries.firstOrNull { it.categoryId == categoryId }?.amount ?: 0L } / past.size
    }

    /** Expected leftover of [summary] with the variable spending still to come. */
    fun baselineNet(summary: MonthlyBudgetSummary, currentMonth: YearMonth, variableAverage: Long): Long {
        val stillToSpend = when {
            summary.yearMonth.isAfter(currentMonth) -> variableAverage
            summary.yearMonth == currentMonth -> (variableAverage - summary.variableExpenseAmount).coerceAtLeast(0L)
            else -> 0L
        }
        return summary.totalIncomeAmount - summary.totalExpenseAmount - summary.projectedFixedPaymentsAmount - stillToSpend
    }

    fun simulate(
        future: List<MonthlyBudgetSummary>,
        past: List<MonthlyBudgetSummary>,
        currentMonth: YearMonth,
        scenarios: List<Scenario>
    ): SimulationResult {
        val variableAverage = averageVariableSpending(past)
        var baselineCumulative = 0L
        var scenarioCumulative = 0L
        val loanPayoffMonths = scenarios.filterIsInstance<Scenario.CloseLoanEarly>().associate { scenario ->
            scenario.loanId to future.firstOrNull { summary ->
                !summary.yearMonth.isBefore(scenario.month) && summary.loanPayments.any { it.loanId == scenario.loanId }
            }?.yearMonth
        }
        val months = future.map { summary ->
            val baseline = baselineNet(summary, currentMonth, variableAverage)
            val delta = scenarios.sumOf { scenario -> delta(scenario, summary, past, loanPayoffMonths) }
            baselineCumulative += baseline
            scenarioCumulative += baseline + delta
            SimulationMonth(summary.yearMonth, baseline, baseline + delta, baselineCumulative, scenarioCumulative)
        }
        return SimulationResult(months, variableAverage)
    }

    /** Effect of one scenario on one month's leftover; positive means more money left. */
    private fun delta(
        scenario: Scenario,
        summary: MonthlyBudgetSummary,
        past: List<MonthlyBudgetSummary>,
        loanPayoffMonths: Map<Long, YearMonth?>
    ): Long {
        val month = summary.yearMonth
        return when (scenario) {
            is Scenario.CancelSubscription -> if (month.isBefore(scenario.fromMonth)) 0L else {
                summary.subscriptionPayments.filter { it.subscriptionId == scenario.subscriptionId && !it.isPaid }.sumOf { it.amount }
            }
            is Scenario.CloseLoanEarly -> {
                val payoffMonth = loanPayoffMonths[scenario.loanId] ?: return 0L
                val payment = summary.loanPayments.firstOrNull { it.loanId == scenario.loanId }
                when {
                    month.isBefore(payoffMonth) -> 0L
                    month == payoffMonth && payment != null -> {
                        val fullRemaining = payment.amount + payment.remainingAmount
                        payment.amount - (scenario.payoffAmount ?: fullRemaining)
                    }
                    else -> payment?.amount ?: 0L
                }
            }
            // Every simulated month is the current one or later, so the cut applies throughout.
            is Scenario.CutCategory -> averageCategorySpending(past, scenario.categoryId) * scenario.percent / 100L
            is Scenario.SalaryChange -> if (month.isBefore(scenario.fromMonth)) 0L else {
                summary.salaryAmount * scenario.percent / 100L
            }
            is Scenario.FixedExpenseRaise -> if (month.isBefore(scenario.fromMonth)) 0L else {
                -summary.fixedExpensePayments.filter { it.fixedExpenseId == scenario.fixedExpenseId }.sumOf { it.amount } * scenario.percent / 100L
            }
            is Scenario.ExtraMonthly -> if (month.isBefore(scenario.fromMonth)) 0L else -scenario.amount
        }
    }
}
