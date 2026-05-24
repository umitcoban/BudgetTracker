package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CategoryBudgetDao
import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.CategoryBudget
import com.umit.budgettracker.core.domain.repository.CategoryBudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class CategoryBudgetRepositoryImpl @Inject constructor(
    private val budgetDao: CategoryBudgetDao,
    private val categoryDao: CategoryDao
) : CategoryBudgetRepository {
    override fun observeBudgetsForMonth(yearMonth: YearMonth): Flow<List<CategoryBudget>> {
        return budgetDao.getByMonth(yearMonth.toString()).map { entities ->
            entities.deduplicateByCategoryAndMonth().map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                entity.toDomain(category)
            }
        }
    }

    override fun observeAllBudgets(): Flow<List<CategoryBudget>> {
        return budgetDao.getAll().map { entities ->
            entities.deduplicateByCategoryAndMonth().map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                entity.toDomain(category)
            }
        }
    }

    override fun observeBudgetForCategoryAndMonth(categoryId: Long, yearMonth: YearMonth): Flow<CategoryBudget?> {
        return budgetDao.getByMonth(yearMonth.toString()).map { entities ->
            entities.find { it.categoryId == categoryId }?.let { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                entity.toDomain(category)
            }
        }
    }

    override suspend fun upsertCategoryBudget(budget: CategoryBudget) {
        val existingBudgetsForCategoryAndMonth = budgetDao.getAllByCategoryAndMonth(
            categoryId = budget.categoryId,
            yearMonth = budget.yearMonth.toString()
        )
        val existingForCategoryAndMonth = existingBudgetsForCategoryAndMonth.firstOrNull()
        val targetId = existingForCategoryAndMonth?.id ?: budget.id

        if (
            budget.id != 0L &&
            existingForCategoryAndMonth != null &&
            existingForCategoryAndMonth.id != budget.id
        ) {
            budgetDao.deleteById(budget.id)
        }

        existingBudgetsForCategoryAndMonth
            .filter { it.id != targetId }
            .forEach { budgetDao.deleteById(it.id) }

        budgetDao.insert(budget.toEntity().copy(id = targetId))
    }

    override suspend fun deleteCategoryBudget(budget: CategoryBudget) {
        budgetDao.delete(budget.toEntity())
    }
}

private fun List<com.umit.budgettracker.core.database.entity.CategoryBudgetEntity>.deduplicateByCategoryAndMonth() =
    sortedWith(
        compareBy<com.umit.budgettracker.core.database.entity.CategoryBudgetEntity> { it.yearMonth }
            .thenBy { it.categoryId }
            .thenByDescending { it.id }
    ).distinctBy { it.yearMonth to it.categoryId }
