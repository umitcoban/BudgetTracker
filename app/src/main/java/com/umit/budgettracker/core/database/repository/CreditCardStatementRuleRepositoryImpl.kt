package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CreditCardStatementRuleDao
import com.umit.budgettracker.core.database.entity.CreditCardStatementRuleEntity
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.repository.CreditCardStatementRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class CreditCardStatementRuleRepositoryImpl @Inject constructor(
    private val dao: CreditCardStatementRuleDao
) : CreditCardStatementRuleRepository {
    override fun observeAllRules(): Flow<List<CreditCardStatementRule>> = dao.getAll().map { rules ->
        rules.map { it.toDomain() }
    }

    override suspend fun saveRule(rule: CreditCardStatementRule) {
        dao.insert(rule.toEntity())
    }
}

private fun CreditCardStatementRuleEntity.toDomain() = CreditCardStatementRule(id, accountId, YearMonth.parse(effectiveFromMonth), statementDay, dueDay)
private fun CreditCardStatementRule.toEntity() = CreditCardStatementRuleEntity(id, accountId, effectiveFromMonth.toString(), statementDay, dueDay)
