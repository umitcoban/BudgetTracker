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
            entities.map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                entity.toDomain(category)
            }
        }
    }

    override fun observeAllBudgets(): Flow<List<CategoryBudget>> {
        return budgetDao.getAll().map { entities ->
            entities.map { entity ->
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
        budgetDao.insert(budget.toEntity())
    }

    override suspend fun deleteCategoryBudget(budget: CategoryBudget) {
        budgetDao.delete(budget.toEntity())
    }
}
