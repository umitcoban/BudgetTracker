package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class Income(
    val id: Long,
    val title: String,
    val amount: Long,
    val incomeDate: LocalDate,
    val type: IncomeType,
    val note: String?
)

enum class IncomeType {
    EXTRA,
    BONUS,
    FREELANCE,
    SALE,
    DEBT_COLLECTION,
    OTHER
}
