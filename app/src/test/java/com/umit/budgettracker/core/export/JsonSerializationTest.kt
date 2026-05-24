package com.umit.budgettracker.core.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonSerializationTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun testSerializationRoundTrip() {
        val dto = BudgetTrackerExportDto(
            schemaVersion = 1,
            exportedAt = "2026-05-23T12:00:00Z",
            salaryRules = listOf(SalaryRuleDto(1, 100000, "2026-01", "Test")),
            savingGoals = emptyList(),
            categories = emptyList(),
            paymentAccounts = emptyList(),
            expenses = emptyList(),
            installmentGroups = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList(),
            subscriptionPriceHistory = emptyList(),
            categoryBudgets = emptyList(),
            expenseTemplates = emptyList(),
            debtRecords = emptyList(),
            netWorthSnapshots = emptyList()
        )

        val jsonString = json.encodeToString(dto)
        val decodedDto = json.decodeFromString<BudgetTrackerExportDto>(jsonString)

        assertEquals(dto.schemaVersion, decodedDto.schemaVersion)
        assertEquals(dto.salaryRules.size, decodedDto.salaryRules.size)
        assertEquals(dto.salaryRules[0].amount, decodedDto.salaryRules[0].amount)
    }
}
