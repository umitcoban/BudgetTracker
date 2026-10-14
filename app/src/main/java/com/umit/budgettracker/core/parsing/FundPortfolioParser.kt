package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One fund row read from pasted text, CSV or OCR. Amounts in kuruş, price at PRICE_SCALE. */
data class ParsedFundRow(
    val code: String,
    val quantity: Long,
    val price: Long,
    val value: Long,
    val profit: Long?,
    val cost: Long?,
    val sourceLine: String,
    /** Fund title when the source carries one ("GPY ALTIN FONU"). */
    val name: String? = null,
    /** Day the price belongs to when it is not today's (rows rebuilt from transaction history). */
    val priceDate: LocalDate? = null,
    /** Realised profit of past sales (transaction history only). */
    val realizedProfit: Long? = null
)

data class FundParseResult(val rows: List<ParsedFundRow>, val unreadLines: List<String>)

/**
 * Reads broker portfolio tables such as
 *
 * ```
 * GTA  2.415  3.777,39  1,564138  +279,13  %7,98
 * GTA-GPY ALTIN FONU  123 - 4567890 ŞUBE  8,1234  2.000  2.000  3.200,00 TL  3.200,00 TL  200,00 TL  % 6,67  1,600000 TL
 * ```
 *
 * Column order varies between brokers, so instead of fixed positions the parser looks for a
 * fund code (three upper-case letters) and then the number triple `quantity × price ≈ value`
 * (within 0,5 %). A signed amount after the value, if present, is the profit; cost = value − profit.
 * Branch / account numbers ("123 - 4567890") and digits inside the fund title ("1. PARA",
 * "2.DEĞİŞKEN") are dropped before the search so they cannot pose as a quantity.
 */
object FundPortfolioParser {
    internal val fundCode = Regex("""(?<![A-ZÇĞİÖŞÜ])([A-Z]{3})(?![A-ZÇĞİÖŞÜa-z])""")
    private val headerWords = listOf("FON", "KOD", "ADET", "TUTAR", "FİYAT", "FIYAT", "TOPLAM", "KÂR", "KAR")
    private val accountNumber = Regex("""\b\d{2,5}\s*[-–]\s*\d{5,}\b""")
    private val ambiguousThousands = Regex("""\d{1,3},\d{3}""")
    private val bareSixDecimals = Regex("""\d{7,9}""")
    private val tolerance = BigDecimal("0.005")

    /**
     * [knownCodes] are funds the user already holds or has prices for. OCR sometimes drops or
     * doubles a letter of the code ("IIH-İSTANBUL" → "IH-İSTANBUL"); a row whose code is one
     * letter away from exactly one known code is read as that fund.
     */
    fun parse(text: String, knownCodes: Set<String> = emptySet()): FundParseResult {
        val rows = mutableListOf<ParsedFundRow>()
        val unread = mutableListOf<String>()
        val lines = text.lines().map { it.replace('\t', ' ').replace(';', ' ').trim() }.filter { it.isNotBlank() }
        // OCR often splits a table row; join a code-only line with the numbers on the next line.
        val merged = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (fundCode.matches(line.trim()) && index + 1 < lines.size && fundCode.find(lines[index + 1]) == null) {
                merged += "$line ${lines[index + 1]}"
                index += 2
            } else {
                merged += line
                index++
            }
        }
        merged.forEach { line ->
            val code = fundCode.findAll(line).map { it.groupValues[1] }.firstOrNull { it !in headerWords }
                ?: repairedCode(line, knownCodes)?.let { (code, raw) ->
                    val parsed = parseRow(raw, line)?.copy(code = code)
                    if (parsed != null) rows += parsed else unread += line
                    return@forEach
                }
            if (code == null) {
                if (TurkishNumbers.tokens(line).size >= 3 && headerWords.none { line.uppercase().contains(it) }) unread += line
                return@forEach
            }
            val parsed = parseRow(code, line)
            if (parsed != null) rows += parsed else unread += line
        }
        return FundParseResult(rows.distinctBy { it.code }, unread)
    }

    private val mangledCode = Regex("""^\s*([A-Z]{2,4})\s*[-–—]""")

    /** (known code, code as written) when the row's code is one edit away from a single known code. */
    private fun repairedCode(line: String, knownCodes: Set<String>): Pair<String, String>? {
        val raw = mangledCode.find(line)?.groupValues?.get(1) ?: return null
        return knownCodes.filter { isOneEditAway(raw, it) }.singleOrNull()?.let { it to raw }
    }

    private fun isOneEditAway(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) return a.indices.count { a[it] != b[it] } == 1
        val (short, long) = if (a.length < b.length) a to b else b to a
        return long.indices.any { long.removeRange(it, it + 1) == short }
    }

    private fun parseRow(code: String, line: String): ParsedFundRow? {
        val afterCode = line.substringAfter(code).replace(accountNumber, " ")
        val numbers = valueTokens(afterCode)
        findTriple(numbers)?.let { return buildRow(code, line, numbers, it) }
        val repaired = numbers.map(::ocrRepaired)
        findTriple(repaired)?.let { return buildRow(code, line, repaired, it) }
        return singleUnitRow(code, line, numbers)
    }

    private fun buildRow(code: String, line: String, numbers: List<TurkishNumbers.NumberToken>, triple: Triple<Int, Int, Int>): ParsedFundRow {
        val (q, p, v) = triple
        val value = numbers[v].value
        // Zero amounts ("satılabilir 0,00") and price-like numbers are not the profit column.
        val profit = numbers.withIndex()
            .firstOrNull { (index, token) ->
                index > v && index != p && index != q && token.value.abs() < value && token.value.signum() != 0 && token.decimals < 4
            }
            ?.value?.value
        return ParsedFundRow(
            code = code,
            quantity = numbers[q].value.scaled(AssetValuationRules.QUANTITY_SCALE),
            price = numbers[p].value.scaled(AssetValuationRules.PRICE_SCALE),
            value = value.toMinor(),
            profit = profit?.toMinor(),
            cost = profit?.let { value.subtract(it).toMinor() },
            sourceLine = line
        )
    }

    /**
     * Second reading for OCR slips: the thousands dot of "2.500" read as a comma ("2,500" → 2500;
     * only three-digit tails, so "1,600000" is untouched), and a six-decimal fund price whose
     * comma was dropped ("25000000" → 25,000000; seven or more bare digits).
     */
    private fun ocrRepaired(token: TurkishNumbers.NumberToken): TurkishNumbers.NumberToken = when {
        ambiguousThousands.matches(token.text) -> token.copy(text = token.text.replace(",", "."), value = token.value.movePointRight(3))
        bareSixDecimals.matches(token.text) -> token.value.movePointLeft(6).let { price ->
            token.copy(text = price.toPlainString().replace('.', ','), value = price)
        }
        else -> token
    }

    /**
     * A one-unit holding ("YAY  1  2.000,00  1.999,998000") has value = price; OCR often drops the
     * lone "1", so a six-decimal price equal to an amount on the row is read as one unit.
     */
    private fun singleUnitRow(code: String, line: String, numbers: List<TurkishNumbers.NumberToken>): ParsedFundRow? {
        val (p, v) = numbers.indices.filter { numbers[it].decimals >= 4 && numbers[it].value.signum() > 0 }.firstNotNullOfOrNull { p ->
            val rounded = numbers[p].value.setScale(2, RoundingMode.HALF_UP)
            numbers.indices.firstOrNull { it != p && numbers[it].value.compareTo(rounded) == 0 }?.let { p to it }
        } ?: return null
        val one = TurkishNumbers.NumberToken("1", BigDecimal.ONE, -1, false)
        return buildRow(code, line, numbers + one, Triple(numbers.size, p, v))
    }

    /**
     * Number tokens that can be table values: percentages, title fragments glued to letters
     * ("2.DEĞİŞKEN") and ordinals ending in a dot ("1. PARA") are skipped.
     */
    internal fun valueTokens(text: String): List<TurkishNumbers.NumberToken> =
        TurkishNumbers.tokens(text).filter { token ->
            val end = token.start + token.text.length
            val gluedToWord = text.getOrNull(end)?.isLetter() == true && !text.startsWith("TL", end) && !text.startsWith("TRY", end)
            !token.isPercent && !token.text.endsWith('.') && !token.text.endsWith(',') && !gluedToWord
        }

    /**
     * Indices (quantity, price, value) with quantity × price ≈ value. Fund prices carry six
     * decimals, so when the row has a number with four or more decimals the price must be one of
     * them; otherwise "1 × 14.836,12 = 14.836,12" would win over the real triple.
     */
    internal fun findTriple(numbers: List<TurkishNumbers.NumberToken>, absoluteValue: Boolean = false): Triple<Int, Int, Int>? {
        if (numbers.size < 3) return null
        val finePrices = numbers.any { it.decimals >= 4 }
        for (q in numbers.indices) for (p in numbers.indices) for (v in numbers.indices) {
            if (q == p || p == v || q == v) continue
            val quantity = numbers[q].value
            val price = numbers[p].value
            val value = if (absoluteValue) numbers[v].value.abs() else numbers[v].value
            if (quantity.signum() <= 0 || price.signum() <= 0 || value.signum() <= 0) continue
            if (finePrices && numbers[p].decimals < 4) continue
            val diff = quantity.multiply(price).subtract(value).abs()
            if (diff > value.multiply(tolerance).max(BigDecimal("0.01"))) continue
            // Prefer the reading where the price has the most decimals (fund prices carry six).
            if (numbers[p].decimals < numbers[v].decimals) continue
            return Triple(q, p, v)
        }
        return null
    }

    internal fun BigDecimal.scaled(scale: Long): Long =
        multiply(BigDecimal.valueOf(scale)).setScale(0, RoundingMode.HALF_UP).longValueExact()

    internal fun BigDecimal.toMinor(): Long = movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
