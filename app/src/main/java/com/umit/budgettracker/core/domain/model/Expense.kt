package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class Expense(
    val id: Long,
    val title: String,
    val amount: Long,
    val expenseDate: LocalDate,
    val categoryId: Long,
    val paymentAccountId: Long,
    val paymentSourceType: AccountType,
    val note: String?,
    val installmentGroupId: Long? = null,
    val subscriptionId: Long? = null,
    val loanId: Long? = null,
    val fixedExpenseId: Long? = null,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null,
    val category: Category? = null,
    val account: PaymentAccount? = null
)
