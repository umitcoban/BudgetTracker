package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_price_history")
data class SubscriptionPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val amount: Long,
    val effectiveFromMonth: String, // "YYYY-MM"
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
