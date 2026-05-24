package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.SalaryRule
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface SalaryRepository {
    fun observeAllSalaryRules(): Flow<List<SalaryRule>>
    fun observeSalaryForMonth(yearMonth: YearMonth): Flow<SalaryRule?>
    suspend fun upsertSalaryRule(rule: SalaryRule)
    suspend fun deleteSalaryRule(rule: SalaryRule)
}
