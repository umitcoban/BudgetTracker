package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface SubscriptionRepository {
    fun observeActiveSubscriptions(): Flow<List<Subscription>>
    fun observeAllSubscriptions(): Flow<List<Subscription>>
    fun observeSubscriptionById(id: Long): Flow<Subscription?>
    fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistory>>
    fun observeAllPriceHistory(): Flow<List<SubscriptionPriceHistory>>
    suspend fun upsertSubscription(subscription: Subscription)
    suspend fun addPriceHistory(history: SubscriptionPriceHistory)
    suspend fun createSubscriptionWithPrice(subscription: Subscription, initialAmount: Long, startMonth: YearMonth)
    suspend fun deactivateSubscription(id: Long)
    suspend fun deleteSubscription(id: Long)
}
