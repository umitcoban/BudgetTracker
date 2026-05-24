package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {
    override fun observeActiveCategories(): Flow<List<Category>> {
        return dao.getAllActive().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeAllCategories(): Flow<List<Category>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun upsertCategory(category: Category) {
        dao.insert(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        dao.delete(category.toEntity())
    }
}
