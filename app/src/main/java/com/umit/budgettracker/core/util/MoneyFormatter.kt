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

    fun parse(input: String): Long? {
        if (input.isBlank()) return null
        return try {
            val cleanedInput = input.replace("[^0-9,.]".toRegex(), "").replace(",", ".")
            (cleanedInput.toDouble() * 100).toLong()
        } catch (e: Exception) {
            null
        }
    }
}
