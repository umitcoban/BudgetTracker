package com.umit.budgettracker.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: PaymentAccountRepository,
    private val dao: PaymentAccountDao // Using DAO for simple CRUD to save time on repository expansion
) : ViewModel() {

    val accounts: StateFlow<List<PaymentAccount>> = repository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCreditCard(name: String, statementDay: Int, dueDay: Int) {
        viewModelScope.launch {
            dao.insert(
                PaymentAccount(
                    id = 0,
                    name = name,
                    type = AccountType.CREDIT_CARD,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    isActive = true
                ).toEntity()
            )
        }
    }

    fun toggleAccountActive(account: PaymentAccount) {
        viewModelScope.launch {
            dao.update(account.copy(isActive = !account.isActive).toEntity())
        }
    }
}
