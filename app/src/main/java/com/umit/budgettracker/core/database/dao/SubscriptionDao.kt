package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.SubscriptionEntity
import com.umit.budgettracker.core.database.entity.SubscriptionPriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions")
    fun getAll(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: SubscriptionPriceHistoryEntity)

    @Query("SELECT * FROM subscription_price_history WHERE subscriptionId = :subscriptionId ORDER BY effectiveFromMonth DESC")
    fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistoryEntity>>

    @Query("SELECT * FROM subscription_price_history WHERE subscriptionId = :subscriptionId ORDER BY effectiveFromMonth DESC")
    suspend fun getPriceHistory(subscriptionId: Long): List<SubscriptionPriceHistoryEntity>

    @Query("SELECT * FROM subscription_price_history")
    fun getAllPriceHistory(): Flow<List<SubscriptionPriceHistoryEntity>>

    @Query(
        """
        UPDATE subscription_price_history
        SET originalCurrency = :currency,
            exchangeRateToTry = :exchangeRateToTry,
            exchangeRateScale = :exchangeRateScale,
            exchangeRateSource = :exchangeRateSource,
            exchangeRateUpdatedAt = :exchangeRateUpdatedAt
        WHERE subscriptionId = :subscriptionId AND originalCurrency IS NULL
        """
    )
    suspend fun backfillMissingPriceHistoryCurrency(
        subscriptionId: Long,
        currency: String,
        exchangeRateToTry: Long?,
        exchangeRateScale: Int?,
        exchangeRateSource: String?,
        exchangeRateUpdatedAt: Long?
    )

    @Query("DELETE FROM subscription_price_history WHERE subscriptionId = :subscriptionId")
    suspend fun deletePriceHistory(subscriptionId: Long)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)
}
