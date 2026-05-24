package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CreditCardStatementCalculatorTest {

    private val calculator = CreditCardStatementCalculator()

    @Test
    fun testStatementDates_NormalCase() {
        val account = PaymentAccount(1, "Card", AccountType.CREDIT_CARD, 10, 20, true)
        val month = YearMonth.of(2026, 6)
        val summary = calculator.calculateStatement(account, month, emptyList())

        assertEquals(LocalDate.of(2026, 6, 20), summary.dueDate)
        assertEquals(LocalDate.of(2026, 6, 10), summary.statementEndDate)
        assertEquals(LocalDate.of(2026, 5, 11), summary.statementStartDate)
    }

    @Test
    fun testStatementDates_DueDayBeforeStatementDay() {
        val account = PaymentAccount(1, "Card", AccountType.CREDIT_CARD, 25, 5, true)
        val month = YearMonth.of(2026, 6)
        val summary = calculator.calculateStatement(account, month, emptyList())

        assertEquals(LocalDate.of(2026, 6, 5), summary.dueDate)
        assertEquals(LocalDate.of(2026, 5, 25), summary.statementEndDate)
        assertEquals(LocalDate.of(2026, 4, 26), summary.statementStartDate)
    }

    @Test
    fun testClamping() {
        val account = PaymentAccount(1, "Card", AccountType.CREDIT_CARD, 31, 10, true)
        val month = YearMonth.of(2026, 4) // April has 30 days
        val summary = calculator.calculateStatement(account, month, emptyList())

        // Since dueDay (10) < statementDay (31), statement end month is previous (March).
        // March has 31 days. So end date is 2026-03-31.
        assertEquals(LocalDate.of(2026, 3, 31), summary.statementEndDate)
    }
}
