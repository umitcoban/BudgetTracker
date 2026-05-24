package com.umit.budgettracker.feature.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val repository: LoanRepository
) : ViewModel() {

    val loans: StateFlow<List<Loan>> = repository.observeAllLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLoan(title: String, principal: Long, monthly: Long, count: Int, start: YearMonth, day: Int) {
        viewModelScope.launch {
            repository.upsertLoan(
                Loan(
                    id = 0,
                    title = title,
                    principalAmount = principal,
                    monthlyPaymentAmount = monthly,
                    installmentCount = count,
                    startMonth = start,
                    paymentDay = day,
                    categoryId = null,
                    paymentAccountId = null,
                    note = null,
                    isActive = true
                )
            )
        }
    }
}
