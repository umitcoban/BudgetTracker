package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.parsing.FundPortfolioParser.scaled
import com.umit.budgettracker.core.parsing.FundPortfolioParser.toMinor
import java.time.LocalDate
import java.util.Locale

/** One buy or sell from a broker's fund transaction history. Quantity at QUANTITY_SCALE, price at PRICE_SCALE, amount in kuruş (always positive). */
data class ParsedFundMovement(
    val date: LocalDate,
    val code: String,
    val name: String?,
    val isBuy: Boolean,
    val quantity: Long,
    val price: Long,
    val amount: Long,
    val sourceLine: String
)

data class FundMovementParseResult(val movements: List<ParsedFundMovement>, val unreadLines: List<String>)

/**
 * Reads fund transaction histories ("Fon Hareketleri") such as Garanti's `.xls` download:
 *
 * ```
 * Tarih       Fon                          İşlem Detayı  İşlem Tipi  Adet   Birim Fiyatı   Tutar
 * 11/06/2026  GTL-GPY 1. PARA PİYASASI...                ALIŞ        40033  0,124895 TL    -4999,92 TL
 * ```
 *
 * A row needs a date, a fund code, an ALIŞ / SATIŞ keyword and the `quantity × price ≈ amount`
 * triple; column order does not matter. Identical rows are kept: brokers split one order into
 * several equal executions, and each one is a real transaction.
 */
object FundMovementParser {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val side = Regex("""(?<!\p{L})(ALIŞ|ALIS|ALIM|SATIŞ|SATIS|SATIM)(?!\p{L})""")
    private val title = Regex("""^\s*[A-Z]{3}\s*[-–]\s*(.+)$""")

    fun parse(text: String): FundMovementParseResult {
        val movements = mutableListOf<ParsedFundMovement>()
        val unread = mutableListOf<String>()
        text.lines().filter { it.isNotBlank() }.forEach { raw ->
            val date = TurkishNumbers.findDate(raw) ?: return@forEach
            val line = raw.removeRange(date.range)
            val sideMatch = side.find(line.uppercase(tr)) ?: return@forEach
            val code = FundPortfolioParser.fundCode.findAll(line).map { it.groupValues[1] }.firstOrNull()
            val afterCode = code?.let { line.substringAfter(it) }
            val numbers = afterCode?.let { FundPortfolioParser.valueTokens(it.replace('\t', ' ')) }.orEmpty()
            val triple = FundPortfolioParser.findTriple(numbers, absoluteValue = true)
            if (code == null || triple == null) {
                unread += raw.replace('\t', ' ').trim()
                return@forEach
            }
            val (q, p, v) = triple
            movements += ParsedFundMovement(
                date = date.date,
                code = code,
                name = fundTitle(line, code),
                isBuy = sideMatch.value.startsWith("AL"),
                quantity = numbers[q].value.scaled(AssetValuationRules.QUANTITY_SCALE),
                price = numbers[p].value.scaled(AssetValuationRules.PRICE_SCALE),
                amount = numbers[v].value.abs().toMinor(),
                sourceLine = raw.replace('\t', ' ').trim()
            )
        }
        return FundMovementParseResult(movements, unread)
    }

    /** "GTA-GPY ALTIN FONU" cell → "GPY ALTIN FONU"; only for tab-separated rows, where the cell ends cleanly. */
    private fun fundTitle(line: String, code: String): String? {
        val cell = line.split('\t').firstOrNull { it.trim().startsWith(code) } ?: return null
        if (!line.contains('\t')) return null
        return title.find(cell)?.groupValues?.get(1)?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
