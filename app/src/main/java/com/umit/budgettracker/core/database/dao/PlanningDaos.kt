package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.umit.budgettracker.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InflationRateDao {
    @Query("SELECT * FROM inflation_rates ORDER BY month DESC")
    fun observeAll(): Flow<List<InflationRateEntity>>

    @Query("SELECT * FROM inflation_rates ORDER BY month DESC")
    suspend fun getAll(): List<InflationRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: InflationRateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<InflationRateEntity>)

    /** Seed path: never overwrite a month the user already entered. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfMissing(rates: List<InflationRateEntity>)

    @Query("DELETE FROM inflation_rates WHERE month = :month")
    suspend fun delete(month: String)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY isActive DESC, type, name")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets ORDER BY isActive DESC, type, name")
    suspend fun getAll(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<AssetEntity>)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM market_prices ORDER BY code")
    fun observePrices(): Flow<List<MarketPriceEntity>>

    @Query("SELECT * FROM market_prices ORDER BY code")
    suspend fun getPrices(): List<MarketPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<MarketPriceEntity>)

    @Query("SELECT * FROM price_alerts ORDER BY priceCode")
    fun observeAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts ORDER BY priceCode")
    suspend fun getAlerts(): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlert(alert: PriceAlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<PriceAlertEntity>)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deleteAlert(id: Long)
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY isActive DESC, priority, name")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY isActive DESC, priority, name")
    suspend fun getGoals(): List<SavingsGoalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<SavingsGoalEntity>)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT COUNT(*) FROM savings_goals WHERE linkedAssetId = :assetId")
    suspend fun countLinkedToAsset(assetId: Long): Int

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC, id DESC")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY date DESC, id DESC")
    suspend fun getContributions(): List<GoalContributionEntity>

    @Query("SELECT COUNT(*) FROM goal_contributions WHERE goalId = :goalId")
    suspend fun countContributions(goalId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: GoalContributionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(contributions: List<GoalContributionEntity>)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)
}

@Dao
interface PlannedExpenseDao {
    @Query("SELECT * FROM planned_expenses ORDER BY isCompleted, dueDate")
    fun observeAll(): Flow<List<PlannedExpenseEntity>>

    @Query("SELECT * FROM planned_expenses ORDER BY isCompleted, dueDate")
    suspend fun getAll(): List<PlannedExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlannedExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlannedExpenseEntity>)

    @Query("DELETE FROM planned_expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PublicHolidayDao {
    @Query("SELECT * FROM public_holidays ORDER BY date")
    fun observeAll(): Flow<List<PublicHolidayEntity>>

    @Query("SELECT * FROM public_holidays ORDER BY date")
    suspend fun getAll(): List<PublicHolidayEntity>

    @Query("DELETE FROM public_holidays WHERE date BETWEEN :startDay AND :endDay")
    suspend fun deleteRange(startDay: Long, endDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<PublicHolidayEntity>)

    @Transaction
    suspend fun replaceRange(startDay: Long, endDay: Long, holidays: List<PublicHolidayEntity>) {
        deleteRange(startDay, endDay)
        insertAll(holidays)
    }
}

@Dao
interface AssistantDao {
    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, pattern")
    fun observeMerchantRules(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY hitCount DESC, pattern")
    suspend fun getMerchantRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE pattern = :pattern LIMIT 1")
    suspend fun findMerchantRule(pattern: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchantRules(rules: List<MerchantRuleEntity>)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun deleteMerchantRule(id: Long)

    @Query("SELECT * FROM dismissed_suggestions")
    fun observeDismissed(): Flow<List<DismissedSuggestionEntity>>

    @Query("SELECT * FROM dismissed_suggestions")
    suspend fun getDismissed(): List<DismissedSuggestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissed(items: List<DismissedSuggestionEntity>)

    @Query("DELETE FROM dismissed_suggestions")
    suspend fun clearDismissed()

    @Query("SELECT * FROM expense_drafts ORDER BY date DESC, id DESC")
    fun observeDrafts(): Flow<List<ExpenseDraftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: ExpenseDraftEntity)

    @Query("DELETE FROM expense_drafts WHERE id = :id")
    suspend fun deleteDraft(id: Long)
}
