package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.LoanPaymentRules
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.LoanPaymentRepository
import com.umit.budgettracker.core.domain.repository.LoanRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Records a loan instalment for a month once, and writes the matching [Expense] (linked via
 * `loanId`) so the payment shows up in Harcamalar, reports and the card statement like
 * subscription and fixed-expense payments do.
 */
class MarkLoanPaymentAsPaidUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository
) {
    suspend operator fun invoke(loanId: Long, month: YearMonth): MarkLoanPaymentResult {
        val loan = loanRepository.observeLoanById(loanId).first() ?: return MarkLoanPaymentResult.NotDue
        if (!LoanPaymentRules.isDueForMonth(loan, month)) return MarkLoanPaymentResult.NotDue
        if (loanPaymentRepository.getPayment(loanId, month) != null) return MarkLoanPaymentResult.AlreadyPaid

        val categoryId = loan.categoryId
            ?: categoryRepository.observeActiveCategories().first()
                .let { categories -> categories.firstOrNull { it.name == DEFAULT_LOAN_CATEGORY_NAME } ?: categories.firstOrNull() }
                ?.id
            ?: return MarkLoanPaymentResult.MissingRequiredSelection
        val account = loan.paymentAccountId?.let { accountRepository.getAccountById(it) }
            ?: accountRepository.observeActiveAccounts().first()
                .let { accounts -> accounts.firstOrNull { it.type == AccountType.BANK_ACCOUNT } ?: accounts.firstOrNull() }
            ?: return MarkLoanPaymentResult.MissingRequiredSelection

        expenseRepository.insertExpense(
            Expense(
                id = 0,
                title = instalmentTitle(loan, month),
                amount = loan.monthlyPaymentAmount,
                expenseDate = month.atDay(loan.paymentDay.coerceIn(1, month.lengthOfMonth())),
                categoryId = categoryId,
                paymentAccountId = account.id,
                paymentSourceType = account.type,
                note = "Kredi taksidi",
                loanId = loan.id
            )
        )
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

    private fun instalmentTitle(loan: Loan, month: YearMonth): String {
        val instalment = ChronoUnit.MONTHS.between(loan.startMonth, month).toInt() + 1
        return "${loan.title} (${instalment}/${loan.installmentCount})"
    }

    private companion object {
        const val DEFAULT_LOAN_CATEGORY_NAME = "Kredi"
    }
}

enum class MarkLoanPaymentResult {
    MarkedPaid,
    AlreadyPaid,
    NotDue,
    MissingRequiredSelection
}
