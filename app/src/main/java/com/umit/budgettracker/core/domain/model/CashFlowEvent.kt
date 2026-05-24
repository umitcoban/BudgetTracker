package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class CashFlowEvent(
    val date: LocalDate,
    val title: String,
    val amount: Long,
    val type: CashFlowEventType,
    val sourceId: Long?,
    val description: String?
)

enum class CashFlowEventType {
    INCOME, EXPENSE, CREDIT_CARD_PAYMENT, INSTALLMENT, SUBSCRIPTION, LOAN, FIXED_EXPENSE
}
