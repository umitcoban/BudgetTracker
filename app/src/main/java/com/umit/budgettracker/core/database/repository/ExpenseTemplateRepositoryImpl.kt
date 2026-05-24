package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.dao.ExpenseTemplateDao
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.ExpenseTemplate
import com.umit.budgettracker.core.domain.repository.ExpenseTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseTemplateRepositoryImpl @Inject constructor(
    private val templateDao: ExpenseTemplateDao,
    private val categoryDao: CategoryDao,
    private val accountDao: PaymentAccountDao
) : ExpenseTemplateRepository {
    override fun observeActiveTemplates(): Flow<List<ExpenseTemplate>> {
        return templateDao.getAllActive().map { entities ->
            entities.map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                val account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                entity.toDomain(category, account)
            }
        }
    }

    override suspend fun upsertTemplate(template: ExpenseTemplate) {
        templateDao.insert(template.toEntity())
    }

    override suspend fun deleteTemplate(id: Long) {
        templateDao.getById(id)?.let {
            templateDao.delete(it)
        }
    }
}
