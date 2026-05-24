package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fixed_expenses",
    indices = [Index("startMonth"), Index("endMonth")]
)
data class FixedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val dayOfMonth: Int,
    val startMonth: String,
    val endMonth: String? = null,
    val categoryId: Long? = null,
    val paymentAccountId: Long? = null,
    val note: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
