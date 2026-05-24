package com.umit.budgettracker.feature.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.DebtType
import com.umit.budgettracker.core.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val repository: DebtRepository
) : ViewModel() {

    val debts: StateFlow<List<DebtRecord>> = repository.observeAllDebtRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addDebt(title: String, person: String?, amount: Long, type: DebtType, dueDate: LocalDate?) {
        viewModelScope.launch {
            repository.upsertDebtRecord(
                DebtRecord(
                    id = 0,
                    title = title,
                    personName = person,
                    amount = amount,
                    type = type,
                    dueDate = dueDate,
                    isPaid = false,
                    note = null
                )
            )
        }
    }

    fun markAsPaid(id: Long) {
        viewModelScope.launch {
            repository.markAsPaid(id)
        }
    }
}
