package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class MonthlyBudgetSummary(
    val yearMonth: YearMonth,
    val salaryAmount: Long = 0,
    val additionalIncomeAmount: Long = 0,
    val savingGoalAmount: Long = 0,
    val totalExpenseAmount: Long = 0,
    val calendarCreditCardSpendingAmount: Long = 0,
    val creditCardPaymentAmount: Long = 0,
    val directExpenseAmount: Long = 0,
    val subscriptionAmount: Long = 0,
    val subscriptionPlannedAmount: Long = 0,
    val subscriptionPaidAmount: Long = 0,
    val subscriptionUnpaidPlannedAmount: Long = 0,
    val loanPaymentAmount: Long = 0,
    val fixedExpenseAmount: Long = 0,
    val suggestedSavingAmount: Long = 0,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val warnings: List<BudgetWarning> = emptyList()
) {
    val totalIncomeAmount: Long get() = salaryAmount + additionalIncomeAmount
    val remainingBeforeSaving: Long get() = totalIncomeAmount - totalExpenseAmount
    val remainingAfterSaving: Long get() = totalIncomeAmount - totalExpenseAmount - savingGoalAmount
    val projectedFixedPaymentsAmount: Long get() = subscriptionAmount + loanPaymentAmount + fixedExpenseAmount
    val remainingAfterFixedPayments: Long get() = totalIncomeAmount - totalExpenseAmount - projectedFixedPaymentsAmount
    val remainingAfterSavingAndFixedPayments: Long get() = totalIncomeAmount - totalExpenseAmount - projectedFixedPaymentsAmount - savingGoalAmount
}

data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val iconName: String,
    val colorValue: Int,
    val amount: Long,
    val budgetLimit: Long? = null,
    val percentage: Float? = null
)

data class BudgetWarning(
    val type: BudgetWarningType,
    val message: String
)

enum class BudgetWarningType {
    NEGATIVE_REMAINING,
    CATEGORY_LIMIT_80_PERCENT,
    CATEGORY_LIMIT_EXCEEDED,
    UPCOMING_CARD_PAYMENT,
    UPCOMING_LOAN_PAYMENT
}
