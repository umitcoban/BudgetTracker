package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.SalaryRuleDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.calculator.SalaryRules
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
            SalaryRules.effectiveForMonth(
                rules = entities.map { it.toDomain() },
                month = yearMonth
            )
        }
    }

    override suspend fun upsertSalaryRule(rule: SalaryRule) {
        val existingForMonth = dao.getByEffectiveStartMonth(rule.effectiveStartMonth.toString())
        val entity = rule.toEntity()
        dao.insert(
            if (existingForMonth != null && existingForMonth.id != rule.id) {
                entity.copy(id = existingForMonth.id)
            } else {
                entity
            }
        )
    }

    override suspend fun deleteSalaryRule(rule: SalaryRule) {
        dao.delete(rule.toEntity())
    }
}
