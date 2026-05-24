package com.umit.budgettracker.core.util

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

object DateUtils {
    private val turkishLocale = Locale.forLanguageTag("tr-TR")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", turkishLocale)

    fun formatMonthYear(yearMonth: YearMonth): String {
        return yearMonth.format(monthYearFormatter).replaceFirstChar { it.uppercase() }
    }
}
