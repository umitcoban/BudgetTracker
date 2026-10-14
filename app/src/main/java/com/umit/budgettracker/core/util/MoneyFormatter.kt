package com.umit.budgettracker.core.util

import java.text.NumberFormat
import java.util.*

object MoneyFormatter {
    private val turkishLocale = Locale.forLanguageTag("tr-TR")
    private val currencyFormat = NumberFormat.getCurrencyInstance(turkishLocale).apply {
        currency = Currency.getInstance("TRY")
    }

    fun format(amountKurus: Long): String {
        return currencyFormat.format(amountKurus / 100.0)
    }

    /**
     * Typed TRY text → kuruş, exactly: "19,99", "1.250", "1.250,50" and "100000.50" all work.
     * Parsing used to go through Double, which stored 19,99 as 19,98 and 1.250 as 1,25.
     */
    fun parse(input: String): Long? = com.umit.budgettracker.core.parsing.TurkishNumbers.parseMinor(input)
}
