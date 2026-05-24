package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val yearMonth: String, // "YYYY-MM"
    val limitAmount: Long,
    val note: String? = null
)
