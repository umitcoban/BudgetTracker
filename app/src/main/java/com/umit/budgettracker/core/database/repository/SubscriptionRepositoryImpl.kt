package com.umit.budgettracker.core.database.repository

import androidx.room.withTransaction
import com.umit.budgettracker.core.database.AppDatabase
import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.dao.SubscriptionDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class SubscriptionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: PaymentAccountDao
) : SubscriptionRepository {

    override fun observeActiveSubscriptions(): Flow<List<Subscription>> {
        return subscriptionDao.getAll().map { entities ->
            entities.filter { it.isActive }.map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                val account = accountDao.getById(entity.paymentAccountId)?.toDomain()
                entity.toDomain(category, account)
            }
        }
    }

    override fun observeAllSubscriptions(): Flow<List<Subscription>> {
        return subscriptionDao.getAll().map { entities ->
            entities.map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                val account = accountDao.getById(entity.paymentAccountId)?.toDomain()
                entity.toDomain(category, account)
            }
        }
    }

    override fun observeSubscriptionById(id: Long): Flow<Subscription?> {
        return subscriptionDao.getAll().map { entities ->
            entities.find { it.id == id }?.let { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                val account = accountDao.getById(entity.paymentAccountId)?.toDomain()
                entity.toDomain(category, account)
            }
        }
    }

    override fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistory>> {
        return subscriptionDao.observePriceHistory(subscriptionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllPriceHistory(): Flow<List<SubscriptionPriceHistory>> {
        return subscriptionDao.getAllPriceHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun upsertSubscription(subscription: Subscription) {
        subscriptionDao.insert(subscription.toEntity())
    }

    override suspend fun backfillMissingPriceHistoryCurrency(subscription: Subscription) {
        subscriptionDao.backfillMissingPriceHistoryCurrency(
            subscriptionId = subscription.id,
            currency = subscription.originalCurrency ?: "TRY",
            exchangeRateToTry = subscription.exchangeRateToTry,
            exchangeRateScale = subscription.exchangeRateScale,
            exchangeRateSource = subscription.exchangeRateSource,
            exchangeRateUpdatedAt = subscription.exchangeRateUpdatedAt
        )
    }

    override suspend fun addPriceHistory(history: SubscriptionPriceHistory) {
        subscriptionDao.insertPriceHistory(history.toEntity())
    }

    override suspend fun createSubscriptionWithPrice(subscription: Subscription, initialAmount: Long, startMonth: YearMonth) {
        database.withTransaction {
            val id = subscriptionDao.insert(subscription.toEntity())
            subscriptionDao.insertPriceHistory(
                SubscriptionPriceHistory(
                    id = 0,
                    subscriptionId = id,
                    amount = initialAmount,
                    effectiveFromMonth = startMonth,
                    originalCurrency = subscription.originalCurrency ?: "TRY",
                    exchangeRateToTry = subscription.exchangeRateToTry,
                    exchangeRateScale = subscription.exchangeRateScale,
                    exchangeRateSource = subscription.exchangeRateSource,
                    exchangeRateUpdatedAt = subscription.exchangeRateUpdatedAt
                ).toEntity()
            )
        }
    }

    override suspend fun deactivateSubscription(id: Long) {
        database.withTransaction {
            val entities = subscriptionDao.getAll().firstOrNull() ?: emptyList()
            entities.find { it.id == id }?.let { entity ->
                subscriptionDao.insert(entity.copy(isActive = false))
            }
        }
    }

    override suspend fun deleteSubscription(id: Long) {
        database.withTransaction {
            val entities = subscriptionDao.getAll().firstOrNull() ?: emptyList()
            entities.find { it.id == id }?.let { entity ->
                subscriptionDao.deletePriceHistory(id)
                subscriptionDao.delete(entity)
            }
        }
    }
}
