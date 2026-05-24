package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.ExpenseTemplate
import kotlinx.coroutines.flow.Flow

interface ExpenseTemplateRepository {
    fun observeActiveTemplates(): Flow<List<ExpenseTemplate>>
    suspend fun upsertTemplate(template: ExpenseTemplate)
    suspend fun deleteTemplate(id: Long)
}
