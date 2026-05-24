package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "salary_rules")
data class SalaryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long, // in minor units (kuruş)
    val effectiveStartMonth: String, // "YYYY-MM"
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
