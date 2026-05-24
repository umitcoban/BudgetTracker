package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class DebtRecord(
    val id: Long,
    val title: String,
    val personName: String?,
    val amount: Long,
    val type: DebtType,
    val dueDate: LocalDate?,
    val isPaid: Boolean,
    val note: String?
)

enum class DebtType {
    I_OWE, OWED_TO_ME
}
