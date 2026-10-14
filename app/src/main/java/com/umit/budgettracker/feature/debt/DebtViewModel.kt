package com.umit.budgettracker.feature.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val repository: DebtRepository
) : ViewModel() {

    val debts: StateFlow<List<DebtRecord>> = repository.observeAllDebtRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(record: DebtRecord) {
        viewModelScope.launch { repository.upsertDebtRecord(record) }
    }

    /** Closing and reopening both go through upsert so a mistaken tap can be undone. */
    fun setPaid(record: DebtRecord, paid: Boolean) {
        viewModelScope.launch { repository.upsertDebtRecord(record.copy(isPaid = paid)) }
    }

    fun delete(record: DebtRecord) {
        viewModelScope.launch { repository.deleteDebtRecord(record.id) }
    }
}
