package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_card_statement_rules",
    indices = [Index(value = ["accountId", "effectiveFromMonth"], unique = true)]
)
data class CreditCardStatementRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val effectiveFromMonth: String,
    val statementDay: Int,
    val dueDay: Int
)
