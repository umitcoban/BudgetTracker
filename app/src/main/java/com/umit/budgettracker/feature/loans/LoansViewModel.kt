package com.umit.budgettracker.feature.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.LoanPaymentCalculator
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.domain.repository.LoanPaymentRepository
import com.umit.budgettracker.core.domain.repository.LoanDeletionResult
import com.umit.budgettracker.core.domain.repository.LoanRepository
import com.umit.budgettracker.core.domain.usecase.MarkLoanPaymentAsPaidUseCase
import com.umit.budgettracker.core.domain.usecase.MarkLoanPaymentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoansViewModel @Inject constructor(
    private val repository: LoanRepository,
    loanPaymentRepository: LoanPaymentRepository,
    private val markLoanPaymentAsPaid: MarkLoanPaymentAsPaidUseCase
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val loans: StateFlow<List<Loan>> = repository.observeAllLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paidLoanIds: StateFlow<Set<Long>> = _selectedMonth
        .flatMapLatest { month -> loanPaymentRepository.observePaymentsForMonth(month) }
        .map { payments -> payments.map { it.loanId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val paymentsByLoanId: StateFlow<Map<Long, List<LoanPayment>>> = loanPaymentRepository
        .observeAllPayments()
        .map { payments -> payments.groupBy { it.loanId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun markPaymentAsPaid(loan: Loan) {
        viewModelScope.launch {
            _message.value = when (markLoanPaymentAsPaid(loan.id, _selectedMonth.value)) {
                MarkLoanPaymentResult.MarkedPaid -> "Kredi ödemesi ödendi olarak işaretlendi."
                MarkLoanPaymentResult.AlreadyPaid -> "Bu kredi ödemesi zaten işaretlenmiş."
                MarkLoanPaymentResult.NotDue -> "Bu kredi seçili ay için ödeme beklemiyor."
            }
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            _message.value = when (repository.deleteLoan(loan.id)) {
                LoanDeletionResult.Deleted -> "Kredi silindi."
                LoanDeletionResult.HasLinkedExpenses -> "Bu krediye bağlı harcamalar olduğu için silinemez. Krediyi erken kapatabilirsiniz."
                LoanDeletionResult.HasPaymentHistory -> "Bu kredinin ödeme geçmişi olduğu için silinemez."
                LoanDeletionResult.NotFound -> "Kredi bulunamadı."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
