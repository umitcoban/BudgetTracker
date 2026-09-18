package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.domain.repository.LoanPaymentRepository
import com.umit.budgettracker.core.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Month-independent loan data; combine with [LoanMonthlyCalculator.calculatePayments] per month. */
data class LoanInputs(
    val loans: List<Loan>,
    val payments: List<LoanPayment>
)

class LoanMonthlyCalculator @Inject constructor(
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository
) {
    fun getPaymentsForMonth(yearMonth: YearMonth): Flow<List<LoanMonthlyPayment>> {
        return combine(
            loanRepository.observeActiveLoans(),
            loanPaymentRepository.observePaymentsForMonth(yearMonth)
        ) { loans, paidPayments ->
            calculatePayments(yearMonth, loans, paidPayments)
        }
    }

    fun observeInputs(): Flow<LoanInputs> {
        return combine(
            loanRepository.observeActiveLoans(),
            loanPaymentRepository.observeAllPayments()
        ) { loans, payments -> LoanInputs(loans, payments) }
    }

    /**
     * Pure per-month projection over already-loaded active loans. [paidPayments] may contain
     * records from any month; only those matching [yearMonth] mark a loan as paid.
     */
    fun calculatePayments(
        yearMonth: YearMonth,
        loans: List<Loan>,
        paidPayments: List<LoanPayment>
    ): List<LoanMonthlyPayment> {
        val paidLoanIds = paidPayments
            .filter { it.paymentMonth == yearMonth }
            .map { it.loanId }
            .toSet()
        return loans.mapNotNull { loan ->
            if (!LoanPaymentRules.isDueForMonth(loan, yearMonth) || loan.id in paidLoanIds) {
                null
            } else {
                val start = loan.startMonth
                val monthsDiff = ChronoUnit.MONTHS.between(start, yearMonth).toInt()
                val currentInstallment = monthsDiff + 1
                val remainingInstallments = loan.installmentCount - currentInstallment

                LoanMonthlyPayment(
                    loanId = loan.id,
                    title = loan.title,
                    amount = loan.monthlyPaymentAmount,
                    paymentDay = loan.paymentDay,
                    currentInstallment = currentInstallment,
                    totalInstallments = loan.installmentCount,
                    remainingAmount = remainingInstallments * loan.monthlyPaymentAmount,
                    categoryId = loan.categoryId,
                    paymentAccountId = loan.paymentAccountId,
                    category = loan.category,
                    account = loan.account
                )
            }
        }
    }
}
