package com.umit.budgettracker.core.database.converter

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.YearMonth

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? {
        return value?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun fromYearMonth(value: YearMonth?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toYearMonth(value: String?): YearMonth? {
        return value?.let { YearMonth.parse(it) }
    }
}
