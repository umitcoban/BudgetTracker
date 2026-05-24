package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class InstallmentGroup(
    val id: Long,
    val title: String,
    val totalAmount: Long,
    val installmentCount: Int,
    val startDate: LocalDate,
    val categoryId: Long,
    val paymentAccountId: Long,
    val note: String?,
    val category: Category? = null,
    val account: PaymentAccount? = null
)
