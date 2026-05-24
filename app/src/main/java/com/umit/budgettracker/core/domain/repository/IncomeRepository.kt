package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Income
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface IncomeRepository {
    fun observeAllIncomes(): Flow<List<Income>>
    fun observeIncomesForMonth(yearMonth: YearMonth): Flow<List<Income>>
    suspend fun insertIncome(income: Income)
    suspend fun updateIncome(income: Income)
    suspend fun deleteIncome(income: Income)
}
