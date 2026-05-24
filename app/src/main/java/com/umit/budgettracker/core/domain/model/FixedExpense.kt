package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class FixedExpense(
    val id: Long,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    val categoryId: Long?,
    val paymentAccountId: Long?,
    val note: String?,
    val isActive: Boolean,
    val category: Category? = null,
    val account: PaymentAccount? = null
)

data class FixedExpenseMonthlyPayment(
    val fixedExpenseId: Long,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int,
    val categoryId: Long?,
    val paymentAccountId: Long?,
    val category: Category? = null,
    val account: PaymentAccount? = null
)
