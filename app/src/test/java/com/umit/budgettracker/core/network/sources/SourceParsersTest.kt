package com.umit.budgettracker.core.network.sources

import com.umit.budgettracker.core.domain.model.PublicHoliday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Real responses captured on 2026-10-13 (see docs/DATA_SOURCES.md); no personal data. */
class SourceParsersTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("datasources/$name")) { "missing fixture $name" }.readText()

    @Test
    fun tcmb_parsesForexBuyingPerUnit() {
        val xml = fixture("tcmb_today.xml")
        val prices = SourceParsers.parseTcmb(xml, fetchedAt = 1L).associateBy { it.code }

        assertEquals(49_279_400L, prices.getValue("FX:USD").price) // 49,2794
        assertTrue(prices.size >= 15)
        assertEquals("TCMB", prices.getValue("FX:USD").source)
        assertEquals("13.10.2026", SourceParsers.tcmbBulletinDate(xml))
        // JPY is quoted per 100 units in the bulletin and must be per unit here.
        prices["FX:JPY"]?.let { assertTrue(it.price < 1_000_000L) }
    }

    @Test
    fun truncgil_parsesGoldCoinsAndConvertsOunce() {
        val prices = SourceParsers.parseTruncgil(fixture("truncgil_today.json"), fetchedAt = 1L).associateBy { it.code }

        assertEquals(6_583_860_000L, prices.getValue("GOLD:GRAM").price)        // "6.583,86"
        assertEquals(10_547_410_000L, prices.getValue("GOLD:CEYREK").price)     // "10.547,41"
        assertEquals(43_726_000_000L, prices.getValue("GOLD:CUMHURIYET").price) // "43.726,00"
        assertEquals(49_355_600L, prices.getValue("FX:USD").price)
        // ons $4.148,34 × 49,3556
        assertEquals(
            BigDecimal("4148.34").multiply(BigDecimal("49.3556")).movePointRight(6).longValueExact(),
            prices.getValue("GOLD:ONS").price
        )
    }

    @Test
    fun brokenOrChangedFormats_yieldEmptyInsteadOfCrashing() {
        assertTrue(SourceParsers.parseTcmb("<html>bakım</html>", 1L).isEmpty())
        assertTrue(SourceParsers.parseTruncgil("{\"Update_Date\":\"x\"}", 1L).isEmpty())
        assertTrue(SourceParsers.parseTruncgil("", 1L).isEmpty())
        assertTrue(SourceParsers.parseNager("not json").isEmpty())
        assertTrue(SourceParsers.parseTcmbCpi("<p>no table</p>").isEmpty())
    }

    @Test
    fun goldApi_gramFallbackMatchesTruncgilWithinOnePercent() {
        val ounce = SourceParsers.parseGoldApiOunceUsd(fixture("goldapi_xau.json"))
        assertEquals(BigDecimal("4162.5"), ounce)
        val gram = SourceParsers.gramGoldFromOunce(ounce!!, BigDecimal("49.2794"), 1L)
        // 4162,5 × 49,2794 / 31,1034768 ≈ 6.595,0 ₺; Truncgil quoted 6.583,86.
        assertTrue(gram.price in 6_520_000_000L..6_660_000_000L)
    }

    @Test
    fun nager_parsesTurkishHolidays() {
        val holidays = SourceParsers.parseNager(fixture("nager_tr_2026.json"))
        assertTrue(holidays.size >= 10)
        assertTrue(PublicHoliday(LocalDate.of(2026, 1, 1), "Yılbaşı") in holidays)
        assertNotNull(holidays.firstOrNull { it.date == LocalDate.of(2026, 3, 20) })
    }

    @Test
    fun tcmbCpi_readsMonthAnnualAndMonthlyColumns() {
        val rates = SourceParsers.parseTcmbCpi(fixture("tcmb_cpi.html")).associateBy { it.month }

        val september = rates.getValue(YearMonth.of(2026, 9))
        assertEquals(184, september.monthlyRateBp)
        assertEquals(2973, september.annualRateBp)
        assertEquals(484, rates.getValue(YearMonth.of(2026, 1)).monthlyRateBp)
        assertEquals(255, rates.getValue(YearMonth.of(2025, 10)).monthlyRateBp)
    }
}
