package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import kotlinx.coroutines.flow.Flow

interface CreditCardStatementRuleRepository {
    fun observeAllRules(): Flow<List<CreditCardStatementRule>>
    suspend fun saveRule(rule: CreditCardStatementRule)
}
