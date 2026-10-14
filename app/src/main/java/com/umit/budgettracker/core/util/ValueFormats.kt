package com.umit.budgettracker.core.util

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.parsing.TurkishNumbers
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Display / input helpers for scaled prices, quantities and basis points (no Double anywhere). */
object ValueFormats {
    private val turkish = Locale.forLanguageTag("tr-TR")

    private fun format(value: BigDecimal, minDecimals: Int, maxDecimals: Int): String =
        NumberFormat.getNumberInstance(turkish).apply {
            minimumFractionDigits = minDecimals
            maximumFractionDigits = maxDecimals
        }.format(value)

    /**
     * PRICE_SCALE → "6.592,12 ₺". Fund prices (codes "FUND:…") always keep their six decimals
     * ("1.900,503121 ₺"); other prices under 10 ₺ show four.
     */
    fun price(scaled: Long, code: String? = null): String {
        val value = BigDecimal.valueOf(scaled).movePointLeft(6)
        return when {
            code?.startsWith("FUND:") == true -> format(value, 6, 6)
            value < BigDecimal.TEN -> format(value, 4, 6)
            else -> format(value, 2, 2)
        } + " ₺"
    }

    /** QUANTITY_SCALE → "2.010" / "2,5". */
    fun quantity(scaled: Long): String = format(BigDecimal.valueOf(scaled).movePointLeft(6), 0, 6)

    /** For editing: "2,5" without grouping. */
    fun quantityInput(scaled: Long): String =
        BigDecimal.valueOf(scaled).movePointLeft(6).stripTrailingZeros().toPlainString().replace('.', ',')

    fun priceInput(scaled: Long): String =
        BigDecimal.valueOf(scaled).movePointLeft(6).stripTrailingZeros().toPlainString().replace('.', ',')

    fun parseQuantity(text: String): Long? = TurkishNumbers.parseScaled(text, AssetValuationRules.QUANTITY_SCALE)

    fun parsePrice(text: String): Long? = TurkishNumbers.parseScaled(text, AssetValuationRules.PRICE_SCALE)

    /** 798 → "+%6,90", −184 → "−%1,84". */
    fun signedPercent(bp: Int): String {
        val sign = when {
            bp > 0 -> "+"
            bp < 0 -> "−"
            else -> ""
        }
        val abs = kotlin.math.abs(bp)
        return "$sign%${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    fun signedMoney(minor: Long): String = (if (minor > 0) "+" else if (minor < 0) "−" else "") + MoneyFormatter.format(kotlin.math.abs(minor))
}
