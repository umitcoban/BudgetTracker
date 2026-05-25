package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.SalaryRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class SalaryRulesTest {
    @Test
    fun effectiveForMonth_usesFutureRaiseWithoutChangingPastMonths() {
        val rules = listOf(
            SalaryRule(id = 1L, amount = 50_000_00L, effectiveStartMonth = YearMonth.of(2026, 5), note = null),
            SalaryRule(id = 2L, amount = 70_000_00L, effectiveStartMonth = YearMonth.of(2026, 8), note = null)
        )

        assertEquals(50_000_00L, SalaryRules.effectiveForMonth(rules, YearMonth.of(2026, 5))?.amount)
        assertEquals(50_000_00L, SalaryRules.effectiveForMonth(rules, YearMonth.of(2026, 7))?.amount)
        assertEquals(70_000_00L, SalaryRules.effectiveForMonth(rules, YearMonth.of(2026, 8))?.amount)
        assertEquals(70_000_00L, SalaryRules.effectiveForMonth(rules, YearMonth.of(2026, 12))?.amount)
    }

    @Test
    fun idForSave_createsNewRuleWhenExistingRuleMonthChanges() {
        val existing = SalaryRule(
            id = 1L,
            amount = 100_000_00L,
            effectiveStartMonth = YearMonth.of(2026, 5),
            note = null
        )

        assertEquals(1L, SalaryRules.idForSave(existing, YearMonth.of(2026, 5)))
        assertEquals(0L, SalaryRules.idForSave(existing, YearMonth.of(2026, 7)))
        assertEquals(0L, SalaryRules.idForSave(null, YearMonth.of(2026, 7)))
    }
}
