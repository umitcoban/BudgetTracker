package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class ExpenseAdjustment(
    val id: Long,
    val expenseId: Long,
    val amount: Long,
    val type: ExpenseAdjustmentType,
    val adjustmentDate: LocalDate,
    val note: String?
)

enum class ExpenseAdjustmentType {
    REFUND
}
