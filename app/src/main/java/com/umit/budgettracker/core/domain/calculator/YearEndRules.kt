package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.InflationRate
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import java.time.YearMonth

data class YearCategoryTotal(val categoryId: Long, val name: String, val colorValue: Int, val iconName: String, val amount: Long)

data class YearEndReport(
    val year: Int,
    val months: List<MonthlyBudgetSummary>,
    val totalIncome: Long,
    val totalExpense: Long,
    /** Income minus spending and planned payments, summed over the months. */
    val totalLeftover: Long,
    val totalSavingGoal: Long,
    val mostExpensiveMonth: MonthlyBudgetSummary?,
    val cheapestMonth: MonthlyBudgetSummary?,
    val topCategories: List<YearCategoryTotal>,
    val subscriptionTotal: Long,
    val feeTotal: Long,
    val feeCount: Int,
    val previousIncome: Long,
    val previousExpense: Long,
    /** Nominal change of spending vs the same months a year earlier, whole percent. */
    val expenseChangePercent: Int?,
    val incomeChangePercent: Int?,
    /** After inflation, basis points; null without CPI data. */
    val realExpenseChangeBp: Int?,
    val realIncomeChangeBp: Int?,
    /** Price-level growth over the comparison window, basis points. */
    val inflationBp: Int?,
    val inflationEstimated: Boolean
) {
    val lastMonth: YearMonth? get() = months.lastOrNull()?.yearMonth
}

object YearEndRules {
    /** Bank fees and interest recognised from expense titles. */
    private val FEE_PATTERN = Regex(
        "(faiz|komisyon|kart aidat|kart ücret|kart ucret|yıllık ücret|yillik ucret|hesap işletim|hesap isletim|bsmv|kkdf|gecikme|masraf|eft ücret|havale ücret)",
        RegexOption.IGNORE_CASE
    )

    fun isFee(expense: Expense): Boolean = FEE_PATTERN.containsMatchIn(expense.title)

    /**
     * [months] are this year's summaries up to the latest included month, [previous] the same
     * calendar months one year earlier (same order). [yearExpenses] are the calendar-year expenses
     * used for fee detection.
     */
    fun build(
        year: Int,
        months: List<MonthlyBudgetSummary>,
        previous: List<MonthlyBudgetSummary>,
        yearExpenses: List<Expense>,
        rates: List<InflationRate>
    ): YearEndReport {
        val totalIncome = months.sumOf { it.totalIncomeAmount }
        val totalExpense = months.sumOf { it.totalExpenseAmount }
        val previousIncome = previous.sumOf { it.totalIncomeAmount }
        val previousExpense = previous.sumOf { it.totalExpenseAmount }
        val active = months.filter { it.totalExpenseAmount > 0L }
        val fees = yearExpenses.filter { it.expenseDate.year == year && isFee(it) }
        val lastMonth = months.lastOrNull()?.yearMonth
        val factor = lastMonth?.let { InflationRules.factor(rates, it.minusMonths(12), it) }

        val categories = months
            .flatMap { it.categorySummaries }
            .groupBy { it.categoryId }
            .map { (id, items) ->
                val first = items.first()
                YearCategoryTotal(id, first.categoryName, first.colorValue, first.iconName, items.sumOf { it.amount })
            }
            .filter { it.amount > 0L }
            .sortedByDescending { it.amount }

        return YearEndReport(
            year = year,
            months = months,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            totalLeftover = months.sumOf { it.remainingAfterFixedPayments },
            totalSavingGoal = months.sumOf { it.savingGoalAmount },
            mostExpensiveMonth = active.maxByOrNull { it.totalExpenseAmount },
            cheapestMonth = active.minByOrNull { it.totalExpenseAmount },
            topCategories = categories,
            subscriptionTotal = months.sumOf { it.subscriptionPaidAmount + it.subscriptionUnpaidPlannedAmount },
            feeTotal = fees.sumOf { it.amount },
            feeCount = fees.size,
            previousIncome = previousIncome,
            previousExpense = previousExpense,
            expenseChangePercent = previousExpense.takeIf { it > 0L }?.let { CategoryTrendRules.percentChange(totalExpense, it) },
            incomeChangePercent = previousIncome.takeIf { it > 0L }?.let { CategoryTrendRules.percentChange(totalIncome, it) },
            realExpenseChangeBp = lastMonth?.let {
                InflationRules.realChangeBp(rates, previousExpense, it.minusMonths(12), totalExpense, it)
            },
            realIncomeChangeBp = lastMonth?.let {
                InflationRules.realChangeBp(rates, previousIncome, it.minusMonths(12), totalIncome, it)
            },
            inflationBp = factor?.changeBp(),
            inflationEstimated = factor?.isEstimated ?: false
        )
    }
}
