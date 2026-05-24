package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeActiveCategories(): Flow<List<Category>>
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun upsertCategory(category: Category)
    suspend fun deleteCategory(category: Category)
}
