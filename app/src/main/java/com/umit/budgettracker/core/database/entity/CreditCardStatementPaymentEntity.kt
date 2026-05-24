package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_card_statement_payments",
    indices = [
        Index(value = ["accountId", "paymentMonth"], unique = true)
    ]
)
data class CreditCardStatementPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val paymentMonth: String,
    val amountAtPayment: Long,
    val isPaid: Boolean,
    val paidAt: Long? = null
)
