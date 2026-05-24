package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PaymentAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("categoryId"),
        Index("paymentAccountId"),
        Index("expenseDate")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val expenseDate: Long, // LocalDate to EpochDay or millis
    val categoryId: Long,
    val paymentAccountId: Long,
    val paymentSourceType: String, // CASH, BANK_ACCOUNT, CREDIT_CARD
    val note: String? = null,
    val installmentGroupId: Long? = null,
    val subscriptionId: Long? = null,
    val loanId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
