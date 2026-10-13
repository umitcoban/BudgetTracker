package com.umit.budgettracker.core.database.repository

import androidx.room.withTransaction
import com.umit.budgettracker.core.database.AppDatabase
import com.umit.budgettracker.core.database.dao.AssetDao
import com.umit.budgettracker.core.database.dao.AssistantDao
import com.umit.budgettracker.core.database.dao.InflationRateDao
import com.umit.budgettracker.core.database.dao.PlannedExpenseDao
import com.umit.budgettracker.core.database.dao.PublicHolidayDao
import com.umit.budgettracker.core.database.dao.SavingsGoalDao
import com.umit.budgettracker.core.database.entity.DismissedSuggestionEntity
import com.umit.budgettracker.core.database.entity.MerchantRuleEntity
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.util.TextMatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class InflationRepositoryImpl @Inject constructor(
    private val dao: InflationRateDao
) : InflationRepository {
    override fun observeAll(): Flow<List<InflationRate>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getAll(): List<InflationRate> = dao.getAll().map { it.toDomain() }
    override suspend fun upsert(rate: InflationRate) = dao.upsert(rate.toEntity())
    override suspend fun delete(month: YearMonth) = dao.delete(month.toString())
}

class AssetRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: AssetDao,
    private val goalDao: SavingsGoalDao
) : AssetRepository {
    override fun observeAssets(): Flow<List<Asset>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getAssets(): List<Asset> = dao.getAll().map { it.toDomain() }

    override suspend fun upsertAsset(asset: Asset): Long {
        return if (asset.id == 0L) {
            dao.insert(asset.toEntity())
        } else {
            dao.update(asset.toEntity().copy(updatedAt = System.currentTimeMillis()))
            asset.id
        }
    }

    override suspend fun deleteAsset(id: Long): AssetDeletionResult {
        if (goalDao.countLinkedToAsset(id) > 0) return AssetDeletionResult.LinkedToGoal
        dao.delete(id)
        return AssetDeletionResult.Deleted
    }

    override fun observePrices(): Flow<List<MarketPrice>> = dao.observePrices().map { list -> list.map { it.toDomain() } }
    override suspend fun getPrices(): List<MarketPrice> = dao.getPrices().map { it.toDomain() }

    override suspend fun savePrices(prices: List<MarketPrice>) {
        if (prices.isEmpty()) return
        db.withTransaction {
            val existing = dao.getPrices().associateBy { it.code }
            dao.insertPrices(prices.map { price ->
                // Change is only meaningful against the same source (TCMB vs Truncgil quotes differ).
                val old = existing[price.code]?.takeIf { it.source == price.source }
                price.toEntity().copy(
                    previousPrice = price.previousPrice ?: old?.price?.takeIf { it != price.price } ?: old?.previousPrice
                )
            })
        }
    }

    override fun observeAlerts(): Flow<List<PriceAlert>> = dao.observeAlerts().map { list -> list.map { it.toDomain() } }
    override suspend fun getAlerts(): List<PriceAlert> = dao.getAlerts().map { it.toDomain() }
    override suspend fun upsertAlert(alert: PriceAlert) = dao.upsertAlert(alert.toEntity())
    override suspend fun deleteAlert(id: Long) = dao.deleteAlert(id)
}

class SavingsGoalRepositoryImpl @Inject constructor(
    private val dao: SavingsGoalDao
) : SavingsGoalRepository {
    override fun observeGoals(): Flow<List<SavingsGoal>> = dao.observeGoals().map { list -> list.map { it.toDomain() } }
    override fun observeContributions(): Flow<List<GoalContribution>> =
        dao.observeContributions().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertGoal(goal: SavingsGoal): Long {
        return if (goal.id == 0L) {
            dao.insertGoal(goal.toEntity())
        } else {
            dao.updateGoal(goal.toEntity().copy(updatedAt = System.currentTimeMillis()))
            goal.id
        }
    }

    override suspend fun deleteGoal(id: Long): Boolean {
        if (dao.countContributions(id) > 0) return false
        dao.deleteGoal(id)
        return true
    }

    override suspend fun addContribution(contribution: GoalContribution) = dao.insertContribution(contribution.toEntity())
    override suspend fun deleteContribution(id: Long) = dao.deleteContribution(id)
}

class PlannedExpenseRepositoryImpl @Inject constructor(
    private val dao: PlannedExpenseDao
) : PlannedExpenseRepository {
    override fun observeAll(): Flow<List<PlannedExpense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(item: PlannedExpense) = dao.upsert(item.toEntity().copy(updatedAt = System.currentTimeMillis()))
    override suspend fun delete(id: Long) = dao.delete(id)
}

class HolidayRepositoryImpl @Inject constructor(
    private val dao: PublicHolidayDao
) : HolidayRepository {
    override fun observeAll(): Flow<List<PublicHoliday>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getAll(): List<PublicHoliday> = dao.getAll().map { it.toDomain() }

    override suspend fun replaceYear(year: Int, holidays: List<PublicHoliday>) {
        if (holidays.isEmpty()) return
        dao.replaceRange(
            startDay = LocalDate.of(year, 1, 1).toEpochDay(),
            endDay = LocalDate.of(year, 12, 31).toEpochDay(),
            holidays = holidays.filter { it.date.year == year }.map { it.toEntity() }
        )
    }
}

class AssistantRepositoryImpl @Inject constructor(
    private val dao: AssistantDao
) : AssistantRepository {
    override fun observeMerchantRules(): Flow<List<MerchantRule>> =
        dao.observeMerchantRules().map { list -> list.map { it.toDomain() } }

    override suspend fun getMerchantRules(): List<MerchantRule> = dao.getMerchantRules().map { it.toDomain() }

    override suspend fun learnMerchant(merchantText: String, categoryId: Long) {
        val pattern = TextMatching.key(merchantText)
        if (pattern.isBlank()) return
        val existing = dao.findMerchantRule(pattern)
        dao.upsertMerchantRule(
            MerchantRuleEntity(
                id = existing?.id ?: 0L,
                pattern = pattern,
                categoryId = categoryId,
                hitCount = (existing?.hitCount ?: 0) + 1
            )
        )
    }

    override suspend fun deleteMerchantRule(id: Long) = dao.deleteMerchantRule(id)

    override fun observeDismissedSuggestionKeys(): Flow<Set<String>> =
        dao.observeDismissed().map { list -> list.map { it.key }.toSet() }

    override suspend fun dismissSuggestion(key: String) = dao.insertDismissed(listOf(DismissedSuggestionEntity(key = key)))
    override suspend fun restoreSuggestions() = dao.clearDismissed()

    override fun observeDrafts(): Flow<List<ExpenseDraft>> = dao.observeDrafts().map { list -> list.map { it.toDomain() } }
    override suspend fun addDraft(draft: ExpenseDraft) = dao.insertDraft(draft.toEntity())
    override suspend fun deleteDraft(id: Long) = dao.deleteDraft(id)
}
