package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.dao.FixedExpenseDao
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FixedExpenseRepositoryImpl @Inject constructor(
    private val dao: FixedExpenseDao,
    private val categoryDao: CategoryDao,
    private val accountDao: PaymentAccountDao
) : FixedExpenseRepository {
    override fun observeAllFixedExpenses(): Flow<List<FixedExpense>> {
        return dao.getAll().map { entities ->
            entities.map { entity ->
                entity.toDomain(
                    category = entity.categoryId?.let { categoryDao.getById(it)?.toDomain() },
                    account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                )
            }
        }
    }

    override fun observeActiveFixedExpenses(): Flow<List<FixedExpense>> {
        return dao.getActive().map { entities ->
            entities.map { entity ->
                entity.toDomain(
                    category = entity.categoryId?.let { categoryDao.getById(it)?.toDomain() },
                    account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                )
            }
        }
    }

    override suspend fun upsertFixedExpense(expense: FixedExpense) {
        dao.insert(expense.toEntity())
    }

    override suspend fun deleteFixedExpense(expense: FixedExpense) {
        dao.delete(expense.toEntity())
    }
}
