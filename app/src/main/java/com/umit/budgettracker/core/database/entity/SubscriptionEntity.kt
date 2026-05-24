package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long,
    val paymentAccountId: Long,
    val billingDay: Int,
    val isActive: Boolean = true,
    val note: String? = null,
    val cancelledFromMonth: String? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
