package com.umit.budgettracker.feature.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.InstallmentProgress
import com.umit.budgettracker.core.domain.calculator.InstallmentRules
import com.umit.budgettracker.core.domain.repository.InstallmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class InstallmentsViewModel @Inject constructor(
    private val repository: InstallmentRepository
) : ViewModel() {

    private val _showCompleted = MutableStateFlow(false)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    val uiState: StateFlow<InstallmentsUiState> = combine(
        repository.observeInstallmentGroups(),
        _showCompleted
    ) { groups, showCompleted ->
        val today = LocalDate.now()
        val progress = groups.map { InstallmentRules.progress(it, today) }
        val active = progress.filter { !it.isCompleted }.sortedWith(compareBy({ it.nextInstallmentDate }, { it.group.title }))
        val completed = progress.filter { it.isCompleted }.sortedByDescending { it.group.startDate }
        InstallmentsUiState(
            active = active,
            completed = if (showCompleted) completed else emptyList(),
            completedCount = completed.size,
            activeRemainingAmount = active.sumOf { it.remainingAmount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstallmentsUiState())

    fun toggleShowCompleted() {
        _showCompleted.value = !_showCompleted.value
    }

    fun deleteInstallmentGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteInstallmentGroupWithGeneratedExpenses(groupId)
        }
    }
}

data class InstallmentsUiState(
    val active: List<InstallmentProgress> = emptyList(),
    val completed: List<InstallmentProgress> = emptyList(),
    val completedCount: Int = 0,
    val activeRemainingAmount: Long = 0L
)
