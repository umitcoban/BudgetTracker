package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AnomalyRulesTest {
    private fun expense(id: Long, title: String, amount: Long, date: LocalDate, subscriptionId: Long? = null) = Expense(
        id = id, title = title, amount = amount, expenseDate = date, categoryId = 1, paymentAccountId = 1,
        paymentSourceType = AccountType.CREDIT_CARD, note = null, subscriptionId = subscriptionId
    )

    @Test
    fun findDuplicates_needsSameDayAmountAndSimilarTitle() {
        val day = LocalDate.of(2026, 10, 12)
        val duplicates = AnomalyRules.findDuplicates(
            listOf(
                expense(1, "Migros", 45_075L, day),
                expense(2, "MİGROS KADIKÖY", 45_075L, day),
                expense(3, "Shell", 45_075L, day),
                expense(4, "Migros", 45_075L, day.plusDays(1)),
                expense(5, "Netflix", 22_999L, day, subscriptionId = 9),
                expense(6, "Netflix", 22_999L, day, subscriptionId = 9)
            )
        )
        assertEquals(1, duplicates.size)
        assertEquals(2, duplicates.single().count)
        assertEquals(45_075L, duplicates.single().amount)
    }

    @Test
    fun findSubscriptionIncreases_comparesSameCurrencyOnly() {
        val month = YearMonth.of(2026, 10)
        val subs = listOf(
            Subscription(1, "Netflix", 1, 1, 5, true, null),
            Subscription(2, "Spotify", 1, 1, 5, true, null, originalCurrency = "USD")
        )
        val history = listOf(
            SubscriptionPriceHistory(1, 1, 20_000L, YearMonth.of(2026, 1)),
            SubscriptionPriceHistory(2, 1, 25_000L, month),
            SubscriptionPriceHistory(3, 2, 999L, YearMonth.of(2026, 1), originalCurrency = "USD"),
            SubscriptionPriceHistory(4, 2, 30_000L, month, originalCurrency = "TRY")
        )

        val increases = AnomalyRules.findSubscriptionIncreases(month, subs, history)

        assertEquals(listOf("Netflix"), increases.map { it.title })
        assertEquals(25, increases.single().changePercent)
    }

    @Test
    fun findPositiveTrends_requiresStrictFallAndTenPercentDrop() {
        fun month(m: Int, fuel: Long, food: Long) = MonthlyBudgetSummary(
            yearMonth = YearMonth.of(2026, m),
            categorySummaries = listOf(
                CategorySummary(1, "Yakıt", "gas", 0, fuel),
                CategorySummary(2, "Yemek", "food", 0, food)
            )
        )
        val trends = AnomalyRules.findPositiveTrends(
            listOf(month(7, 120_000L, 100_000L), month(8, 100_000L, 99_000L), month(9, 90_000L, 98_000L), month(10, 80_000L, 97_500L))
        )
        // Yemek falls every month but only by 2,5 % → not mentioned.
        assertEquals(listOf("Yakıt"), trends.map { it.categoryName })
        assertEquals(3, trends.single().months)
    }

    @Test
    fun warnings_keepContractOrderWithNewTypes() {
        val warnings = BudgetWarningRules.build(
            month = YearMonth.of(2026, 9),
            today = LocalDate.of(2026, 9, 18),
            remainingAfterFixedPayments = -1L,
            categorySummaries = listOf(CategorySummary(1, "Market", "cart", 0, 45_000L, budgetLimit = 50_000L)),
            cardPayments = emptyList(),
            loanPayments = emptyList(),
            categorySpikes = listOf(CategorySpike(2, "Yemek", 30_000L, 10_000L, 200)),
            duplicates = listOf(DuplicateCandidate("Migros", LocalDate.of(2026, 9, 12), 45_075L, 2)),
            subscriptionIncreases = listOf(SubscriptionPriceIncrease(1, "Netflix", 20_000L, 25_000L, "TRY", 25)),
            positiveTrends = listOf(PositiveTrend("Yakıt", 120_000L, 80_000L, 3))
        )

        assertEquals(
            listOf(
                BudgetWarningType.NEGATIVE_REMAINING,
                BudgetWarningType.POSSIBLE_DUPLICATE_EXPENSE,
                BudgetWarningType.CATEGORY_SPENDING_SPIKE,
                BudgetWarningType.SUBSCRIPTION_PRICE_INCREASE,
                BudgetWarningType.CATEGORY_LIMIT_80_PERCENT,
                BudgetWarningType.POSITIVE_TREND
            ),
            warnings.map { it.type }
        )
        assertTrue(warnings.last().isPositive)
        assertTrue(warnings.dropLast(1).none { it.isPositive })
    }
}

class RecurringExpenseDetectorTest {
    private val current = YearMonth.of(2026, 10)

    private fun expense(id: Long, title: String, amount: Long, month: YearMonth, day: Int = 5, fixedExpenseId: Long? = null) = Expense(
        id = id, title = title, amount = amount, expenseDate = month.atDay(day), categoryId = 3, paymentAccountId = 2,
        paymentSourceType = AccountType.BANK_ACCOUNT, note = null, fixedExpenseId = fixedExpenseId
    )

    @Test
    fun detect_suggestsStableMonthlyHandEnteredExpenses() {
        val expenses = (0..3).map { expense(it.toLong(), "Spor salonu", 150_000L + it * 1_000L, current.minusMonths(it.toLong()), day = 3) } +
            (0..2).map { expense(10L + it, "Kafe", 10_000L * (it + 1), current.minusMonths(it.toLong())) } + // too volatile
            (1..4).map { expense(20L + it, "Kira", 2_000_000L, current.minusMonths(it.toLong()), fixedExpenseId = 7) } // already fixed

        val suggestions = RecurringExpenseDetector.detect(expenses, emptyList(), emptyList(), emptySet(), current)

        assertEquals(1, suggestions.size)
        val gym = suggestions.single()
        assertEquals("Spor salonu", gym.title)
        assertEquals(4, gym.monthCount)
        assertEquals(3, gym.typicalDay)
        assertEquals(152_000L, gym.typicalAmount)
    }

    @Test
    fun detect_skipsDismissedAndExistingSubscriptionTitles() {
        val expenses = (0..3).map { expense(it.toLong(), "Spor salonu", 150_000L, current.minusMonths(it.toLong())) } +
            (0..3).map { expense(10L + it, "YouTube Premium", 7_999L, current.minusMonths(it.toLong())) }
        val subs = listOf(Subscription(1, "Youtube premium", 1, 1, 5, true, null))

        val suggestions = RecurringExpenseDetector.detect(expenses, subs, emptyList(), setOf("salonu spor"), current)

        assertTrue(suggestions.isEmpty())
    }
}

class SimulationRulesTest {
    private val current = YearMonth.of(2026, 10)

    private fun summary(month: YearMonth, variable: Long = 0L, loan: LoanMonthlyPayment? = null, sub: SubscriptionMonthlyPayment? = null, fixed: FixedExpenseMonthlyPayment? = null) =
        MonthlyBudgetSummary(
            yearMonth = month,
            salaryAmount = 6_000_000L,
            totalExpenseAmount = variable,
            variableExpenseAmount = variable,
            subscriptionAmount = sub?.amount ?: 0L,
            loanPaymentAmount = loan?.amount ?: 0L,
            fixedExpenseAmount = fixed?.amount ?: 0L,
            subscriptionPayments = listOfNotNull(sub),
            loanPayments = listOfNotNull(loan),
            fixedExpensePayments = listOfNotNull(fixed),
            categorySummaries = listOf(CategorySummary(4, "Yemek", "food", 0, variable / 2))
        )

    private val past = (3 downTo 1).map { summary(current.minusMonths(it.toLong()), variable = 3_000_000L) }

    @Test
    fun baseline_assumesAverageVariableSpendingInFutureMonths() {
        val future = (0 until 3).map { summary(current.plusMonths(it.toLong()), variable = if (it == 0) 1_000_000L else 0L) }
        val result = SimulationRules.simulate(future, past, current, emptyList())
        // Current month: 60.000 − 10.000 spent − 20.000 still expected; later months: 60.000 − 30.000.
        assertEquals(listOf(3_000_000L, 3_000_000L, 3_000_000L), result.months.map { it.baselineNet })
        assertEquals(3_000_000L, result.assumedVariableSpending)
        assertEquals(0L, result.totalDifference)
    }

    @Test
    fun scenarios_cancelSubscriptionCutCategoryAndRaiseRent() {
        val sub = SubscriptionMonthlyPayment(1, "Netflix", 25_000L, billingDay = 5, categoryId = 1, paymentAccountId = 1)
        val rent = FixedExpenseMonthlyPayment(7, "Kira", 2_000_000L, 1, 1, 1)
        val future = (0 until 4).map { summary(current.plusMonths(it.toLong()), sub = sub, fixed = rent) }

        val result = SimulationRules.simulate(
            future, past, current,
            listOf(
                Scenario.CancelSubscription(1, "Netflix", current.plusMonths(1)),
                Scenario.CutCategory(4, "Yemek", 20),
                Scenario.FixedExpenseRaise(7, "Kira", 40, current.plusMonths(2))
            )
        )

        val diffs = result.months.map { it.difference }
        // Cut: 15.000 avg × 20 % = 3.000 every month; Netflix 250 from month 2; rent +8.000 cost from month 3.
        assertEquals(listOf(300_000L, 325_000L, -475_000L, -475_000L), diffs)
    }

    @Test
    fun closeLoanEarly_paysRemainingOnceThenSavesInstalments() {
        val loan = { remaining: Long -> LoanMonthlyPayment(5, "İhtiyaç", 100_000L, 10, 1, 4, remaining, null, null) }
        val future = listOf(
            summary(current, loan = loan(300_000L)),
            summary(current.plusMonths(1), loan = loan(200_000L)),
            summary(current.plusMonths(2), loan = loan(100_000L)),
            summary(current.plusMonths(3), loan = loan(0L))
        )

        val result = SimulationRules.simulate(future, past, current, listOf(Scenario.CloseLoanEarly(5, "İhtiyaç", current.plusMonths(1), null)))

        assertEquals(listOf(0L, -200_000L, 100_000L, 100_000L), result.months.map { it.difference })
        assertEquals(0L, result.totalDifference)
    }

    @Test
    fun salaryChange_appliesFromMonth() {
        val future = (0 until 2).map { summary(current.plusMonths(it.toLong())) }
        val result = SimulationRules.simulate(future, past, current, listOf(Scenario.SalaryChange(10, current.plusMonths(1))))
        assertEquals(listOf(0L, 600_000L), result.months.map { it.difference })
    }
}
