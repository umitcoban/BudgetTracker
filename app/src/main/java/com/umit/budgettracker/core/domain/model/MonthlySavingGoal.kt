package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class MonthlySavingGoal(
    val yearMonth: YearMonth,
    val amount: Long,
    val note: String?
)
