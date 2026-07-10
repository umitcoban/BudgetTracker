package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CategoryDao
import com.umit.budgettracker.core.database.dao.ExpenseDao
import com.umit.budgettracker.core.database.dao.LoanDao
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.repository.LoanDeletionResult
import com.umit.budgettracker.core.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class LoanRepositoryImpl @Inject constructor(
    private val loanDao: LoanDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val accountDao: PaymentAccountDao
) : LoanRepository {

    override fun observeActiveLoans(): Flow<List<Loan>> {
        return loanDao.getAll().map { entities ->
            entities.filter { it.isActive }.map { entity ->
                val category = entity.categoryId?.let { categoryDao.getById(it)?.toDomain() }
                val account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                entity.toDomain(category, account)
            }
        }
    }

    override fun observeAllLoans(): Flow<List<Loan>> {
        return loanDao.getAll().map { entities ->
            entities.map { entity ->
                val category = entity.categoryId?.let { categoryDao.getById(it)?.toDomain() }
                val account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                entity.toDomain(category, account)
            }
        }
    }

    override fun observeLoanById(id: Long): Flow<Loan?> {
        return loanDao.getAll().map { entities ->
            entities.find { it.id == id }?.let { entity ->
                val category = entity.categoryId?.let { categoryDao.getById(it)?.toDomain() }
                val account = entity.paymentAccountId?.let { accountDao.getById(it)?.toDomain() }
                entity.toDomain(category, account)
            }
        }
    }

    override suspend fun upsertLoan(loan: Loan) {
        loanDao.insert(loan.toEntity())
    }

    override suspend fun closeLoanEarly(id: Long, closedAt: LocalDate) {
        loanDao.closeEarly(
            id = id,
            closedAt = closedAt.toEpochDay(),
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteLoan(id: Long): LoanDeletionResult {
        val loan = loanDao.getById(id) ?: return LoanDeletionResult.NotFound
        if (expenseDao.countByLoanId(id) > 0) return LoanDeletionResult.HasLinkedExpenses

        loanDao.delete(loan)
        return LoanDeletionResult.Deleted
    }
}
