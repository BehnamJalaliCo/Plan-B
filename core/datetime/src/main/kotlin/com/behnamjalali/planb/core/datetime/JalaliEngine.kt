package com.behnamjalali.planb.core.datetime

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import com.behnamjalali.planb.core.model.CalendarSystem
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Jalali (Solar Hijri) calendar backed by Android ICU's Persian calendar.
 *
 * ICU is queried once per Jalali year for the 13 month boundaries; all further
 * conversions are plain arithmetic on that cached table, which keeps month grids
 * and large lists fast while relying on ICU for the calendar rules themselves.
 */
object JalaliEngine : CalendarEngine {
    override val system = CalendarSystem.JALALI

    /** Gregorian/Jalali year offset: Jalali year ≈ Gregorian year - 621 after Nowruz. */
    private const val YEAR_OFFSET = 621
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val locale = ULocale("fa_IR@calendar=persian")

    /** monthStarts[i] = LocalDate of the 1st of month i+1; index 12 = next Farvardin 1. */
    private class YearTable(val monthStarts: Array<LocalDate>)

    private val cache = ConcurrentHashMap<Int, YearTable>()

    private fun table(year: Int): YearTable = cache.getOrPut(year) {
        val starts = Array(13) { index ->
            val y = if (index == 12) year + 1 else year
            val m = if (index == 12) 0 else index
            icuToLocalDate(y, m, 1)
        }
        YearTable(starts)
    }

    private fun icuToLocalDate(year: Int, monthZeroBased: Int, day: Int): LocalDate {
        val cal = Calendar.getInstance(utc, locale)
        cal.clear()
        cal.set(year, monthZeroBased, day)
        return LocalDate.ofEpochDay(Math.floorDiv(cal.timeInMillis, 86_400_000L))
    }

    override fun toCalendarDate(date: LocalDate): CalendarDate {
        var year = date.year - YEAR_OFFSET
        if (date < table(year).monthStarts[0]) year -= 1
        val starts = table(year).monthStarts
        var month = 11
        while (month > 0 && date < starts[month]) month--
        val day = (date.toEpochDay() - starts[month].toEpochDay()).toInt() + 1
        return CalendarDate(year, month + 1, day)
    }

    override fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        require(month in 1..12) { "month must be 1..12" }
        val start = table(year).monthStarts[month - 1]
        return start.plusDays((day.coerceIn(1, monthLength(year, month)) - 1).toLong())
    }

    override fun monthLength(year: Int, month: Int): Int {
        val starts = table(year).monthStarts
        return (starts[month].toEpochDay() - starts[month - 1].toEpochDay()).toInt()
    }

    override fun isLeapYear(year: Int): Boolean = monthLength(year, 12) == 30
}

object CalendarEngines {
    fun of(system: CalendarSystem): CalendarEngine = when (system) {
        CalendarSystem.JALALI -> JalaliEngine
        CalendarSystem.GREGORIAN -> GregorianEngine
    }
}
