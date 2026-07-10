package com.umit.budgettracker.core.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class LoanPayment(
    val id: Long,
    val loanId: Long,
    val paymentMonth: YearMonth,
    val amount: Long,
    val paidAt: LocalDate
)
