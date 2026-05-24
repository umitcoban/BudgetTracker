package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class CategoryBudget(
    val id: Long,
    val categoryId: Long,
    val yearMonth: YearMonth,
    val limitAmount: Long,
    val note: String?,
    val category: Category? = null
)
