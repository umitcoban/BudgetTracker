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

    @Test
    fun salaryRuleWithoutPayDay_fromSchema13_decodesAsNull() {
        val schema13Rule = """{"id":1,"amount":9000000,"effectiveStartMonth":"2026-01","note":null}"""

        val decoded = json.decodeFromString<SalaryRuleDto>(schema13Rule)

        assertEquals(null, decoded.payDay)
    }

    @Test
    fun salaryRulePayDay_roundTripsInSchema14() {
        val dto = SalaryRuleDto(id = 1L, amount = 9_000_000L, effectiveStartMonth = "2026-01", note = null, payDay = 15)

        val decoded = json.decodeFromString<SalaryRuleDto>(json.encodeToString(dto))

        assertEquals(15, decoded.payDay)
        assertEquals(dto, decoded)
    }
}

class JsonSchema15Test {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun schema14Export_withoutPlanningSections_decodesWithEmptyDefaults() {
        val schema14 = """
            {
              "appName": "BudgetTracker",
              "schemaVersion": 14,
              "exportedAt": "2026-09-01T10:00:00Z",
              "salaryRules": [],
              "savingGoals": [],
              "categories": [],
              "paymentAccounts": [],
              "expenses": [],
              "installmentGroups": [],
              "loans": [],
              "subscriptions": [],
              "subscriptionPriceHistory": [],
              "categoryBudgets": [],
              "expenseTemplates": [],
              "debtRecords": [],
              "netWorthSnapshots": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString<BudgetTrackerExportDto>(schema14)

        assertEquals(14, decoded.schemaVersion)
        assertEquals(emptyList<InflationRateDto>(), decoded.inflationRates)
        assertEquals(emptyList<AssetDto>(), decoded.assets)
        assertEquals(emptyList<SavingsGoalDto>(), decoded.savingsGoals)
        assertEquals(emptyList<String>(), decoded.dismissedSuggestions)
    }

    @Test
    fun schema15Sections_roundTripWithScaledValues() {
        val dto = BudgetTrackerExportDto(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = "2026-10-13T10:00:00Z",
            salaryRules = emptyList(),
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
            netWorthSnapshots = emptyList(),
            inflationRates = listOf(InflationRateDto("2026-09", 184, 2973, null, "TCMB")),
            assets = listOf(
                AssetDto(1, "GTL", "FUND", "FUND:GTL", 51_628_000_000L, null, 649_984L, "Para piyasası", isEmergencyFund = true),
                AssetDto(2, "Vadeli", "DEPOSIT", null, 1_000_000L, 12_000_000L, 12_000_000L, null, 4500, 20_740L, 20_772L)
            ),
            marketPrices = listOf(MarketPriceDto("FUND:GTL", "GTL", 141_840L, null, "İçe aktarma", 1L)),
            priceAlerts = listOf(PriceAlertDto(1, "GOLD:GRAM", "ABOVE", 7_000_000_000L)),
            savingsGoals = listOf(SavingsGoalDto(1, "Tatil", 6_000_000L, "2027-08", 1, "beach", null)),
            goalContributions = listOf(GoalContributionDto(1, 1, 2_880_000L, 20_739L)),
            plannedExpenses = listOf(PlannedExpenseDto(1, "Kasko", 1_800_000L, 20_892L, isYearly = true)),
            merchantRules = listOf(MerchantRuleDto(1, "a101", 1, 4)),
            dismissedSuggestions = listOf("salonu spor")
        )

        val decoded = json.decodeFromString<BudgetTrackerExportDto>(json.encodeToString(dto))

        assertEquals(dto, decoded)
        assertEquals(15, decoded.schemaVersion)
    }
}
