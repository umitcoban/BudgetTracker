package com.umit.budgettracker.feature.installments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.InstallmentGroup
import com.umit.budgettracker.core.domain.repository.InstallmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallmentsViewModel @Inject constructor(
    private val repository: InstallmentRepository
) : ViewModel() {

    val installmentGroups: StateFlow<List<InstallmentGroup>> = repository.observeInstallmentGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteInstallmentGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteInstallmentGroupWithGeneratedExpenses(groupId)
        }
    }
}
