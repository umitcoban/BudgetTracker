package com.umit.budgettracker.core.database.repository

import androidx.room.withTransaction
import com.umit.budgettracker.core.database.AppDatabase
import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.dao.ExpenseDao
import com.umit.budgettracker.core.database.dao.InstallmentGroupDao
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.InstallmentGroup
import com.umit.budgettracker.core.domain.repository.InstallmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InstallmentRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val groupDao: InstallmentGroupDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val accountDao: PaymentAccountDao
) : InstallmentRepository {

    override fun observeInstallmentGroups(): Flow<List<InstallmentGroup>> {
        return groupDao.getAll().map { entities ->
            entities.map { entity ->
                val category = categoryDao.getById(entity.categoryId)?.toDomain()
                val account = accountDao.getById(entity.paymentAccountId)?.toDomain()
                entity.toDomain(category, account)
            }
        }
    }

    override suspend fun getInstallmentGroupById(id: Long): InstallmentGroup? {
        return groupDao.getById(id)?.let { entity ->
            val category = categoryDao.getById(entity.categoryId)?.toDomain()
            val account = accountDao.getById(entity.paymentAccountId)?.toDomain()
            entity.toDomain(category, account)
        }
    }

    override suspend fun insertInstallmentGroup(group: InstallmentGroup, expenses: List<Expense>): Long {
        return database.withTransaction {
            val groupId = groupDao.insert(group.toEntity())
            val expenseEntities = expenses.map { 
                it.copy(installmentGroupId = groupId).toEntity() 
            }
            expenseDao.insertAll(expenseEntities)
            groupId
        }
    }

    override suspend fun deleteInstallmentGroupWithGeneratedExpenses(groupId: Long) {
        database.withTransaction {
            val group = groupDao.getById(groupId)
            if (group != null) {
                expenseDao.deleteByInstallmentGroupId(groupId)
                groupDao.delete(group)
            }
        }
    }

    override fun observeExpensesForInstallmentGroup(groupId: Long): Flow<List<Expense>> {
        return expenseDao.getByInstallmentGroupId(groupId).map { entities ->
            entities.map { it.expense.toDomain(it.category?.toDomain(), it.account?.toDomain()) }
        }
    }
}
