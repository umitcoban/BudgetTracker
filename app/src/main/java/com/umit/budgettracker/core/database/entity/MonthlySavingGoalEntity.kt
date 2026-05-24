package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_saving_goals")
data class MonthlySavingGoalEntity(
    @PrimaryKey val yearMonth: String, // "YYYY-MM"
    val amount: Long, // in minor units
    val note: String? = null
)
