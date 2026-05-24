package com.umit.budgettracker.core.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class CreditCardStatementSummary(
    val accountId: Long,
    val accountName: String,
    val paymentMonth: YearMonth,
    val statementStartDate: LocalDate,
    val statementEndDate: LocalDate,
    val dueDate: LocalDate,
    val totalAmount: Long,
    val expenses: List<Expense>
)
