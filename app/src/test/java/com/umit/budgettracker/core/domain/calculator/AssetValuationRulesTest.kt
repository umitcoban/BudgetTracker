package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.parsing.FundPortfolioParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AssetValuationRulesTest {
    private val today = LocalDate.of(2026, 10, 13)

    /** The portfolio from the roadmap (no account / customer numbers). */
    private val portfolioTable = """
        Fon Kodu  Adet  Güncel Tutar  Fiyat  Kâr/Zarar
        GTA  2.010  3.115,75  1,550123  +201,15  %6,90
        GTL  50.123  7.018,07  0,140017  +700,40  %11,09
        GUH  8.012  3.847,45  0,480211  +401,22  %11,64
        GPI  108  2.063,29  19,104503  +150,10  %7,85
        YAY  1  1.900,50  1.900,503121  +200,33  %11,78
        AFT  3.510  3.553,33  1,012345  +300,25  %9,23
        KZL  137  3.713,17  27,103456  +301,40  %8,83
        IIH  39  1.189,98  30,512347  −20,15  −%1,84
        PPK  1.612  3.870,79  2,401234  +440,60  %12,84
    """.trimIndent()

    @Test
    fun valueOf_roundsHalfUpToKurusWithSixDecimalPrices() {
        // 2010 × 1,550123 = 3115,747230 → 3115,75
        assertEquals(311_575L, AssetValuationRules.valueOf(2_010L * Q, 1_550_123L))
        // 50123 × 0,140017 = 7018,072091 → 7018,07
        assertEquals(701_807L, AssetValuationRules.valueOf(50_123L * Q, 140_017L))
        // 1 × 1900,503121 → 1900,50
        assertEquals(190_050L, AssetValuationRules.valueOf(1L * Q, 1_900_503_121L))
        // Fractional grams: 2,5 gr × 6.592,00 = 16.480,00
        assertEquals(1_648_000L, AssetValuationRules.valueOf(2_500_000L, 6_592_000_000L))
    }

    @Test
    fun valueOf_doesNotOverflowForLargeHoldings() {
        // 50.123 units × 1.900,503121 ₺ would overflow a Long product of the raw scaled values.
        assertEquals(9_525_891_793L, AssetValuationRules.valueOf(50_123L * Q, 1_900_503_121L))
    }

    @Test
    fun portfolioFixture_matchesBrokerTotals() {
        val rows = FundPortfolioParser.parse(portfolioTable).rows
        assertEquals(9, rows.size)
        val assets = rows.mapIndexed { index, row ->
            Asset(
                id = index + 1L,
                name = row.code,
                type = AssetType.FUND,
                priceCode = "FUND:${row.code}",
                quantity = row.quantity,
                manualValue = null,
                costAmount = row.cost,
                allocationTag = null,
                isEmergencyFund = false,
                isActive = true,
                note = null
            )
        }
        val prices = rows.map { MarketPrice("FUND:${it.code}", it.code, it.price, null, "test", 0L) }

        val summary = AssetValuationRules.summarize(assets, prices, today)

        assertEquals(3_027_233L, summary.totalValue)       // 30.272,33 ₺
        assertEquals(267_530L, summary.totalProfit)        // +2.675,30 ₺
        assertEquals(2_759_703L, summary.totalCost)        // 27.597,03 ₺
        assertEquals(969, summary.totalProfitBp)          // %9,69
        val iih = summary.valuations.first { it.asset.name == "IIH" }
        assertEquals(-2_015L, iih.profit)
        assertEquals(-167, iih.profitBp)
        assertEquals(690, summary.valuations.first { it.asset.name == "GTA" }.profitBp)
    }

    @Test
    fun deposit_accruesSimpleGrossInterestUntilMaturity() {
        val deposit = Asset(
            id = 1, name = "Vadeli", type = AssetType.DEPOSIT, priceCode = null, quantity = Q,
            manualValue = 12_000_000L, costAmount = null, allocationTag = null, interestRateBp = 4500,
            startDate = today.minusDays(10), maturityDate = today.plusDays(22),
            isEmergencyFund = true, isActive = true, note = null
        )

        val valuation = AssetValuationRules.valuate(deposit, emptyMap(), today)

        // 120.000 × %45 × 10 / 365 = 1.479,45
        assertEquals(147_945L, valuation.accruedInterest)
        assertEquals(12_147_945L, valuation.value)
        // 32 days → 4.734,24
        assertEquals(473_424L, valuation.interestAtMaturity)
        assertEquals(22L, valuation.daysToMaturity)
    }

    @Test
    fun pricedAssetWithoutPrice_isWorthZeroAndHasNoProfit() {
        val gold = Asset(1, "Altın", AssetType.GOLD, "GOLD:GRAM", 10 * Q, null, null, null, isEmergencyFund = false, isActive = true, note = null)
        val valuation = AssetValuationRules.valuate(gold, emptyMap(), today)
        assertEquals(0L, valuation.value)
        assertNull(valuation.profitBp)
    }

    @Test
    fun emergencyFundAndAllocation_onlyCountActiveAssets() {
        val cash = Asset(1, "Vadesiz", AssetType.CASH, null, Q, 5_000_000L, null, "Nakit", isEmergencyFund = true, isActive = true, note = null)
        val old = Asset(2, "Eski", AssetType.CASH, null, Q, 9_000_000L, null, "Nakit", isEmergencyFund = true, isActive = false, note = null)
        val summary = AssetValuationRules.summarize(listOf(cash, old), emptyList(), today)
        assertEquals(5_000_000L, summary.totalValue)
        assertEquals(5_000_000L, summary.emergencyFundValue)
        assertEquals(listOf("Nakit" to 5_000_000L), summary.allocation)
    }

    private companion object {
        const val Q = AssetValuationRules.QUANTITY_SCALE
    }
}
