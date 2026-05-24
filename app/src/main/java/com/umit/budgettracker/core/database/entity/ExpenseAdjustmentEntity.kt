package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_adjustments",
    indices = [Index("expenseId")]
)
data class ExpenseAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val amount: Long,
    val type: String,
    val adjustmentDate: Long,
    val note: String? = null
)
