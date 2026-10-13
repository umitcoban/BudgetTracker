package com.umit.budgettracker.core.parsing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Exact parsing of numbers as they appear in Turkish bank / broker text, without floating point.
 *
 * - "3.115,75" → 3115.75 (dot = thousands, comma = decimal)
 * - "1,550123" → 1.550123
 * - "2.010"    → 2010 (a lone dot followed by exactly three digits is a thousands separator)
 * - "49.3681"  → 49.3681 (dot with any other digit count is a decimal point, e.g. TCMB XML)
 * - "−20,15", "-20,15", "+201,15", "(20,15)" → signed
 * - "₺", "TL", "TRY", "%", "$" and spaces are ignored
 */
object TurkishNumbers {
    private val numberToken = Regex("""[-−+(]?\s*[$₺]?\s*\d[\d.,]*\)?""")

    fun parseDecimal(raw: String): BigDecimal? {
        var text = raw.trim()
            .replace("TRY", "", ignoreCase = true)
            .replace("TL", "", ignoreCase = true)
            .replace("₺", "")
            .replace("$", "")
            .replace("%", "")
            .replace(" ", "")
            .replace(" ", "")
        if (text.isEmpty()) return null
        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1)
        }
        when {
            text.startsWith("-") || text.startsWith("−") -> { negative = true; text = text.substring(1) }
            text.startsWith("+") -> text = text.substring(1)
        }
        if (text.endsWith("-")) { negative = true; text = text.dropLast(1) }
        if (text.isEmpty() || text.any { !it.isDigit() && it != '.' && it != ',' }) return null
        if (!text.first().isDigit()) return null

        val normalized = when {
            text.contains(',') -> {
                if (text.count { it == ',' } > 1) return null
                text.replace(".", "").replace(',', '.')
            }
            text.count { it == '.' } > 1 -> {
                if (!Regex("""\d{1,3}(\.\d{3})+""").matches(text)) return null
                text.replace(".", "")
            }
            text.contains('.') -> {
                val decimals = text.substringAfter('.')
                if (decimals.length == 3 && text.substringBefore('.').length in 1..3) text.replace(".", "") else text
            }
            else -> text
        }
        val value = normalized.toBigDecimalOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    /** TRY text → kuruş, half-up. */
    fun parseMinor(raw: String): Long? = parseDecimal(raw)?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()

    /** Text → value scaled by [scale] (e.g. 1_000_000 for six-decimal fund prices), half-up. */
    fun parseScaled(raw: String, scale: Long): Long? =
        parseDecimal(raw)?.multiply(BigDecimal.valueOf(scale))?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()

    /** All number-looking tokens of [line] in order, with their source text. */
    fun tokens(line: String): List<NumberToken> = numberToken.findAll(line).mapNotNull { match ->
        val text = match.value.trim()
        // The pattern's optional whitespace can include spaces before the number; start points at the text itself.
        val start = match.range.first + (match.value.length - match.value.trimStart().length)
        parseDecimal(text)?.let { NumberToken(text, it, start, line.getOrNull(match.range.last + 1) == '%' || line.getOrNull(start - 1) == '%') }
    }.toList()

    data class NumberToken(val text: String, val value: BigDecimal, val start: Int, val isPercent: Boolean) {
        val decimals: Int get() = text.substringAfterLast(',', "").filter { it.isDigit() }.length
    }

    private val datePatterns = listOf(
        Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4})\b"""),
        Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2})\b"""),
        Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    )

    private val monthNames = mapOf(
        "ocak" to 1, "şubat" to 2, "subat" to 2, "mart" to 3, "nisan" to 4, "mayıs" to 5, "mayis" to 5,
        "haziran" to 6, "temmuz" to 7, "ağustos" to 8, "agustos" to 8, "eylül" to 9, "eylul" to 9,
        "ekim" to 10, "kasım" to 11, "kasim" to 11, "aralık" to 12, "aralik" to 12
    )
    private val namedDate = Regex("""\b(\d{1,2})\s+(ocak|şubat|subat|mart|nisan|mayıs|mayis|haziran|temmuz|ağustos|agustos|eylül|eylul|ekim|kasım|kasim|aralık|aralik)\s+(\d{4})\b""", RegexOption.IGNORE_CASE)

    /** First date in [line] as day.month.year (Turkish order), ISO, or "12 Ekim 2026". */
    fun findDate(line: String): DateMatch? {
        datePatterns.forEachIndexed { index, regex ->
            val match = regex.find(line) ?: return@forEachIndexed
            val (a, b, c) = match.destructured
            val date = runCatching {
                when (index) {
                    2 -> LocalDate.of(a.toInt(), b.toInt(), c.toInt())
                    1 -> LocalDate.of(2000 + c.toInt(), b.toInt(), a.toInt())
                    else -> LocalDate.of(c.toInt(), b.toInt(), a.toInt())
                }
            }.getOrNull() ?: return@forEachIndexed
            return DateMatch(date, match.range)
        }
        namedDate.find(line)?.let { match ->
            val (day, month, year) = match.destructured
            val monthNumber = monthNames[month.lowercase(java.util.Locale.forLanguageTag("tr-TR"))] ?: return null
            val date = runCatching { LocalDate.of(year.toInt(), monthNumber, day.toInt()) }.getOrNull() ?: return null
            return DateMatch(date, match.range)
        }
        return null
    }

    data class DateMatch(val date: LocalDate, val range: IntRange)
}
