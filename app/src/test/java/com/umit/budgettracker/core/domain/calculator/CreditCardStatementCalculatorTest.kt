package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.model.ExpenseAdjustmentType
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
    fun statementPeriods_stayContiguousWhenStatementDayChangesBetweenMonths() {
        val account = PaymentAccount(1, "Bonus", AccountType.CREDIT_CARD, 11, 20, true)
        val rules = listOf(
            CreditCardStatementRule(1, account.id, YearMonth.of(2026, 10), statementDay = 12, dueDay = 20),
            CreditCardStatementRule(2, account.id, YearMonth.of(2026, 11), statementDay = 11, dueDay = 20)
        )
        val onOldStatementDay = expense(1L, 1_000L, LocalDate.of(2026, 9, 11), account)
        val dayAfterOldStatement = expense(2L, 2_000L, LocalDate.of(2026, 9, 12), account)
        val onNewStatementDay = expense(3L, 4_000L, LocalDate.of(2026, 10, 12), account)
        val dayAfterNewStatement = expense(4L, 8_000L, LocalDate.of(2026, 10, 13), account)
        val all = listOf(onOldStatementDay, dayAfterOldStatement, onNewStatementDay, dayAfterNewStatement)

        val september = calculator.calculateStatement(account, YearMonth.of(2026, 9), all, rules)
        val october = calculator.calculateStatement(account, YearMonth.of(2026, 10), all, rules)
        val november = calculator.calculateStatement(account, YearMonth.of(2026, 11), all, rules)

        assertEquals(LocalDate.of(2026, 9, 11), september.statementEndDate)
        assertEquals(LocalDate.of(2026, 9, 12)..LocalDate.of(2026, 10, 12), october.statementStartDate..october.statementEndDate)
        assertEquals(LocalDate.of(2026, 10, 13)..LocalDate.of(2026, 11, 11), november.statementStartDate..november.statementEndDate)
        assertEquals(listOf(1L), september.expenses.map { it.id })
        assertEquals(listOf(2L, 3L), october.expenses.map { it.id })
        assertEquals(listOf(4L), november.expenses.map { it.id })
    }

    @Test
    fun totalAmount_subtractsRefundsAndNeverGoesBelowZero() {
        val account = PaymentAccount(1, "Card", AccountType.CREDIT_CARD, 10, 20, true)
        val month = YearMonth.of(2026, 6)
        val refunded = expense(id = 1L, amount = 10_000L, date = LocalDate.of(2026, 5, 20), account = account)
        val overRefunded = expense(id = 2L, amount = 3_000L, date = LocalDate.of(2026, 6, 1), account = account)
        val outsideStatement = expense(id = 3L, amount = 50_000L, date = LocalDate.of(2026, 6, 11), account = account)
        val adjustments = listOf(
            refund(expenseId = 1L, amount = 2_500L),
            refund(expenseId = 2L, amount = 9_000L)
        )

        val summary = calculator.calculateStatement(
            account = account,
            paymentMonth = month,
            allExpenses = listOf(refunded, overRefunded, outsideStatement),
            adjustments = adjustments
        )

        assertEquals(7_500L, summary.totalAmount)
        assertEquals(listOf(1L, 2L), summary.expenses.map { it.id })
    }

    private fun expense(id: Long, amount: Long, date: LocalDate, account: PaymentAccount) = Expense(
        id = id,
        title = "Harcama",
        amount = amount,
        expenseDate = date,
        categoryId = 1L,
        paymentAccountId = account.id,
        paymentSourceType = account.type,
        note = null,
        account = account
    )

    private fun refund(expenseId: Long, amount: Long) = ExpenseAdjustment(
        id = expenseId * 100L,
        expenseId = expenseId,
        amount = amount,
        type = ExpenseAdjustmentType.REFUND,
        adjustmentDate = LocalDate.of(2026, 6, 2),
        note = null
    )

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
