package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.SalaryRuleDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class SalaryRepositoryImpl @Inject constructor(
    private val dao: SalaryRuleDao
) : SalaryRepository {
    override fun observeAllSalaryRules(): Flow<List<SalaryRule>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeSalaryForMonth(yearMonth: YearMonth): Flow<SalaryRule?> {
        return dao.getAll().map { entities ->
            entities
                .filter { YearMonth.parse(it.effectiveStartMonth) <= yearMonth }
                .maxByOrNull { it.effectiveStartMonth }
                ?.toDomain()
        }
    }

    override suspend fun upsertSalaryRule(rule: SalaryRule) {
        dao.insert(rule.toEntity())
    }

    override suspend fun deleteSalaryRule(rule: SalaryRule) {
        dao.delete(rule.toEntity())
    }
}
