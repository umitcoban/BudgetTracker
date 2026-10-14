package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class InflationRulesTest {
    private val seed = InflationRules.SEED

    @Test
    fun seed_isConsistentWithTcmbAnnualRate() {
        // Twelve monthly rates Oct 2025 – Sep 2026 compound to %29,72 vs. TCMB's %29,73.
        assertEquals(2972, InflationRules.compoundedAnnualBp(seed, YearMonth.of(2026, 9)))
        assertTrue(InflationRules.inconsistentMonths(seed).isEmpty())
    }

    @Test
    fun inconsistentAnnualRate_isReported() {
        val wrong = seed.map { if (it.month == YearMonth.of(2026, 9)) it.copy(annualRateBp = 3100) else it }
        val issue = InflationRules.inconsistentMonths(wrong).single()
        assertEquals(YearMonth.of(2026, 9), issue.month)
        assertEquals(2972, issue.compoundedBp)
    }

    @Test
    fun realValue_compoundsMonthlyRatesWithIntegerArithmetic() {
        // 1.000 ₺ at end of Aug 2026 → Sep 2026 money with %1,84.
        val result = InflationRules.realValue(seed, 100_000L, YearMonth.of(2026, 8), YearMonth.of(2026, 9))!!
        assertEquals(101_840L, result.amount)
        assertFalse(result.isEstimated)
        assertEquals(184, result.inflationBp)
    }

    @Test
    fun missingMonth_usesLastKnownRateAndIsMarkedEstimated() {
        val factor = InflationRules.factor(seed, YearMonth.of(2026, 9), YearMonth.of(2026, 10))!!
        assertTrue(factor.isEstimated)
        assertEquals(listOf(YearMonth.of(2026, 10)), factor.estimatedMonths)
        assertEquals(184, factor.changeBp())
        assertEquals(listOf(YearMonth.of(2026, 10)), InflationRules.missingMonths(seed, YearMonth.of(2026, 10)))
    }

    @Test
    fun noRateBeforePeriod_returnsNull() {
        assertNull(InflationRules.factor(seed, YearMonth.of(2024, 1), YearMonth.of(2024, 3)))
    }

    @Test
    fun realChange_salaryRaiseBelowInflationIsNegative() {
        // 50.000 → 60.000 (+%20) over the year where prices rose ~%29,72.
        val bp = InflationRules.realChangeBp(seed, 5_000_000L, YearMonth.of(2025, 9), 6_000_000L, YearMonth.of(2026, 9))!!
        assertTrue(bp in -800..-700)
    }

    @Test
    fun parseAndFormatBp_roundTripTurkishInput() {
        assertEquals(184, InflationRules.parseBp("1,84"))
        assertEquals(184, InflationRules.parseBp("%1,84"))
        assertEquals(2973, InflationRules.parseBp("29.73"))
        assertEquals(30, InflationRules.parseBp("0,3"))
        assertEquals(185, InflationRules.parseBp("1,845"))
        assertEquals(-50, InflationRules.parseBp("-0,5"))
        assertNull(InflationRules.parseBp("abc"))
        assertNull(InflationRules.parseBp(""))
        assertEquals("%1,84", InflationRules.formatBp(184))
        assertEquals("−%0,05", InflationRules.formatBp(-5))
    }
}

class SavingsGoalRulesTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun goal(id: Long, target: Long, month: YearMonth?, priority: Int = 2) =
        SavingsGoal(id, "Hedef $id", target, month, priority, "savings", null, true, null)

    @Test
    fun progress_derivesMonthlyNeedAndDelayFromContributions() {
        val tatil = goal(1, 6_000_000L, YearMonth.of(2027, 7))
        val contributions = listOf(
            GoalContribution(1, 1, 2_000_000L, LocalDate.of(2026, 1, 5), null),
            GoalContribution(2, 1, 300_000L, LocalDate.of(2026, 8, 5), null),
            GoalContribution(3, 1, 300_000L, LocalDate.of(2026, 9, 5), null),
            GoalContribution(4, 1, 280_000L, LocalDate.of(2026, 10, 5), null)
        )

        val progress = SavingsGoalRules.progress(tatil, contributions, null, today)

        assertEquals(2_880_000L, progress.savedAmount)       // 28.800 ₺
        assertEquals(3_120_000L, progress.remainingAmount)   // 31.200 ₺
        assertEquals(48, progress.percent)
        assertEquals(10, progress.monthsLeft)                // Oct 2026 … Jul 2027
        assertEquals(312_000L, progress.requiredMonthly)     // 3.120 ₺/ay
        assertEquals(293_333L, progress.averageMonthly)      // last three months
        assertEquals(YearMonth.of(2027, 8), progress.projectedFinish)
        assertEquals(1, progress.delayMonths)
    }

    @Test
    fun linkedAssetValue_replacesContributionSum() {
        val progress = SavingsGoalRules.progress(goal(1, 1_000_000L, null), emptyList(), linkedAssetValue = 1_200_000L, today = today)
        assertEquals(1_200_000L, progress.savedAmount)
        assertTrue(progress.isCompleted)
        assertEquals(100, progress.percent)
    }

    @Test
    fun suggestAllocation_servesHigherPriorityFirstAndSharesShortfall() {
        val high = SavingsGoalRules.progress(goal(1, 3_120_000L * 10, YearMonth.of(2027, 7), priority = 1), emptyList(), null, today)
        val midA = SavingsGoalRules.progress(goal(2, 1_200_000L, YearMonth.of(2027, 9), priority = 2), emptyList(), null, today)
        val midB = SavingsGoalRules.progress(goal(3, 2_400_000L, YearMonth.of(2027, 9), priority = 2), emptyList(), null, today)

        val allocation = SavingsGoalRules.suggestAllocation(3_420_000L, listOf(midA, high, midB))

        assertEquals(3_120_000L, allocation.first { it.goalId == 1L }.amount)
        // 300.000 left for two goals asking 100.000 and 200.000 → fits exactly.
        assertEquals(100_000L, allocation.first { it.goalId == 2L }.amount)
        assertEquals(200_000L, allocation.first { it.goalId == 3L }.amount)
        assertEquals(3_420_000L, allocation.sumOf { it.amount })

        val tight = SavingsGoalRules.suggestAllocation(3_270_000L, listOf(midA, high, midB))
        assertEquals(3_270_000L, tight.sumOf { it.amount })
        assertEquals(50_000L, tight.first { it.goalId == 2L }.amount)
    }

    @Test
    fun suggestAllocation_ignoresNonPositiveLeftover() {
        val p = SavingsGoalRules.progress(goal(1, 100_000L, null), emptyList(), null, today)
        assertTrue(SavingsGoalRules.suggestAllocation(0L, listOf(p)).isEmpty())
    }
}

class EmergencyAndPlannerRulesTest {
    @Test
    fun emergencyFund_coversMonthsInTenths() {
        val months = listOf(3_000_000L, 3_150_000L, 3_300_000L).mapIndexed { index, amount ->
            MonthlyBudgetSummary(yearMonth = YearMonth.of(2026, 7 + index), essentialExpenseAmount = amount)
        }
        val essential = EmergencyFundRules.essentialMonthlyAverage(months)
        assertEquals(3_150_000L, essential) // 31.500 ₺

        val status = EmergencyFundRules.status(essential, fundAmount = 7_875_000L, targetMonths = 6)
        assertEquals(25, status.coveredMonthsTenths)   // 2,5 ay
        assertEquals(18_900_000L, status.targetAmount) // 189.000 ₺
        assertEquals(11_025_000L, status.shortfall)    // 110.250 ₺
    }

    @Test
    fun emergencyFund_ignoresEmptyMonthsAndHandlesNoData() {
        val months = listOf(MonthlyBudgetSummary(YearMonth.of(2026, 9)), MonthlyBudgetSummary(YearMonth.of(2026, 10), essentialExpenseAmount = 1_000_000L))
        assertEquals(1_000_000L, EmergencyFundRules.essentialMonthlyAverage(months))
        assertNull(EmergencyFundRules.status(0L, 500L).coveredMonthsTenths)
    }

    @Test
    fun plannedExpense_spreadsRemainingOverMonthsInclusive() {
        val kasko = PlannedExpense(1, "Kasko", 1_800_000L, LocalDate.of(2027, 3, 15), null, 0L, isYearly = true, isCompleted = false, note = null)
        assertEquals(6, PlannedExpenseRules.monthsUntil(kasko, YearMonth.of(2026, 10)))
        assertEquals(300_000L, PlannedExpenseRules.monthlyReserve(kasko, YearMonth.of(2026, 10)))
        val partly = kasko.copy(savedAmount = 600_000L)
        assertEquals(200_000L, PlannedExpenseRules.monthlyReserve(partly, YearMonth.of(2026, 10)))
        assertEquals(0L, PlannedExpenseRules.monthlyReserve(kasko, YearMonth.of(2027, 4)))
        val next = PlannedExpenseRules.nextOccurrence(partly)
        assertEquals(LocalDate.of(2028, 3, 15), next.dueDate)
        assertEquals(0L, next.savedAmount)
    }

    @Test
    fun businessDays_shiftWeekendsAndHolidays() {
        val holidays = listOf(PublicHoliday(LocalDate.of(2026, 10, 29), "Cumhuriyet Bayramı"))
        val holidayDates = holidays.map { it.date }.toSet()
        // Saturday 2026-10-24 → Monday 26
        assertEquals(LocalDate.of(2026, 10, 26), BusinessDayRules.nextBusinessDay(LocalDate.of(2026, 10, 24), holidayDates))
        // Thursday holiday → Friday 30
        assertEquals(LocalDate.of(2026, 10, 30), BusinessDayRules.nextBusinessDay(LocalDate.of(2026, 10, 29), holidayDates))
        assertNull(BusinessDayRules.shiftNote(LocalDate.of(2026, 10, 27), holidays))
        assertTrue(BusinessDayRules.shiftNote(LocalDate.of(2026, 10, 29), holidays)!!.startsWith("Cumhuriyet Bayramı"))
    }
}

class CashForecastRulesTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun event(day: Int, title: String, amount: Long, type: CashFlowEventType) =
        CashFlowEvent(LocalDate.of(2026, 10, day), title, amount, type, null, null)

    @Test
    fun forecast_findsTightestDayPressureEventAndNextIncome() {
        val events = listOf(
            event(5, "Market", 50_000L, CashFlowEventType.EXPENSE),          // already in balance
            event(15, "Netflix", 22_999L, CashFlowEventType.SUBSCRIPTION),
            event(20, "Kart Ekstresi", 1_840_000L, CashFlowEventType.CREDIT_CARD_PAYMENT),
            event(28, "Maaş", 7_000_000L, CashFlowEventType.INCOME)
        )

        val forecast = CashForecastRules.forecast(1_432_999L, events, today, today.plusDays(45))

        assertEquals(LocalDate.of(2026, 10, 20), forecast.lowestDate)
        assertEquals(-430_000L, forecast.lowestBalance)
        assertEquals(430_000L, forecast.shortfall)
        assertEquals("Kart Ekstresi", forecast.pressureEvent?.title)
        assertEquals("Maaş", forecast.nextIncome?.title)
    }

    @Test
    fun forecast_withoutShortfallKeepsStartAsLowestWhenOnlyIncomes() {
        val forecast = CashForecastRules.forecast(100_000L, listOf(event(28, "Maaş", 5_000_000L, CashFlowEventType.INCOME)), today, today.plusDays(45))
        assertEquals(0L, forecast.shortfall)
        assertEquals(today, forecast.lowestDate)
        assertNull(forecast.pressureEvent)
    }
}

class PriceAlertRulesTest {
    private val price = MarketPrice("GOLD:GRAM", "Gram altın", 6_600_000_000L, 6_400_000_000L, "Truncgil", 0L)

    @Test
    fun triggered_respectsConditionAndCooldown() {
        val above = PriceAlert(1, "GOLD:GRAM", AlertCondition.ABOVE, 6_500_000_000L, true, null, null)
        val below = PriceAlert(2, "GOLD:GRAM", AlertCondition.BELOW, 6_500_000_000L, true, null, null)
        val cooling = above.copy(id = 3, lastTriggeredAt = 1_000L)
        val off = above.copy(id = 4, isActive = false)
        val now = 2_000L

        val fired = PriceAlertRules.triggered(listOf(above, below, cooling, off), listOf(price), now)

        assertEquals(listOf(1L), fired.map { it.first.id })
    }

    @Test
    fun significantMoves_usesBasisPointsOnWatchedCodes() {
        val moves = PriceAlertRules.significantMoves(listOf(price, price.copy(code = "FUND:GTL")), thresholdBp = 300)
        assertEquals(1, moves.size)
        assertEquals(313, moves.single().changeBp)
        assertTrue(PriceAlertRules.significantMoves(listOf(price), thresholdBp = 400).isEmpty())
    }
}

class YearEndRulesTest {
    @Test
    fun build_summarisesYearAndAdjustsComparisonForInflation() {
        val months = (1..9).map { m ->
            MonthlyBudgetSummary(
                yearMonth = YearMonth.of(2026, m),
                salaryAmount = 6_000_000L,
                totalExpenseAmount = if (m == 4) 5_000_000L else 3_000_000L,
                categorySummaries = listOf(CategorySummary(1, "Market", "cart", 0, 1_000_000L))
            )
        }
        val previous = months.map { it.copy(yearMonth = it.yearMonth.minusYears(1), salaryAmount = 5_000_000L, totalExpenseAmount = 2_500_000L) }
        val fee = Expense(1, "Kart yıllık ücret", 120_000L, LocalDate.of(2026, 3, 1), 1, 1, AccountType.CREDIT_CARD, null)
        val faiz = Expense(2, "Gecikme faizi", 30_000L, LocalDate.of(2026, 5, 1), 1, 1, AccountType.CREDIT_CARD, null)
        val normal = Expense(3, "Migros", 30_000L, LocalDate.of(2026, 5, 1), 1, 1, AccountType.CREDIT_CARD, null)

        val report = YearEndRules.build(2026, months, previous, listOf(fee, faiz, normal), InflationRules.SEED)

        assertEquals(54_000_000L, report.totalIncome)
        assertEquals(29_000_000L, report.totalExpense)
        assertEquals(YearMonth.of(2026, 4), report.mostExpensiveMonth?.yearMonth)
        assertEquals(150_000L, report.feeTotal)
        assertEquals(2, report.feeCount)
        assertEquals(28, report.expenseChangePercent) // 29.0M vs 22.5M
        assertEquals(2972, report.inflationBp)
        // Nominal +%28,9 is roughly flat in real terms after %29,7 inflation.
        assertTrue(report.realExpenseChangeBp!! in -100..0)
        assertEquals(9_000_000L, report.topCategories.single().amount)
        assertNotNull(report.lastMonth)
    }
}
