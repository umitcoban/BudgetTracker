package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class CreditCardStatementRule(
    val id: Long,
    val accountId: Long,
    val effectiveFromMonth: YearMonth,
    val statementDay: Int,
    val dueDay: Int
)
