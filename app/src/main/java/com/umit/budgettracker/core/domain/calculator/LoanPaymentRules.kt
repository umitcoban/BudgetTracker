package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Loan
import java.time.YearMonth

object LoanPaymentRules {
    fun isDueForMonth(loan: Loan, month: YearMonth): Boolean {
        if (!loan.isActive) return false
        val endMonth = loan.startMonth.plusMonths((loan.installmentCount - 1).toLong())
        return !month.isBefore(loan.startMonth) && !month.isAfter(endMonth)
    }
}
