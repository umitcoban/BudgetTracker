package com.umit.budgettracker.core.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonSerializationTest {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

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

    @Test
    fun oldSchemaWithoutNewNullableFields_decodesWithDefaults() {
        val jsonString = """
            {
              "appName": "BudgetTracker",
              "schemaVersion": 9,
              "exportedAt": "2026-05-23T12:00:00Z",
              "salaryRules": [],
              "incomes": [],
              "fixedExpenses": [],
              "savingGoals": [],
              "categories": [],
              "paymentAccounts": [],
              "expenses": [],
              "installmentGroups": [],
              "loans": [
                {
                  "id": 1,
                  "title": "Konut Kredisi",
                  "principalAmount": 10000000,
                  "monthlyPaymentAmount": 500000,
                  "installmentCount": 24,
                  "startMonth": "2026-05",
                  "paymentDay": 5,
                  "categoryId": null,
                  "paymentAccountId": null,
                  "note": null,
                  "isActive": false
                }
              ],
              "subscriptions": [],
              "subscriptionPriceHistory": [
                {"id": 1, "subscriptionId": 1, "amount": 50000, "effectiveFromMonth": "2026-05"}
              ],
              "categoryBudgets": [],
              "expenseTemplates": [],
              "debtRecords": [],
              "netWorthSnapshots": [],
              "expenseAttachments": [],
              "creditCardStatementPayments": [],
              "expenseAdjustments": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString<BudgetTrackerExportDto>(jsonString)

        assertEquals(9, decoded.schemaVersion)
        assertEquals(50_000L, decoded.subscriptionPriceHistory.single().amount)
        assertNull(decoded.subscriptionPriceHistory.single().originalCurrency)
        assertNull(decoded.loans.single().closedAt)
        assertEquals(emptyList<LoanPaymentDto>(), decoded.loanPayments)
    }

    @Test
    fun loanPayments_roundTripInCurrentSchema() {
        val dto = BudgetTrackerExportDto(
            schemaVersion = 12,
            exportedAt = "2026-07-10T09:00:00Z",
            salaryRules = emptyList(),
            savingGoals = emptyList(),
            categories = emptyList(),
            paymentAccounts = emptyList(),
            expenses = emptyList(),
            installmentGroups = emptyList(),
            loans = emptyList(),
            loanPayments = listOf(LoanPaymentDto(1L, 10L, "2026-07", 125_000L, 20_279L)),
            subscriptions = emptyList(),
            subscriptionPriceHistory = emptyList(),
            categoryBudgets = emptyList(),
            expenseTemplates = emptyList(),
            debtRecords = emptyList(),
            netWorthSnapshots = emptyList()
        )

        val decoded = json.decodeFromString<BudgetTrackerExportDto>(json.encodeToString(dto))

        assertEquals(dto.loanPayments, decoded.loanPayments)
    }
}
