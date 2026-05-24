package com.umit.budgettracker.core.domain.model

data class PaymentAccount(
    val id: Long,
    val name: String,
    val type: AccountType,
    val statementDay: Int?,
    val dueDay: Int?,
    val isActive: Boolean
)

enum class AccountType {
    CASH, BANK_ACCOUNT, CREDIT_CARD
}
