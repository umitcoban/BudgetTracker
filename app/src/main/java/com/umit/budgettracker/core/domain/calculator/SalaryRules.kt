package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.SalaryRule
import java.time.YearMonth

object SalaryRules {
    fun effectiveForMonth(rules: List<SalaryRule>, month: YearMonth): SalaryRule? {
        return rules
            .filter { !it.effectiveStartMonth.isAfter(month) }
            .maxByOrNull { it.effectiveStartMonth }
    }

    fun idForSave(existingRule: SalaryRule?, effectiveMonth: YearMonth): Long {
        return if (existingRule != null && existingRule.effectiveStartMonth == effectiveMonth) {
            existingRule.id
        } else {
            0L
        }
    }
}
