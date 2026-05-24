package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.IncomeDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Income
import com.umit.budgettracker.core.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class IncomeRepositoryImpl @Inject constructor(
    private val dao: IncomeDao
) : IncomeRepository {
    override fun observeAllIncomes(): Flow<List<Income>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeIncomesForMonth(yearMonth: YearMonth): Flow<List<Income>> {
        val startDate = yearMonth.atDay(1).toEpochDay()
        val endDate = yearMonth.atEndOfMonth().toEpochDay()
        return dao.getForPeriod(startDate, endDate).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertIncome(income: Income) {
        dao.insert(income.toEntity())
    }

    override suspend fun updateIncome(income: Income) {
        dao.update(income.toEntity())
    }

    override suspend fun deleteIncome(income: Income) {
        dao.delete(income.toEntity())
    }
}
