package com.umit.budgettracker.feature.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.domain.repository.NetWorthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val repository: NetWorthRepository
) : ViewModel() {

    val snapshots: StateFlow<List<NetWorthSnapshot>> = repository.observeSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSnapshot(month: YearMonth, cash: Long, bank: Long, invest: Long, card: Long, loan: Long) {
        viewModelScope.launch {
            repository.upsertSnapshot(
                NetWorthSnapshot(
                    id = 0,
                    yearMonth = month,
                    cashAmount = cash,
                    bankAmount = bank,
                    investmentAmount = invest,
                    creditCardDebt = card,
                    loanDebt = loan,
                    note = null
                )
            )
        }
    }
}
