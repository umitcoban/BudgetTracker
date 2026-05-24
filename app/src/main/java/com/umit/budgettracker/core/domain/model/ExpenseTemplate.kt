package com.umit.budgettracker.core.domain.model

data class ExpenseTemplate(
    val id: Long,
    val title: String,
    val defaultAmount: Long?,
    val categoryId: Long,
    val paymentAccountId: Long?,
    val note: String?,
    val isActive: Boolean,
    val category: Category? = null,
    val account: PaymentAccount? = null
)
