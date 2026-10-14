package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.calculator.FundMovementRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Broker layouts copied from real downloads and screenshots, filled with invented amounts and an
 * invented branch / account number.
 */
class FundMovementParserTest {
    /** Garanti "Fon Hareketleri" as [XlsReader.toText] returns it: tab-separated, empty cells kept. */
    private val history = listOf(
        "Ad Soyad/Unvan\tTEST KİŞİ",
        "Başlangıç Tarihi\t01/04/2026",
        "İndirme Tarihi\t01/10/2026 10:00",
        "Tarih\tFon\tİşlem Detayı\tİşlem Tipi\tAdet\tBirim Fiyatı\tTutar",
        "02/06/2026\tGTL-GPY 1. PARA PİYASASI (TL)  FONU\t                    \tALIŞ\t10000\t0,125000 TL\t-1250,00 TL",
        "02/06/2026\tGNP-GP NEMA PARA PİY (TL) FONU\tNEMA                \tALIŞ\t1000\t1,300000 TL\t-1300,00 TL",
        "02/06/2026\tGNP-GP NEMA PARA PİY (TL) FONU\tNEMA                \tALIŞ\t1000\t1,300000 TL\t-1300,00 TL",
        "03/06/2026\tGNP-GP NEMA PARA PİY (TL) FONU\tNEMA                \tSATIŞ\t1500\t1,310000 TL\t+1965,00 TL",
        "03/06/2026\tGPI-GPY 2.DEĞİŞKEN FON(SMART DENGELİ)\t                    \tALIŞ\t50\t17,000000 TL\t-850,00 TL",
        "03/06/2026\tYAY-YAPI KREDİ PORTFÖY YABANCI TEKNOLOJ\t                    \tALIŞ\t1\t1700,000000 TL\t-1700,00 TL",
        "01/07/2026\tGTL-GPY 1. PARA PİYASASI (TL)  FONU\t                    \tALIŞ\t5000\t0,130000 TL\t-650,00 TL",
        "01/09/2026\tMAC-MARMARA CAPITAL PORTFÖY HİSSE SENED\t                    \tSATIŞ\t100\t0,700000 TL\t+70,00 TL"
    ).joinToString("\n")

    @Test
    fun parse_readsEveryRowAndKeepsIdenticalExecutions() {
        val result = FundMovementParser.parse(history)

        assertEquals(8, result.movements.size)
        assertTrue(result.unreadLines.isEmpty())
        // Two equal GNP buys on one day are two transactions, not a duplicate.
        assertEquals(2, result.movements.count { it.code == "GNP" && it.isBuy })
        val gpi = result.movements.single { it.code == "GPI" }
        // "2.DEĞİŞKEN" in the title must not be read as a quantity.
        assertEquals(50_000_000L, gpi.quantity)
        assertEquals(17_000_000L, gpi.price)
        assertEquals(85_000L, gpi.amount)
        assertEquals("GPY 2.DEĞİŞKEN FON(SMART DENGELİ)", gpi.name)
        assertEquals(LocalDate.of(2026, 6, 3), gpi.date)
        assertFalse(result.movements.single { it.code == "MAC" }.isBuy)
    }

    @Test
    fun positions_useAverageCostAndBookRealisedProfit() {
        val positions = FundMovementRules.positions(FundMovementParser.parse(history).movements).associateBy { it.code }

        val gtl = positions.getValue("GTL")
        assertEquals(15_000_000_000L, gtl.quantity)
        assertEquals(190_000L, gtl.cost)
        assertEquals(130_000L, gtl.lastPrice)
        assertEquals(LocalDate.of(2026, 7, 1), gtl.lastDate)

        // 2000 units for 2600,00; selling 1500 removes 1950,00 of cost for 1965,00 → +15,00.
        val gnp = positions.getValue("GNP")
        assertEquals(500_000_000L, gnp.quantity)
        assertEquals(65_000L, gnp.cost)
        assertEquals(1_500L, gnp.realizedProfit)
        assertFalse(gnp.incompleteHistory)

        // A sale with no purchase in the file: history starts too late.
        val mac = positions.getValue("MAC")
        assertEquals(0L, mac.quantity)
        assertTrue(mac.incompleteHistory)
    }

    @Test
    fun toRows_valuesAtLastPriceAndMarksSoldFunds() {
        val rows = FundMovementRules.toRows(FundMovementRules.positions(FundMovementParser.parse(history).movements)).associateBy { it.code }

        val gtl = rows.getValue("GTL")
        assertEquals(195_000L, gtl.value) // 15.000 × 0,13
        assertEquals(190_000L, gtl.cost)
        assertEquals(5_000L, gtl.profit)
        assertEquals(LocalDate.of(2026, 7, 1), gtl.priceDate)
        assertNull(rows.getValue("MAC").cost)
        assertEquals(0L, rows.getValue("MAC").quantity)
    }

    @Test
    fun parse_ignoresPortfolioTables() {
        assertTrue(FundMovementParser.parse("GTA  2.415  3.777,39  1,564138  +279,13").movements.isEmpty())
    }
}

class FundScreenshotTest {
    private fun line(text: String, left: Int, top: Int, height: Int = 16) = OcrLine(text, left, top, left + text.length * 7, top + height)

    /**
     * Two rows of Garanti's web portfolio table: a wrapped title on the left, stacked
     * "satılabilir / toplam" numbers, and the price centred vertically in the row.
     */
    private val twoRows = listOf(
        line("Fon Adı", 60, 27), line("Hesap", 280, 27), line("Fiyat", 880, 27),
        line("GTA-GPY ALTIN", 17, 85), line("FONU", 15, 101),
        line("123 - 4567890 ŞUBE / İLÇE", 210, 93), line("8,6228", 460, 93),
        line("2.000", 580, 85), line("2.000", 580, 101),
        line("3.200,00 TL", 650, 85), line("3.200,00 TL", 650, 101),
        line("200,00 TL", 760, 85), line("% 6,67", 770, 101),
        line("1,600000 TL", 880, 93), line("Al", 1000, 93), line("Sat", 1100, 93),
        line("GTL-GPY 1. PARA", 17, 155), line("PIYASASI (TL) FONU", 17, 173),
        line("123 - 4567890 ŞUBE / İLÇE", 210, 163), line("33,7778", 460, 163),
        line("100.000", 580, 155), line("100.000", 580, 171),
        line("14.000,00 TL", 650, 155), line("14.000,00 TL", 650, 171),
        line("700,00 TL", 760, 155), line("% 5,26", 770, 171),
        line("0,140000 TL", 880, 163)
    )

    @Test
    fun anchoredRows_keepEachFundRowTogether() {
        val rows = OcrLayout.anchoredRows(twoRows)

        assertEquals(2, rows.size)
        assertTrue(rows[0].startsWith("GTA-GPY ALTIN"))
        assertTrue(rows[1].startsWith("GTL-GPY 1. PARA"))
        assertFalse(rows[0].contains("GTL"))
    }

    @Test
    fun anchoredRows_parseWithoutAccountNumberOrTitleDigits() {
        val result = FundPortfolioParser.parse(OcrLayout.anchoredRows(twoRows).joinToString("\n"))

        val gta = result.rows.single { it.code == "GTA" }
        assertEquals(2_000_000_000L, gta.quantity)
        assertEquals(1_600_000L, gta.price)
        assertEquals(320_000L, gta.value)
        assertEquals(20_000L, gta.profit)
        // "1. PARA" and the duplicated value must not turn into "1 × 14.000,00".
        val gtl = result.rows.single { it.code == "GTL" }
        assertEquals(100_000_000_000L, gtl.quantity)
        assertEquals(140_000L, gtl.price)
        assertEquals(1_330_000L, gtl.cost)
    }

    @Test
    fun anchoredRows_endARowAtAnOcrMangledCode() {
        // "IIH-..." read as "IH-..." must still close the previous row.
        val lines = listOf(
            line("KZL-KUVEYT TÜRK", 17, 85), line("100", 580, 93), line("2.500,00 TL", 650, 93), line("25,000000 TL", 880, 93),
            line("IH-İSTANBUL", 15, 155), line("40", 580, 163), line("1.200,00 TL", 650, 163), line("30,000000 TL", 880, 163)
        )
        val rows = OcrLayout.anchoredRows(lines)
        assertEquals(2, rows.size)
        assertEquals(100_000_000L, FundPortfolioParser.parse(rows[0]).rows.single().quantity)
    }

    @Test
    fun anchoredRows_ignoreTitleWrapsThatLookLikeCodes() {
        assertFalse(OcrLayout.isFundAnchor("PIY (TL) FONU"))
        assertFalse(OcrLayout.isFundAnchor("SEN. F"))
        assertFalse(OcrLayout.isFundAnchor("759 - 4567890"))
        assertTrue(OcrLayout.isFundAnchor("GTA"))
        assertTrue(OcrLayout.isFundAnchor("GTA  2.415  3.777,39"))
    }

    @Test
    fun parse_repairsCommonOcrSlips() {
        // Thousands dot read as a comma, a dropped "1" for a one-unit fund, a price without its comma.
        val text = """
            GNP-GP NEMA  2,500  4.000,00 TL  0,00 TL  5,00 TL  1,600000 TL
            YAY-YAPI KREDİ  4,1234  2.000,00 TL  2.000,00 TL  150,00 TL  1.999,998000 TL
            KZL-KUVEYT  8,1111  100  100  2.500,00 TL  2.500,00 TL  200,00 TL  25000000 TL
        """.trimIndent()

        val rows = FundPortfolioParser.parse(text).rows.associateBy { it.code }

        assertEquals(2_500_000_000L, rows.getValue("GNP").quantity)
        assertEquals(500L, rows.getValue("GNP").profit)
        assertEquals(1_000_000L, rows.getValue("YAY").quantity)
        assertEquals(15_000L, rows.getValue("YAY").profit)
        assertEquals(25_000_000L, rows.getValue("KZL").price)
    }
}
