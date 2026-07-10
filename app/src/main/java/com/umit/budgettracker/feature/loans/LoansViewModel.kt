package com.umit.budgettracker.feature.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.LoanPaymentCalculator
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.repository.LoanDeletionResult
import com.umit.budgettracker.core.domain.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val repository: LoanRepository
) : ViewModel() {

    val loans: StateFlow<List<Loan>> = repository.observeAllLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun saveLoan(loan: Loan) {
        viewModelScope.launch {
            val monthlyPayment = LoanPaymentCalculator.calculateMonthlyPayment(
                principalAmount = loan.principalAmount,
                installmentCount = loan.installmentCount
            ) ?: run {
                _message.value = "Taksit tutarı geçerli değil."
                return@launch
            }

            repository.upsertLoan(loan.copy(monthlyPaymentAmount = monthlyPayment))
            _message.value = if (loan.id == 0L) "Kredi eklendi." else "Kredi güncellendi."
        }
    }

    fun closeLoanEarly(loan: Loan) {
        viewModelScope.launch {
            repository.closeLoanEarly(loan.id, LocalDate.now())
            _message.value = "Kredi erken kapatıldı. Gelecek taksitler planlamadan çıkarıldı."
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            _message.value = when (repository.deleteLoan(loan.id)) {
                LoanDeletionResult.Deleted -> "Kredi silindi."
                LoanDeletionResult.HasLinkedExpenses -> "Bu krediye bağlı harcamalar olduğu için silinemez. Krediyi erken kapatabilirsiniz."
                LoanDeletionResult.NotFound -> "Kredi bulunamadı."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
