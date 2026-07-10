package com.umit.budgettracker.core.domain.calculator

object LoanPaymentCalculator {
    fun calculateMonthlyPayment(principalAmount: Long, installmentCount: Int): Long? {
        if (principalAmount <= 0L || installmentCount <= 0) return null

        return (principalAmount / installmentCount).takeIf { it > 0L }
    }
}
