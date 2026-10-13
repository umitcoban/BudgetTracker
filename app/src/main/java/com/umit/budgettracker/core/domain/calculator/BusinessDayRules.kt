package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.PublicHoliday
import com.umit.budgettracker.core.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDate

/** Banks settle on business days; a due date on a weekend or public holiday moves forward. */
object BusinessDayRules {
    fun isBusinessDay(date: LocalDate, holidays: Set<LocalDate>): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && date !in holidays

    fun nextBusinessDay(date: LocalDate, holidays: Set<LocalDate>): LocalDate {
        var current = date
        // A bayram plus weekend never exceeds ~10 days; the bound keeps bad data from looping.
        repeat(20) {
            if (isBusinessDay(current, holidays)) return current
            current = current.plusDays(1)
        }
        return current
    }

    /** "Cumartesi → bankada 12 Ekim Pazartesi'ye kayar" or null when the date is a business day. */
    fun shiftNote(date: LocalDate, holidays: List<PublicHoliday>): String? {
        val holidayDates = holidays.map { it.date }.toSet()
        if (isBusinessDay(date, holidayDates)) return null
        val shifted = nextBusinessDay(date, holidayDates)
        val reason = holidays.firstOrNull { it.date == date }?.name
            ?: if (date.dayOfWeek == DayOfWeek.SATURDAY) "Cumartesi" else "Pazar"
        return "$reason → bankada ${DateUtils.formatDayMonthWeekday(shifted)} günü işlenir"
    }
}
