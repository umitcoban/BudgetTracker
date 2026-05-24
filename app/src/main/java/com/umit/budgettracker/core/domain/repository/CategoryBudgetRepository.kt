package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.CategoryBudget
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface CategoryBudgetRepository {
    fun observeAllBudgets(): Flow<List<CategoryBudget>>
    fun observeBudgetsForMonth(yearMonth: YearMonth): Flow<List<CategoryBudget>>
    fun observeBudgetForCategoryAndMonth(categoryId: Long, yearMonth: YearMonth): Flow<CategoryBudget?>
    suspend fun upsertCategoryBudget(budget: CategoryBudget)
    suspend fun deleteCategoryBudget(budget: CategoryBudget)
}
