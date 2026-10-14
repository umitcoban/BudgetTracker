package com.umit.budgettracker.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun testFormat() {
        // Since Locale might vary in test environment, we just check if it contains the numbers and currency symbol logic
        // But for deterministic check:
        val formatted = MoneyFormatter.format(10000000L) // 100,000.00 TL
        // Depending on system locale it might be ₺100.000,00 or 100.000,00 ₺
        // We know we used Locale.forLanguageTag("tr-TR")
        // Note: In some JVMs ₺ might be replaced by TRY or vice versa.
        assert(formatted.contains("100.000,00"))
    }

    @Test
    fun testParse() {
        assertEquals(10000000L, MoneyFormatter.parse("100000"))
        assertEquals(10000050L, MoneyFormatter.parse("100000,50"))
        assertEquals(10000050L, MoneyFormatter.parse("100000.50"))
        // Exact, no floating point: these used to come out one kuruş short or 1000× too small.
        assertEquals(1999L, MoneyFormatter.parse("19,99"))
        assertEquals(29L, MoneyFormatter.parse("0,29"))
        assertEquals(125000L, MoneyFormatter.parse("1.250"))
        assertEquals(125050L, MoneyFormatter.parse("1.250,50"))
    }
}
