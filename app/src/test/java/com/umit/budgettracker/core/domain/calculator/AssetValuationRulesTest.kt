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
        GTA  2.415  3.777,39  1,564138  +279,13  %7,98
        GTL  51.628  7.322,92  0,141840  +823,08  %12,66
        GUH  7.985  3.907,62  0,489370  +408,10  %11,66
        GPI  108  2.031,97  18,814507  +143,28  %7,59
        YAY  1  1.986,61  1.986,607568  +228,01  %12,97
        AFT  3.683  3.807,91  1,033914  +308,64  %8,82
        KZL  137  3.784,60  27,624846  +300,63  %8,63
        IIH  39  1.218,36  31,240080  −22,80  −%1,84
        PPK  1.659  3.935,24  2,372053  +437,90  %12,52
    """.trimIndent()

    @Test
    fun valueOf_roundsHalfUpToKurusWithSixDecimalPrices() {
        // 2415 × 1,564138 = 3777,393270 → 3777,39
        assertEquals(377_739L, AssetValuationRules.valueOf(2_415L * Q, 1_564_138L))
        // 51628 × 0,141840 = 7322,91552 → 7322,92
        assertEquals(732_292L, AssetValuationRules.valueOf(51_628L * Q, 141_840L))
        // 1 × 1986,607568 → 1986,61
        assertEquals(198_661L, AssetValuationRules.valueOf(1L * Q, 1_986_607_568L))
        // Fractional grams: 2,5 gr × 6.592,00 = 16.480,00
        assertEquals(1_648_000L, AssetValuationRules.valueOf(2_500_000L, 6_592_000_000L))
    }

    @Test
    fun valueOf_doesNotOverflowForLargeHoldings() {
        // 51.628 units × 1.986,607568 ₺ would overflow a Long product of the raw scaled values.
        assertEquals(10_256_457_552L, AssetValuationRules.valueOf(51_628L * Q, 1_986_607_568L))
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

        assertEquals(3_177_262L, summary.totalValue)       // 31.772,62 ₺
        assertEquals(290_597L, summary.totalProfit)        // +2.905,97 ₺
        assertEquals(2_886_665L, summary.totalCost)        // 28.866,65 ₺
        assertEquals(1007, summary.totalProfitBp)          // %10,07
        val iih = summary.valuations.first { it.asset.name == "IIH" }
        assertEquals(-2_280L, iih.profit)
        assertEquals(-184, iih.profitBp)
        assertEquals(798, summary.valuations.first { it.asset.name == "GTA" }.profitBp)
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
