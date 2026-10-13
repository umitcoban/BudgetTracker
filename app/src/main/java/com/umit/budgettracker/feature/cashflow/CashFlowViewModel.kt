package com.umit.budgettracker.feature.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashForecast
import com.umit.budgettracker.core.domain.usecase.CashForecastUseCase
import com.umit.budgettracker.core.domain.calculator.CashFlowProjector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashFlowViewModel @Inject constructor(
    private val projector: CashFlowProjector,
    cashForecast: CashForecastUseCase
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val events: StateFlow<List<CashFlowEvent>> = _selectedMonth
        .flatMapLatest { month -> projector.observeMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Next ~45 days from today; null until the user records a cash / bank balance under Varlıklar. */
    val forecast: StateFlow<CashForecast?> = cashForecast.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
}
