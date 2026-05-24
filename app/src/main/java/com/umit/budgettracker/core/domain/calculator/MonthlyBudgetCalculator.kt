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
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: CategoryBudgetRepository,
    private val subscriptionCalculator: SubscriptionMonthlyCalculator,
    private val loanCalculator: LoanMonthlyCalculator
) {
    fun getSummaryForMonth(month: YearMonth): Flow<MonthlyBudgetSummary> {
        val baseFlow = combine(
            salaryRepository.observeAllSalaryRules(),
            savingGoalRepository.observeSavingGoalForMonth(month),
            expenseRepository.observeExpensesForMonth(month),
            budgetRepository.observeBudgetsForMonth(month)
        ) { salaryRules, savingGoal, expenses, budgets ->
            MonthlyBudgetInputs(
                salaryRules = salaryRules,
                savingGoal = savingGoal,
                expenses = expenses,
                budgets = budgets
            )
        }

        return combine(
            baseFlow,
            subscriptionCalculator.getPaymentsForMonth(month),
            loanCalculator.getPaymentsForMonth(month)
        ) { base, subscriptions, loans ->
            val applicableSalary = base.salaryRules
                .filter { !it.effectiveStartMonth.isAfter(month) }
                .maxByOrNull { it.effectiveStartMonth }?.amount ?: 0L

            val totalExpenses = base.expenses.sumOf { it.amount }
            val savingGoalAmount = base.savingGoal?.amount ?: 0L
            val totalCardExpense = base.expenses.filter { it.paymentSourceType == AccountType.CREDIT_CARD }.sumOf { it.amount }
            val directExpenses = base.expenses.filter { it.paymentSourceType != AccountType.CREDIT_CARD }.sumOf { it.amount }

            val totalSubscriptionsUnpaid = subscriptions.filter { !it.isPaid }.sumOf { it.amount }
            val totalSubscriptionsPaid = base.expenses.filter { it.subscriptionId != null }.sumOf { it.amount }
            val totalSubscriptionsPlanned = subscriptions.sumOf { it.amount }
            val totalLoans = loans.sumOf { it.amount }

            MonthlyBudgetSummary(
                yearMonth = month,
                salaryAmount = applicableSalary,
                savingGoalAmount = savingGoalAmount,
                totalExpenseAmount = totalExpenses,
                calendarCreditCardSpendingAmount = totalCardExpense,
                creditCardPaymentAmount = 0L,
                directExpenseAmount = directExpenses,
                subscriptionAmount = totalSubscriptionsUnpaid,
                subscriptionPlannedAmount = totalSubscriptionsPlanned,
                subscriptionPaidAmount = totalSubscriptionsPaid,
                subscriptionUnpaidPlannedAmount = totalSubscriptionsUnpaid,
                loanPaymentAmount = totalLoans,
                categorySummaries = base.budgets.map { budget ->
                    val spent = base.expenses.filter { it.categoryId == budget.categoryId }.sumOf { it.amount }
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

private data class MonthlyBudgetInputs(
    val salaryRules: List<SalaryRule>,
    val savingGoal: MonthlySavingGoal?,
    val expenses: List<Expense>,
    val budgets: List<CategoryBudget>
)
