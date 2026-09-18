package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.BudgetWarningType
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.util.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetWarningRulesTest {
    private val month = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 18)

    @Test
    fun build_returnsNothingWhenEverythingIsHealthy() {
        val warnings = BudgetWarningRules.build(
            month = month,
            today = today,
            remainingAfterFixedPayments = 12_000L,
            categorySummaries = listOf(category("Market", spent = 30_000L, limit = 50_000L)),
            cardPayments = emptyList(),
            loanPayments = emptyList()
        )

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun build_ordersBySeverityWithNegativeRemainingFirst() {
        val warnings = BudgetWarningRules.build(
            month = month,
            today = today,
            remainingAfterFixedPayments = -5_000L,
            categorySummaries = listOf(
                category("Market", spent = 45_000L, limit = 50_000L),
                category("Yemek", spent = 26_000L, limit = 20_000L)
            ),
            cardPayments = listOf(card("Kart", dueDate = today.plusDays(3), amount = 80_000L)),
            loanPayments = listOf(loan("Konut", paymentDay = 20, amount = 15_000L))
        )

        assertEquals(
            listOf(
                BudgetWarningType.NEGATIVE_REMAINING,
                BudgetWarningType.CATEGORY_LIMIT_EXCEEDED,
                BudgetWarningType.UPCOMING_CARD_PAYMENT,
                BudgetWarningType.UPCOMING_LOAN_PAYMENT,
                BudgetWarningType.CATEGORY_LIMIT_80_PERCENT
            ),
            warnings.map { it.type }
        )
        assertEquals(
            "Yemek bütçesi ${tl(6_000L)} aşıldı (${tl(26_000L)} / ${tl(20_000L)}).",
            warnings[1].message
        )
        assertEquals(
            "Market bütçesinin %90'i kullanıldı (${tl(45_000L)} / ${tl(50_000L)}).",
            warnings[4].message
        )
    }

    @Test
    fun build_usesIntegerPercentAndDoesNotDoubleReportExceededCategories() {
        val warnings = BudgetWarningRules.build(
            month = month,
            today = today,
            remainingAfterFixedPayments = 0L,
            categorySummaries = listOf(
                category("Tam", spent = 50_000L, limit = 50_000L),
                category("Altında", spent = 39_999L, limit = 50_000L)
            ),
            cardPayments = emptyList(),
            loanPayments = emptyList()
        )

        // 100% used but not exceeded -> only the 80% warning; 79.998% rounds down -> none.
        assertEquals(listOf(BudgetWarningType.CATEGORY_LIMIT_80_PERCENT), warnings.map { it.type })
        assertTrue(warnings.single().message.startsWith("Tam bütçesinin %100'i"))
    }

    @Test
    fun build_skipsPaymentsOutsideTheSevenDayWindowOrOutsideTheCurrentMonth() {
        val farAway = BudgetWarningRules.build(
            month = month,
            today = today,
            remainingAfterFixedPayments = 0L,
            categorySummaries = emptyList(),
            cardPayments = listOf(card("Kart", dueDate = today.plusDays(8), amount = 1_000L)),
            loanPayments = listOf(loan("Konut", paymentDay = 26, amount = 1_000L))
        )
        val otherMonth = BudgetWarningRules.build(
            month = month.plusMonths(1),
            today = today,
            remainingAfterFixedPayments = 0L,
            categorySummaries = emptyList(),
            cardPayments = listOf(card("Kart", dueDate = today.plusDays(1), amount = 1_000L)),
            loanPayments = listOf(loan("Konut", paymentDay = 19, amount = 1_000L))
        )

        assertTrue(farAway.isEmpty())
        assertTrue(otherMonth.isEmpty())
    }

    @Test
    fun build_labelsDueTodayTomorrowOverdueAndSkipsPaidCards() {
        val warnings = BudgetWarningRules.build(
            month = month,
            today = today,
            remainingAfterFixedPayments = 0L,
            categorySummaries = emptyList(),
            cardPayments = listOf(
                card("Ödenmiş", dueDate = today, amount = 5_000L, isPaid = true),
                card("Bugün", dueDate = today, amount = 5_000L),
                card("Geçmiş", dueDate = today.minusDays(2), amount = 3_000L)
            ),
            loanPayments = listOf(loan("Yarın", paymentDay = 19, amount = 2_000L))
        )

        assertEquals(
            listOf(
                "Geçmiş ekstresi için ödeme günü geçti: ${tl(3_000L)}.",
                "Bugün ekstresi bugün ödenecek: ${tl(5_000L)}.",
                "Yarın kredi taksidi yarın ödenecek: ${tl(2_000L)}."
            ),
            warnings.map { it.message }
        )
    }

    private fun tl(minorUnits: Long) = MoneyFormatter.format(minorUnits)

    private fun category(name: String, spent: Long, limit: Long?) = CategorySummary(
        categoryId = name.hashCode().toLong(),
        categoryName = name,
        iconName = "category",
        colorValue = 0,
        amount = spent,
        budgetLimit = limit,
        percentage = limit?.let { spent.toFloat() / it }
    )

    private fun card(name: String, dueDate: LocalDate, amount: Long, isPaid: Boolean = false) = CreditCardPaymentDue(
        accountId = name.hashCode().toLong(),
        accountName = name,
        dueDate = dueDate,
        amount = amount,
        isPaid = isPaid
    )

    private fun loan(title: String, paymentDay: Int, amount: Long) = LoanMonthlyPayment(
        loanId = title.hashCode().toLong(),
        title = title,
        amount = amount,
        paymentDay = paymentDay,
        currentInstallment = 1,
        totalInstallments = 12,
        remainingAmount = amount * 11,
        categoryId = null,
        paymentAccountId = null
    )
}
