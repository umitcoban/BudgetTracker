package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.LoanPaymentRules
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.domain.repository.LoanPaymentRepository
import com.umit.budgettracker.core.domain.repository.LoanRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class MarkLoanPaymentAsPaidUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository
) {
    suspend operator fun invoke(loanId: Long, month: YearMonth): MarkLoanPaymentResult {
        val loan = loanRepository.observeLoanById(loanId).first() ?: return MarkLoanPaymentResult.NotDue
        if (!LoanPaymentRules.isDueForMonth(loan, month)) return MarkLoanPaymentResult.NotDue
        if (loanPaymentRepository.getPayment(loanId, month) != null) return MarkLoanPaymentResult.AlreadyPaid

        loanPaymentRepository.insertPayment(
            LoanPayment(
                id = 0,
                loanId = loan.id,
                paymentMonth = month,
                amount = loan.monthlyPaymentAmount,
                paidAt = LocalDate.now()
            )
        )
        return MarkLoanPaymentResult.MarkedPaid
    }
}

enum class MarkLoanPaymentResult {
    MarkedPaid,
    AlreadyPaid,
    NotDue
}
