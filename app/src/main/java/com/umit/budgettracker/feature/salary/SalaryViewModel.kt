package com.umit.budgettracker.feature.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val repository: SalaryRepository
) : ViewModel() {

    val salaryRules: StateFlow<List<SalaryRule>> = repository.observeAllSalaryRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSalaryRule(amount: Long, effectiveMonth: YearMonth, note: String?) {
        viewModelScope.launch {
            repository.upsertSalaryRule(
                SalaryRule(id = 0, amount = amount, effectiveStartMonth = effectiveMonth, note = note)
            )
        }
    }

    fun deleteSalaryRule(rule: SalaryRule) {
        viewModelScope.launch {
            repository.deleteSalaryRule(rule)
        }
    }
}
