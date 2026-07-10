package com.umit.budgettracker.core.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoanPaymentCalculatorTest {
    @Test
    fun calculatesMonthlyPaymentFromPrincipalAndInstallmentCount() {
        assertEquals(
            83_333L,
            LoanPaymentCalculator.calculateMonthlyPayment(
                principalAmount = 1_000_000L,
                installmentCount = 12
            )
        )
    }

    @Test
    fun returnsNullForInvalidLoanValues() {
        assertNull(LoanPaymentCalculator.calculateMonthlyPayment(0L, 12))
        assertNull(LoanPaymentCalculator.calculateMonthlyPayment(100_000L, 0))
    }
}
