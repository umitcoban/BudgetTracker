package com.umit.budgettracker.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.ExpenseTemplate
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseTemplateRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseTemplatesViewModel @Inject constructor(
    private val repository: ExpenseTemplateRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository
) : ViewModel() {

    val templates: StateFlow<List<ExpenseTemplate>> = repository.observeActiveTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTemplate(title: String, amount: Long?, categoryId: Long, accountId: Long?) {
        viewModelScope.launch {
            repository.upsertTemplate(
                ExpenseTemplate(
                    id = 0,
                    title = title,
                    defaultAmount = amount,
                    categoryId = categoryId,
                    paymentAccountId = accountId,
                    note = null,
                    isActive = true
                )
            )
        }
    }
}
