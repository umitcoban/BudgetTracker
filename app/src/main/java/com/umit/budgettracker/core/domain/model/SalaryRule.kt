package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class SalaryRule(
    val id: Long,
    val amount: Long,
    val effectiveStartMonth: YearMonth,
    val note: String?,
    /** Day of month the salary lands; null for rules saved before the field existed. */
    val payDay: Int? = null
)
