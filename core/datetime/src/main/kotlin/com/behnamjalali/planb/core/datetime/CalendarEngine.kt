package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.CalendarSystem
import java.time.DayOfWeek
import java.time.LocalDate

/** A date expressed in a specific calendar system. Months are 1-based. */
data class CalendarDate(val year: Int, val month: Int, val day: Int)

/** A month in a specific calendar system. */
data class CalendarMonth(val year: Int, val month: Int) : Comparable<CalendarMonth> {
    fun plus(months: Int): CalendarMonth {
        val zeroBased = year * 12L + (month - 1) + months
        return CalendarMonth(Math.floorDiv(zeroBased, 12L).toInt(), Math.floorMod(zeroBased, 12L).toInt() + 1)
    }

    override fun compareTo(other: CalendarMonth): Int =
        compareValuesBy(this, other, CalendarMonth::year, CalendarMonth::month)
}

/**
 * Converts canonical [LocalDate] values to and from a display calendar.
 * User data is always stored as [LocalDate]; engines are used only for
 * presentation and calendar-aware arithmetic (months/years).
 */
interface CalendarEngine {
    val system: CalendarSystem

    fun toCalendarDate(date: LocalDate): CalendarDate

    /** [day] is clamped to the month length. */
    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate

    fun monthLength(year: Int, month: Int): Int

    fun isLeapYear(year: Int): Boolean

    fun monthOf(date: LocalDate): CalendarMonth = toCalendarDate(date).let { CalendarMonth(it.year, it.month) }

    fun firstDayOfMonth(month: CalendarMonth): LocalDate = toLocalDate(month.year, month.month, 1)

    fun lastDayOfMonth(month: CalendarMonth): LocalDate =
        toLocalDate(month.year, month.month, monthLength(month.year, month.month))

    /** Adds months in this calendar, clamping the day (e.g. 31 Shahrivar + 1 month → 30 Mehr). */
    fun plusMonths(date: LocalDate, months: Int): LocalDate {
        val c = toCalendarDate(date)
        val target = CalendarMonth(c.year, c.month).plus(months)
        return toLocalDate(target.year, target.month, c.day)
    }

    fun plusYears(date: LocalDate, years: Int): LocalDate = plusMonths(date, years * 12)
}

object GregorianEngine : CalendarEngine {
    override val system = CalendarSystem.GREGORIAN

    override fun toCalendarDate(date: LocalDate) = CalendarDate(date.year, date.monthValue, date.dayOfMonth)

    override fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        val length = monthLength(year, month)
        return LocalDate.of(year, month, day.coerceIn(1, length))
    }

    override fun monthLength(year: Int, month: Int): Int = java.time.YearMonth.of(year, month).lengthOfMonth()

    override fun isLeapYear(year: Int): Boolean = java.time.Year.isLeap(year.toLong())
}

/** Builds the visible weeks of a month grid starting on [firstDayOfWeek]. */
object MonthGrid {
    private const val MIN_ROWS = 5

    data class Cell(val date: LocalDate, val inMonth: Boolean)

    /**
     * Always returns full weeks (5 or 6 rows) including adjacent-month days. A 28-day month
     * that starts on [firstDayOfWeek] fits in 4 weeks; it gets a fifth row of next-month days
     * so the grid height stays stable.
     */
    fun build(engine: CalendarEngine, month: CalendarMonth, firstDayOfWeek: DayOfWeek): List<List<Cell>> {
        val first = engine.firstDayOfMonth(month)
        val last = engine.lastDayOfMonth(month)
        val leading = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        var cursor = first.minusDays(leading.toLong())
        val weeks = ArrayList<List<Cell>>(6)
        while (cursor <= last || weeks.size < MIN_ROWS) {
            val week = ArrayList<Cell>(7)
            repeat(7) {
                week += Cell(cursor, cursor in first..last)
                cursor = cursor.plusDays(1)
            }
            weeks += week
        }
        return weeks
    }

    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.minusDays(Math.floorMod(date.dayOfWeek.value - firstDayOfWeek.value, 7).toLong())

    /** The seven weekdays in display order. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek.plus(it) }
}
