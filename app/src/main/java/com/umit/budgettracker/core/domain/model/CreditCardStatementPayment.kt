package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class CreditCardStatementPayment(
    val id: Long,
    val accountId: Long,
    val paymentMonth: YearMonth,
    val amountAtPayment: Long,
    val isPaid: Boolean,
    val paidAt: Long?
)
