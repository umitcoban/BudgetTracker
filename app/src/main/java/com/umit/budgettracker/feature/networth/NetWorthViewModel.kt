package com.umit.budgettracker.feature.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.LoanMonthlyCalculator
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.repository.NetWorthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Live values from Varlıklar, used to prefill a new snapshot. Past snapshots are never rewritten. */
data class LiveNetWorth(
    val cash: Long = 0L,
    val bank: Long = 0L,
    val investment: Long = 0L,
    val loanDebt: Long = 0L,
    val hasAssets: Boolean = false
) {
    val total: Long get() = cash + bank + investment - loanDebt
}

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val repository: NetWorthRepository,
    assetRepository: AssetRepository,
    loanCalculator: LoanMonthlyCalculator
) : ViewModel() {

    val snapshots: StateFlow<List<NetWorthSnapshot>> = repository.observeSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val live: StateFlow<LiveNetWorth> = combine(
        assetRepository.observeAssets(),
        assetRepository.observePrices(),
        loanCalculator.getPaymentsForMonth(YearMonth.now())
    ) { assets, prices, loans ->
        val summary = AssetValuationRules.summarize(assets, prices, LocalDate.now())
        LiveNetWorth(
            cash = summary.valuations.filter { it.asset.type == AssetType.CASH }.sumOf { it.value },
            bank = summary.valuations.filter { it.asset.type == AssetType.DEPOSIT }.sumOf { it.value },
            investment = summary.valuations.filter { it.asset.type != AssetType.CASH && it.asset.type != AssetType.DEPOSIT }.sumOf { it.value },
            loanDebt = loans.sumOf { it.amount + it.remainingAmount },
            hasAssets = summary.valuations.isNotEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveNetWorth())

    fun saveSnapshot(snapshot: NetWorthSnapshot) {
        viewModelScope.launch {
            // One snapshot per month: saving the same month again replaces it.
            val existing = snapshots.value.firstOrNull { it.yearMonth == snapshot.yearMonth && it.id != snapshot.id }
            existing?.let { repository.deleteSnapshot(it) }
            repository.upsertSnapshot(snapshot)
        }
    }

    fun deleteSnapshot(snapshot: NetWorthSnapshot) {
        viewModelScope.launch { repository.deleteSnapshot(snapshot) }
    }
}
