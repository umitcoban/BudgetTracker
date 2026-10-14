package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Loan
import java.time.YearMonth

/** Where a loan stands in a given month, for the Loans screen sections. */
enum class LoanMonthStatus { DUE, PAID, NOT_STARTED, FINISHED, CLOSED }

object LoanPaymentRules {
    fun isDueForMonth(loan: Loan, month: YearMonth): Boolean {
        if (!loan.isActive) return false
        return !month.isBefore(loan.startMonth) && !month.isAfter(endMonth(loan))
    }

    /** Month of the last instalment. */
    fun endMonth(loan: Loan): YearMonth = loan.startMonth.plusMonths((loan.installmentCount - 1).toLong())

    /** 1-based instalment number falling in [month]. */
    fun installmentNumber(loan: Loan, month: YearMonth): Int =
        java.time.temporal.ChronoUnit.MONTHS.between(loan.startMonth, month).toInt() + 1

    fun statusFor(loan: Loan, month: YearMonth, paidThisMonth: Boolean): LoanMonthStatus = when {
        loan.closedAt != null || !loan.isActive -> LoanMonthStatus.CLOSED
        month.isAfter(endMonth(loan)) -> LoanMonthStatus.FINISHED
        month.isBefore(loan.startMonth) -> LoanMonthStatus.NOT_STARTED
        paidThisMonth -> LoanMonthStatus.PAID
        else -> LoanMonthStatus.DUE
    }
}
