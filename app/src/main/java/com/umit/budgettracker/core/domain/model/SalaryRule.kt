package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class SalaryRule(
    val id: Long,
    val amount: Long,
    val effectiveStartMonth: YearMonth,
    val note: String?
)
