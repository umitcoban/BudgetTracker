package com.umit.budgettracker.core.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

object DateUtils {
    private val turkishLocale = Locale.forLanguageTag("tr-TR")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", turkishLocale)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", turkishLocale)
    private val dayMonthLongFormatter = DateTimeFormatter.ofPattern("d MMMM", turkishLocale)
    private val dayMonthWeekdayFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", turkishLocale)

    fun formatMonthYear(yearMonth: YearMonth): String {
        return yearMonth.format(monthYearFormatter).replaceFirstChar { it.uppercase() }
    }

    /** "12 Eki" style short day-month label. */
    fun formatDate(date: LocalDate): String {
        return date.format(dayMonthFormatter)
    }

    /** "12 Ekim" — list section headers. */
    fun formatDayMonth(date: LocalDate): String {
        return date.format(dayMonthLongFormatter)
    }

    /** "12 Ekim, Pazartesi" — calendar-style headers. */
    fun formatDayMonthWeekday(date: LocalDate): String {
        return date.format(dayMonthWeekdayFormatter).replaceFirstChar { it.uppercase() }
    }
}
