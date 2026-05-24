package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import javax.inject.Inject

class MonthlyBudgetCalculator @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val savingGoalRepository: SavingGoalRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: CategoryBudgetRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val subscriptionCalculator: SubscriptionMonthlyCalculator,
    private val loanCalculator: LoanMonthlyCalculator,
    private val fixedExpenseCalculator: FixedExpenseMonthlyCalculator
) {
    fun getSummaryForMonth(month: YearMonth): Flow<MonthlyBudgetSummary> {
        val incomeFlow = combine(
            salaryRepository.observeAllSalaryRules(),
            savingGoalRepository.observeSavingGoalForMonth(month),
            incomeRepository.observeIncomesForMonth(month)
        ) { salaryRules, savingGoal, incomes ->
            MonthlyIncomeInputs(
                salaryRules = salaryRules,
                savingGoal = savingGoal,
                incomes = incomes
            )
        }

        val expenseFlow = combine(
            expenseRepository.observeAllExpenses(),
            budgetRepository.observeBudgetsForMonth(month),
            adjustmentRepository.observeAllAdjustments()
        ) { expenses, budgets, adjustments ->
            MonthlyExpenseInputs(
                expenses = expenses,
                budgets = budgets,
                adjustments = adjustments
            )
        }

        val baseFlow = combine(
            incomeFlow,
            expenseFlow
        ) { incomeInputs, expenseInputs ->
            MonthlyBudgetInputs(
                salaryRules = incomeInputs.salaryRules,
                savingGoal = incomeInputs.savingGoal,
                incomes = incomeInputs.incomes,
                expenses = expenseInputs.expenses,
                budgets = expenseInputs.budgets,
                adjustments = expenseInputs.adjustments
            )
        }

        return combine(
            baseFlow,
            subscriptionCalculator.getPaymentsForMonth(month),
            loanCalculator.getPaymentsForMonth(month),
            fixedExpenseCalculator.getPaymentsForMonth(month)
        ) { base, subscriptions, loans, fixedExpenses ->
            val applicableSalary = base.salaryRules
                .filter { !it.effectiveStartMonth.isAfter(month) }
                .maxByOrNull { it.effectiveStartMonth }?.amount ?: 0L

            val calendarMonthExpenses = base.expenses.filter { YearMonth.from(it.expenseDate) == month }
            val plannedMonthExpenses = base.expenses.filter { it.planningMonth() == month }
            val adjustmentsByExpenseId = base.adjustments.groupBy { it.expenseId }

            val totalExpenses = plannedMonthExpenses.sumOf { it.netAmount(adjustmentsByExpenseId) }
            val savingGoalAmount = base.savingGoal?.amount ?: 0L
            val additionalIncomeAmount = base.incomes.sumOf { it.amount }
            val totalCardExpense = calendarMonthExpenses
                .filter { it.paymentSourceType == AccountType.CREDIT_CARD }
                .sumOf { it.netAmount(adjustmentsByExpenseId) }
            val creditCardPaymentAmount = plannedMonthExpenses
                .filter { it.paymentSourceType == AccountType.CREDIT_CARD }
                .sumOf { it.netAmount(adjustmentsByExpenseId) }
            val directExpenses = plannedMonthExpenses
                .filter { it.paymentSourceType != AccountType.CREDIT_CARD }
                .sumOf { it.netAmount(adjustmentsByExpenseId) }

            val totalSubscriptionsUnpaid = subscriptions.filter { !it.isPaid }.sumOf { it.amount }
            val totalSubscriptionsPaid = plannedMonthExpenses
                .filter { it.subscriptionId != null }
                .sumOf { it.netAmount(adjustmentsByExpenseId) }
            val totalSubscriptionsPlanned = subscriptions.sumOf { it.amount }
            val totalLoans = loans.sumOf { it.amount }
            val paidFixedExpenseIds = plannedMonthExpenses.mapNotNull { it.fixedExpenseId }.toSet()
            val totalFixedExpenses = fixedExpenses
                .filter { it.fixedExpenseId !in paidFixedExpenseIds }
                .sumOf { it.amount }
            val suggestedSaving = ((applicableSalary + additionalIncomeAmount - totalExpenses - totalSubscriptionsUnpaid - totalLoans - totalFixedExpenses) / 2)
                .coerceAtLeast(0L)

            MonthlyBudgetSummary(
                yearMonth = month,
                salaryAmount = applicableSalary,
                additionalIncomeAmount = additionalIncomeAmount,
                savingGoalAmount = savingGoalAmount,
                totalExpenseAmount = totalExpenses,
                calendarCreditCardSpendingAmount = totalCardExpense,
                creditCardPaymentAmount = creditCardPaymentAmount,
                directExpenseAmount = directExpenses,
                subscriptionAmount = totalSubscriptionsUnpaid,
                subscriptionPlannedAmount = totalSubscriptionsPlanned,
                subscriptionPaidAmount = totalSubscriptionsPaid,
                subscriptionUnpaidPlannedAmount = totalSubscriptionsUnpaid,
                loanPaymentAmount = totalLoans,
                fixedExpenseAmount = totalFixedExpenses,
                suggestedSavingAmount = suggestedSaving,
                categorySummaries = base.budgets.map { budget ->
                    val spent = plannedMonthExpenses
                        .filter { it.categoryId == budget.categoryId }
                        .sumOf { it.netAmount(adjustmentsByExpenseId) }
                    CategorySummary(
                        categoryId = budget.categoryId,
                        categoryName = budget.category?.name ?: "Bilinmeyen",
                        iconName = budget.category?.iconName ?: "category",
                        colorValue = budget.category?.colorValue ?: 0xFF9E9E9E.toInt(),
                        amount = spent,
                        budgetLimit = budget.limitAmount
                    )
                }
            )
        }
    }
}

private fun Expense.netAmount(adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>): Long {
    val adjustmentTotal = adjustmentsByExpenseId[id].orEmpty().sumOf { it.amount }
    return (amount - adjustmentTotal).coerceAtLeast(0L)
}

private fun Expense.planningMonth(): YearMonth {
    if (paymentSourceType != AccountType.CREDIT_CARD) {
        return YearMonth.from(expenseDate)
    }

    val account = account ?: return YearMonth.from(expenseDate)
    val statementDay = account.statementDay ?: return YearMonth.from(expenseDate)
    val dueDay = account.dueDay ?: return YearMonth.from(expenseDate)

    val expenseMonth = YearMonth.from(expenseDate)
    val statementEndMonth = if (expenseDate.dayOfMonth <= statementDay.coerceAtMost(expenseMonth.lengthOfMonth())) {
        expenseMonth
    } else {
        expenseMonth.plusMonths(1)
    }

    return if (dueDay <= statementDay) {
        statementEndMonth.plusMonths(1)
    } else {
        statementEndMonth
    }
}

private data class MonthlyIncomeInputs(
    val salaryRules: List<SalaryRule>,
    val savingGoal: MonthlySavingGoal?,
    val incomes: List<Income>
)

private data class MonthlyExpenseInputs(
    val expenses: List<Expense>,
    val budgets: List<CategoryBudget>,
    val adjustments: List<ExpenseAdjustment>
)

private data class MonthlyBudgetInputs(
    val salaryRules: List<SalaryRule>,
    val savingGoal: MonthlySavingGoal?,
    val incomes: List<Income>,
    val expenses: List<Expense>,
    val budgets: List<CategoryBudget>,
    val adjustments: List<ExpenseAdjustment>
)
