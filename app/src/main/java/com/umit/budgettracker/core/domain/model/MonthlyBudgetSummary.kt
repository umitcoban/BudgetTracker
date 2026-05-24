package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class MonthlyBudgetSummary(
    val yearMonth: YearMonth,
    val salaryAmount: Long = 0,
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
    val categorySummaries: List<CategorySummary> = emptyList(),
    val warnings: List<BudgetWarning> = emptyList()
) {
    val remainingBeforeSaving: Long get() = salaryAmount - totalExpenseAmount
    val remainingAfterSaving: Long get() = salaryAmount - totalExpenseAmount - savingGoalAmount
    val projectedFixedPaymentsAmount: Long get() = subscriptionAmount + loanPaymentAmount
    val remainingAfterFixedPayments: Long get() = salaryAmount - totalExpenseAmount - projectedFixedPaymentsAmount
    val remainingAfterSavingAndFixedPayments: Long get() = salaryAmount - totalExpenseAmount - projectedFixedPaymentsAmount - savingGoalAmount
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
