package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val principalAmount: Long,
    val monthlyPaymentAmount: Long,
    val installmentCount: Int,
    val startMonth: String, // "YYYY-MM"
    val paymentDay: Int,
    val categoryId: Long? = null,
    val paymentAccountId: Long? = null,
    val note: String? = null,
    val isActive: Boolean = true,
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
