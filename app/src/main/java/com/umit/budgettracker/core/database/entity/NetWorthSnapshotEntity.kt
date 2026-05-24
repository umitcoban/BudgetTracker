package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String, // "YYYY-MM"
    val cashAmount: Long,
    val bankAmount: Long,
    val investmentAmount: Long,
    val creditCardDebt: Long,
    val loanDebt: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
