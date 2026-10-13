package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.ExpenseDraft
import com.umit.budgettracker.core.domain.model.MerchantRule
import kotlinx.coroutines.flow.Flow

/** Learned merchant→category rules, dismissed suggestions and notification drafts. */
interface AssistantRepository {
    fun observeMerchantRules(): Flow<List<MerchantRule>>
    suspend fun getMerchantRules(): List<MerchantRule>

    /** Remembers that [merchantText] belongs to [categoryId] (the latest choice wins). */
    suspend fun learnMerchant(merchantText: String, categoryId: Long)
    suspend fun deleteMerchantRule(id: Long)

    fun observeDismissedSuggestionKeys(): Flow<Set<String>>
    suspend fun dismissSuggestion(key: String)
    suspend fun restoreSuggestions()

    fun observeDrafts(): Flow<List<ExpenseDraft>>
    suspend fun addDraft(draft: ExpenseDraft)
    suspend fun deleteDraft(id: Long)
}
