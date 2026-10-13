package com.umit.budgettracker.core.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Layouts of real Garanti BBVA, QNB and Yapı Kredi exports (XLS and PDF text), filled with
 * invented data. The `.xls` fixtures are genuine BIFF8 files read through [XlsReader].
 */
class BankStatementFormatsTest {
    private fun xls(name: String): String {
        val bytes = requireNotNull(javaClass.classLoader?.getResource("statements/$name")) { "missing $name" }.readBytes()
        assertTrue(XlsReader.isXls(bytes))
        return XlsReader.toText(XlsReader.readFirstSheet(bytes))
    }

    private fun StatementParseResult.spending() = transactions.filter { !it.isCredit }.sumOf { it.amount }
    private fun StatementParseResult.credits() = transactions.filter { it.isCredit }.sumOf { it.amount }

    @Test
    fun xlsReader_keepsColumnPositionsAndTurkishText() {
        val rows = XlsReader.readFirstSheet(javaClass.classLoader!!.getResource("statements/garanti_ekstre.xls")!!.readBytes())
        val header = rows.first { it.contains("Tarih") }
        assertEquals(listOf("Tarih", "İşlem", "Etiket", "Bonus", "Tutar(TL)"), header)
        val bonusOnly = rows.first { it.getOrNull(1)?.startsWith("GİYİM BONUS") == true }
        assertEquals("", bonusOnly[4])
    }

    @Test
    fun garantiXls_negativeIsSpendingBonusRowsSkippedCardsSeparated() {
        val result = BankStatementParser.parse(xls("garanti_ekstre.xls"))

        assertEquals(listOf("1111", "2222"), result.cardHints)
        assertEquals(10, result.transactions.size) // bonus-only campaign row skipped
        val payment = result.transactions.single { it.description.startsWith("Cep Şube") }
        assertTrue(payment.isCredit)
        val refund = result.transactions.single { it.amount == 86_500L }
        assertTrue(refund.isCredit)
        val market = result.transactions.first()
        assertEquals(LocalDate.of(2026, 10, 12), market.date)
        assertEquals(123_450L, market.amount)
        assertFalse(market.isCredit)
        assertEquals("Market", market.bankCategory)
        assertEquals("1111", market.cardHint)
        val installment = result.transactions.single { it.installment != null }
        assertEquals("3/3", installment.installment)
        assertEquals("HEPSİPAY-HEP*HEPSİBURADA İSTANBUL", installment.description)
        assertEquals("2222", result.transactions.last().cardHint)
        assertEquals(123_450L + 45_000L + 71_500L + 36_369L + 64_999L + 23_999L + 70_500L + 179_999L, result.spending())
    }

    @Test
    fun qnbXls_mergedHeaderOffsetPositiveSpendingSpacedMinusCredits() {
        val result = BankStatementParser.parse(xls("qnb_donem_ici.xls"))

        assertEquals(5, result.transactions.size)
        assertEquals(listOf("4444"), result.cardHints)
        assertEquals(48_754L + 65_000L + 103_150L, result.spending())
        assertEquals(50_000L + 48_754L, result.credits())
        val contabo = result.transactions.first { it.description == "CONTABO* PAYMENT" }
        assertEquals("8,18 EUR", contabo.foreignAmount)
        assertEquals("1/1", result.transactions.single { it.description.startsWith("Uber") }.installment)
        assertTrue(result.unreadLines.isEmpty())
    }

    @Test
    fun yapiKrediXls_headerBlockSkippedPlusIsPaymentSectorKept() {
        val result = BankStatementParser.parse(xls("yapikredi_hesap_ozeti.xls"))

        assertEquals(5, result.transactions.size)
        assertEquals(listOf("5555"), result.cardHints)
        assertEquals(23_999L + 35_400L + 30_000L + 28_100L, result.spending()) // = "Hesap Özeti Tutarı" 1.174,99 ₺
        assertEquals(100_000L, result.credits())
        assertEquals("YEMEK", result.transactions.single { it.description.startsWith("UBER") }.bankCategory)
    }

    @Test
    fun garantiPdfText_trailingPlusIsCreditInstalmentAndBonusColumnsRemoved() {
        val text = """
            Kart Numarası 5549 60** **** 1111
            Hesap Kesim Tarihi 12 Ekim 2026
            Son Ödeme Tarihi 22 Ekim 2026
            İşlem Tarihi Dönem İçi İşlemler Kalan Borç / Taksit Bonus (TL) Tutar (TL)
            ÖNCEKİ DÖNEMDEN DEVİR EDİLEN TUTAR 9.000,00
            12 Eylül 2026 ÖDEMENİZ İÇİN TEŞEKKÜR EDERİZ 9.000,00+
            02 Ekim 2026 BONUS MARKET KAMPANYASI 200,00
            16 Eylül 2026 BONUS BEDAVA ALIŞVERİŞ 22,27-
            23 Eylül 2026 9908 H669 A101 EMIRGAN ME 0,03 254,00
            04 Eylül 2026 TRENDYOL.COM 1.000,00x9=9.000,00 6.Taksit 1.000,00
            16 Eylül 2026 HEPSİPAY-HEP*HEPSİBURADA 0,09- 865,00+
            Süpermarket
            06 Ekim 2026 ÇEREZCİLER GIDA 887,90
            BONUS SANAL KART BİLGİLERİNİZ: TEST / 5440 78** **** 2222
            24 Eylül 2026 CONTABO* PAYMENT 91,80 EUR 5.293,97
            12 Eylül 2026 Google ChatGPT 649,99 TL 649,99
            Toplam 320,86 8.020,86
            EKSTRE ÖZETİ Bir sonraki hesap kesiminiz 13 Kasım 2026 Cuma
        """.trimIndent()

        val result = BankStatementParser.parse(text)

        assertEquals(listOf("1111", "2222"), result.cardHints)
        assertEquals(7, result.transactions.size)
        assertEquals(900_000L + 86_500L, result.credits())
        assertEquals(25_400L + 100_000L + 88_790L + 529_397L + 64_999L, result.spending())
        assertEquals("9908 H669 A101 EMIRGAN ME", result.transactions.single { it.amount == 25_400L }.description)
        val trendyol = result.transactions.single { it.description == "TRENDYOL.COM" }
        assertEquals("6/9", trendyol.installment)
        val contabo = result.transactions.single { it.description == "CONTABO* PAYMENT" }
        assertEquals("91,80 EUR", contabo.foreignAmount)
        assertEquals("2222", contabo.cardHint)
        assertEquals("Google ChatGPT", result.transactions.single { it.amount == 64_999L }.description)
    }

    @Test
    fun qnbAndYapiKrediPdfText_signsAndCardColumns() {
        val qnb = BankStatementParser.parse(
            """
            13/10/2026 tarihli son 4 hanesi 3333 olan kartınıza ait Dönem İçi Hareketler
            Son Kesilen Ekstre Borcu 500,00 TL
            14/09/2026 Ödeme - Tesekkür Ederiz - - 500,00 TL
            SANAL KART - 4143 **** **** 4444
            17/09/2026 Uber Eats Yemek İSTANBUL TRTR 650,00 TL 1/1
            25/09/2026 CONTABO* PAYMENT (8,18 EUR) - 487,54 TL
            Toplam 162,46 TL
            """.trimIndent()
        )
        assertEquals(3, qnb.transactions.size)
        assertEquals(65_000L, qnb.spending())
        assertEquals(50_000L + 48_754L, qnb.credits())
        assertTrue(qnb.unreadLines.isEmpty())

        val yk = BankStatementParser.parse(
            """
            Kart No : 540061******5555
            Son Ödeme Tarihi : 21/10/2026
            Bir Sonraki Hesap Kesim Tarihi : 10/11/2026
            - Önceki Dönem TL Hesap Özeti Borcu 5400 61** **** 5555 1.000,00 TL -
            11/09/2026 ÖDEME-İNTERNET BANKACILIĞI 5400 61** **** 5555 +1.000,00 TL -
            15/09/2026 UBER EATS YEMEK ISTANBUL TR 5400 61** **** 5555 354,00 TL 7
            """.trimIndent()
        )
        assertEquals(2, yk.transactions.size)
        assertEquals("UBER EATS YEMEK ISTANBUL TR", yk.transactions[1].description)
        assertEquals(35_400L, yk.spending())
        assertEquals(100_000L, yk.credits())
        assertEquals(listOf("5555"), yk.cardHints)
    }

    @Test
    fun bankCategoryLabels_mapToUserCategories() {
        val categories = listOf(1L to "Market", 2L to "Yemek", 3L to "Giyim", 4L to "Sağlık", 5L to "Fatura", 6L to "Teknoloji")
        assertEquals(2L, BankCategoryMap.suggest("Yeme / İçme", categories))
        assertEquals(4L, BankCategoryMap.suggest("SAGLIK / SAGLIK URUNLERI / KOZMETIK", categories))
        assertEquals(5L, BankCategoryMap.suggest("Kurum Ödemesi", categories))
        assertEquals(3L, BankCategoryMap.suggest("Giyim / Aksesuar", categories))
        assertEquals(6L, BankCategoryMap.suggest("Elektronik", categories))
        assertEquals(null, BankCategoryMap.suggest("DIGER", categories))
    }
}
