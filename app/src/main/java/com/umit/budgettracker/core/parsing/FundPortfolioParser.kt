package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import java.math.BigDecimal
import java.math.RoundingMode

/** One fund row read from pasted text, CSV or OCR. Amounts in kuruş, price at PRICE_SCALE. */
data class ParsedFundRow(
    val code: String,
    val quantity: Long,
    val price: Long,
    val value: Long,
    val profit: Long?,
    val cost: Long?,
    val sourceLine: String
)

data class FundParseResult(val rows: List<ParsedFundRow>, val unreadLines: List<String>)

/**
 * Reads broker portfolio tables such as
 *
 * ```
 * GTA  2.415  3.777,39  1,564138  +279,13  %7,98
 * ```
 *
 * Column order varies between brokers, so instead of fixed positions the parser looks for a
 * fund code (three upper-case letters) and then the number triple `quantity × price ≈ value`
 * (within 0,5 %). A signed amount after the value, if present, is the profit; cost = value − profit.
 */
object FundPortfolioParser {
    private val fundCode = Regex("""(?<![A-ZÇĞİÖŞÜ])([A-Z]{3})(?![A-ZÇĞİÖŞÜa-z])""")
    private val headerWords = listOf("FON", "KOD", "ADET", "TUTAR", "FİYAT", "FIYAT", "TOPLAM", "KÂR", "KAR")
    private val tolerance = BigDecimal("0.005")

    fun parse(text: String): FundParseResult {
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
            if (code == null) {
                if (TurkishNumbers.tokens(line).size >= 3 && headerWords.none { line.uppercase().contains(it) }) unread += line
                return@forEach
            }
            val parsed = parseRow(code, line)
            if (parsed != null) rows += parsed else unread += line
        }
        return FundParseResult(rows.distinctBy { it.code }, unread)
    }

    private fun parseRow(code: String, line: String): ParsedFundRow? {
        val afterCode = line.substringAfter(code)
        val numbers = TurkishNumbers.tokens(afterCode).filter { !it.isPercent }
        if (numbers.size < 3) return null
        for (q in numbers.indices) for (p in numbers.indices) for (v in numbers.indices) {
            if (q == p || p == v || q == v) continue
            val quantity = numbers[q].value
            val price = numbers[p].value
            val value = numbers[v].value
            if (quantity.signum() <= 0 || price.signum() <= 0 || value.signum() <= 0) continue
            val product = quantity.multiply(price)
            val diff = product.subtract(value).abs()
            if (diff > value.multiply(tolerance).max(BigDecimal("0.01"))) continue
            // Prefer the reading where the price has the most decimals (fund prices carry six).
            if (numbers[p].decimals < numbers[v].decimals) continue
            val profitToken = numbers.withIndex()
                .firstOrNull { (index, token) -> index > v && index != p && index != q && token.value.abs() < value }
                ?.value
            val profit = profitToken?.value
            return ParsedFundRow(
                code = code,
                quantity = quantity.multiply(BigDecimal.valueOf(AssetValuationRules.QUANTITY_SCALE)).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                price = price.multiply(BigDecimal.valueOf(AssetValuationRules.PRICE_SCALE)).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                value = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(),
                profit = profit?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact(),
                cost = profit?.let { value.subtract(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() },
                sourceLine = line
            )
        }
        return null
    }
}
