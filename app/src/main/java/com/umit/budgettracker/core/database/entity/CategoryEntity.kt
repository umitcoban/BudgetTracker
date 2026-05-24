package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorValue: Int,
    val type: String, // EXPENSE, INCOME, SYSTEM
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
