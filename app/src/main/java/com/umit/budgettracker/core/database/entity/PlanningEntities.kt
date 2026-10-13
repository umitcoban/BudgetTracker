package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables added in database version 17. Money is Long kuruş; prices/quantities use the scales in
// AssetValuationRules; dates are epoch days; months are "yyyy-MM".

@Entity(tableName = "inflation_rates")
data class InflationRateEntity(
    @PrimaryKey val month: String,
    val monthlyRateBp: Int,
    val annualRateBp: Int?,
    val note: String?,
    val source: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "assets", indices = [Index("priceCode")])
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val priceCode: String?,
    val quantity: Long,
    val manualValue: Long?,
    val costAmount: Long?,
    val allocationTag: String?,
    val interestRateBp: Int?,
    val startDate: Long?,
    val maturityDate: Long?,
    val isEmergencyFund: Boolean,
    val isActive: Boolean,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "market_prices")
data class MarketPriceEntity(
    @PrimaryKey val code: String,
    val name: String,
    val price: Long,
    val previousPrice: Long?,
    val source: String,
    val updatedAt: Long
)

@Entity(tableName = "price_alerts", indices = [Index("priceCode")])
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val priceCode: String,
    val condition: String,
    val threshold: Long,
    val isActive: Boolean,
    val lastTriggeredAt: Long?,
    val note: String?
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val targetMonth: String?,
    val priority: Int,
    val iconName: String,
    val linkedAssetId: Long?,
    val isActive: Boolean,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goal_contributions", indices = [Index("goalId")])
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amount: Long,
    val date: Long,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "planned_expenses", indices = [Index("dueDate")])
data class PlannedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val dueDate: Long,
    val categoryId: Long?,
    val savedAmount: Long,
    val isYearly: Boolean,
    val isCompleted: Boolean,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "public_holidays")
data class PublicHolidayEntity(
    @PrimaryKey val date: Long,
    val name: String
)

@Entity(tableName = "merchant_rules", indices = [Index(value = ["pattern"], unique = true)])
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val categoryId: Long,
    val hitCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dismissed_suggestions")
data class DismissedSuggestionEntity(
    @PrimaryKey val key: String,
    val dismissedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_drafts")
data class ExpenseDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val date: Long,
    val sourceLabel: String,
    val createdAt: Long = System.currentTimeMillis()
)
