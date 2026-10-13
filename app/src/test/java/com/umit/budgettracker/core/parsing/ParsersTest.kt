package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.MerchantRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TurkishNumbersTest {
    @Test
    fun parseDecimal_handlesTurkishAndPlainFormats() {
        assertEquals(BigDecimal("3115.75"), TurkishNumbers.parseDecimal("3.115,75"))
        assertEquals(BigDecimal("1.550123"), TurkishNumbers.parseDecimal("1,550123"))
        assertEquals(BigDecimal("2010"), TurkishNumbers.parseDecimal("2.010"))
        assertEquals(BigDecimal("1900.503121"), TurkishNumbers.parseDecimal("1.900,503121"))
        assertEquals(BigDecimal("49.3681"), TurkishNumbers.parseDecimal("49.3681"))
        assertEquals(BigDecimal("1234567.89"), TurkishNumbers.parseDecimal("1.234.567,89"))
        assertEquals(BigDecimal("-20.15"), TurkishNumbers.parseDecimal("−20,15"))
        assertEquals(BigDecimal("-20.15"), TurkishNumbers.parseDecimal("(20,15)"))
        assertEquals(BigDecimal("201.15"), TurkishNumbers.parseDecimal("+201,15 TL"))
        assertEquals(BigDecimal("6591.46"), TurkishNumbers.parseDecimal("₺6.591,46"))
        assertNull(TurkishNumbers.parseDecimal("12.10.2026"))
        assertNull(TurkishNumbers.parseDecimal("abc"))
    }

    @Test
    fun parseMinorAndScaled_areExact() {
        assertEquals(125_050L, TurkishNumbers.parseMinor("1.250,50"))
        assertEquals(1_250L, TurkishNumbers.parseMinor("12,5"))
        assertEquals(140_017L, TurkishNumbers.parseScaled("0,140017", 1_000_000L))
        // Extra precision rounds half-up instead of truncating.
        assertEquals(2L, TurkishNumbers.parseMinor("0,015"))
    }

    @Test
    fun findDate_readsTurkishOrderIsoAndMonthNames() {
        assertEquals(LocalDate.of(2026, 10, 12), TurkishNumbers.findDate("12.10.2026 MIGROS")?.date)
        assertEquals(LocalDate.of(2026, 3, 5), TurkishNumbers.findDate("05/03/26 kart")?.date)
        assertEquals(LocalDate.of(2026, 10, 1), TurkishNumbers.findDate("2026-10-01")?.date)
        assertEquals(LocalDate.of(2026, 10, 12), TurkishNumbers.findDate("12 Ekim 2026 Pazartesi")?.date)
        assertNull(TurkishNumbers.findDate("32.13.2026"))
    }
}

class FundPortfolioParserTest {
    @Test
    fun parse_readsOcrStyleRowsWithDifferentColumnOrders() {
        val text = """
            GTL 50.123 0,140017 7.018,07 700,40
            Fon: YAY  Adet 1  Fiyat 1.900,503121  Tutar 1.900,50  Kâr 200,33
            IIH;39;1.189,98;30,512347;-20,15
        """.trimIndent()

        val result = FundPortfolioParser.parse(text)

        assertEquals(listOf("GTL", "YAY", "IIH"), result.rows.map { it.code })
        val yay = result.rows[1]
        assertEquals(1_000_000L, yay.quantity)
        assertEquals(1_900_503_121L, yay.price)
        assertEquals(190_050L, yay.value)
        assertEquals(20_033L, yay.profit)
        val iih = result.rows[2]
        assertEquals(-2_015L, iih.profit)
        assertEquals(121_013L, iih.cost)
    }

    @Test
    fun parse_joinsCodeLineSplitFromItsNumbersByOcr() {
        val result = FundPortfolioParser.parse("PPK\n1.612  3.870,79  2,401234  +440,60")
        assertEquals(1, result.rows.size)
        assertEquals(387_079L, result.rows.single().value)
    }

    @Test
    fun parse_reportsLinesWithNumbersButNoConsistentTriple() {
        val result = FundPortfolioParser.parse("ABC 10 20 30")
        assertTrue(result.rows.isEmpty())
        assertEquals(listOf("ABC 10 20 30"), result.unreadLines)
    }
}

class StatementParsingTest {
    private val sample = """
        Hesap Kesim Tarihi: 10.10.2026
        Önceki Dönem Borcu 12.000,00
        12.09.2026  MIGROS KADIKOY ISTANBUL  450,75
        14.09.2026  NETFLIX.COM  229,99
        15.09.2026  SHELL PETROL 2/3 TAKSIT  1.200,00
        18.09.2026  ÖDEME - TEŞEKKÜRLER  5.000,00
        20.09.2026  A101 MAGAZACILIK  89,90
        21.09.2026  okunamayan satır
    """.trimIndent()

    @Test
    fun parse_extractsTransactionsSkipsSummaryAndFlagsCredits() {
        val result = BankStatementParser.parse(sample)

        assertEquals(5, result.transactions.size)
        assertEquals("MIGROS KADIKOY ISTANBUL", result.transactions[0].description)
        assertEquals(45_075L, result.transactions[0].amount)
        assertEquals(120_000L, result.transactions[2].amount)
        assertTrue(result.transactions[3].isCredit)
        assertEquals(listOf("21.09.2026  okunamayan satır"), result.unreadLines)
    }

    @Test
    fun compare_matchesByAmountAndDateToleranceAndSuggestsLearnedCategory() {
        val parsed = BankStatementParser.parse(sample).transactions
        val expenses = listOf(
            expense(1, "Migros", 45_075L, LocalDate.of(2026, 9, 13)),
            expense(2, "Netflix", 22_999L, LocalDate.of(2026, 9, 14)),
            expense(3, "Shell", 110_000L, LocalDate.of(2026, 9, 15)),
            expense(4, "Kırtasiye", 5_000L, LocalDate.of(2026, 9, 16))
        )
        val rules = listOf(MerchantRule(1, "a101", categoryId = 7, hitCount = 3))

        val comparison = StatementMatcher.compare(parsed, expenses, rules)

        assertEquals(2, comparison.matchedCount)
        assertEquals(1, comparison.newCount)
        assertEquals(7L, (comparison.matches.single { it is StatementMatch.New } as StatementMatch.New).suggestedCategoryId)
        // Shell: similar title, different amount; plus the payment line.
        assertEquals(2, comparison.mismatchCount)
        assertEquals(listOf(4L), comparison.missingFromStatement.map { it.id })
        assertEquals(45_075L + 22_999L + 120_000L + 8_990L, comparison.statementTotal)
    }

    @Test
    fun findDuplicate_requiresSameDayAmountAndSimilarTitle() {
        val existing = listOf(expense(1, "MİGROS TİC. A.Ş.", 45_075L, LocalDate.of(2026, 9, 12)))
        assertNotNull(StatementMatcher.findDuplicate(LocalDate.of(2026, 9, 12), 45_075L, "Migros Kadıköy", existing))
        assertNull(StatementMatcher.findDuplicate(LocalDate.of(2026, 9, 13), 45_075L, "Migros", existing))
        assertNull(StatementMatcher.findDuplicate(LocalDate.of(2026, 9, 12), 45_075L, "Shell", existing))
    }

    private fun expense(id: Long, title: String, amount: Long, date: LocalDate) = Expense(
        id = id, title = title, amount = amount, expenseDate = date, categoryId = 1, paymentAccountId = 1,
        paymentSourceType = AccountType.CREDIT_CARD, note = null
    )
}

class ReceiptAndNotificationParserTest {
    @Test
    fun receipt_readsMerchantDateAndTotalButNotSubtotalOrVat() {
        val text = """
            MİGROS TİCARET A.Ş.
            KADIKÖY ŞUBESİ
            TARİH: 12.10.2026  SAAT: 18:42
            SÜT  *34,90
            EKMEK  *12,50
            ARA TOPLAM  *47,40
            TOPKDV  *0,47
            TOPLAM  *47,40
            NAKİT  *50,00
        """.trimIndent()

        val draft = ReceiptParser.parse(text)

        assertEquals("Migros Ticaret", draft.merchant)
        assertEquals(LocalDate.of(2026, 10, 12), draft.date)
        assertEquals(4_740L, draft.total)
    }

    @Test
    fun receipt_repairsOcrAccentOnDottedCapitalI() {
        assertEquals("Migros Ticaret", ReceiptParser.parse("MÍGROS TÍCARET A.Ş.\nTOPLAM *10,00").merchant)
    }

    @Test
    fun receipt_fallsBackToLargestStarAmount() {
        val draft = ReceiptParser.parse("KAHVECİ\n*45,00\n*12,00")
        assertEquals(4_500L, draft.total)
    }

    @Test
    fun notification_parsesSpendingAndIgnoresOtpAndIncoming() {
        val spend = NotificationExpenseParser.parse(
            "Kartınızdan harcama",
            "1234 ile biten kartınızdan MIGROS KADIKOY'de 450,75 TL harcama yapılmıştır."
        )
        assertEquals(45_075L, spend?.amount)
        assertTrue(spend!!.merchant.contains("MIGROS"))

        assertNull(NotificationExpenseParser.parse("Banka", "Doğrulama kodunuz 123456. 450,75 TL işlem için."))
        assertNull(NotificationExpenseParser.parse("Banka", "Hesabınıza 5.000,00 TL gelen havale yatırıldı."))
    }
}
