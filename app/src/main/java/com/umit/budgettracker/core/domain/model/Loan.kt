package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class Loan(
    val id: Long,
    val title: String,
    val principalAmount: Long,
    val monthlyPaymentAmount: Long,
    val installmentCount: Int,
    val startMonth: YearMonth,
    val paymentDay: Int,
    val categoryId: Long?,
    val paymentAccountId: Long?,
    val note: String?,
    val isActive: Boolean,
    val category: Category? = null,
    val account: PaymentAccount? = null
)

data class LoanMonthlyPayment(
    val loanId: Long,
    val title: String,
    val amount: Long,
    val paymentDay: Int,
    val currentInstallment: Int,
    val totalInstallments: Int,
    val remainingAmount: Long,
    val categoryId: Long?,
    val paymentAccountId: Long?,
    val category: Category? = null,
    val account: PaymentAccount? = null
)
