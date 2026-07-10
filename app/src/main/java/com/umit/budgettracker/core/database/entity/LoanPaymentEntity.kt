package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_payments",
    indices = [Index(value = ["loanId", "paymentMonth"], unique = true)]
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val paymentMonth: String,
    val amount: Long,
    val paidAt: Long
)
