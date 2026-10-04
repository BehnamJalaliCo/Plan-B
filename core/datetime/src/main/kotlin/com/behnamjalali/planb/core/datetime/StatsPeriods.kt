package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.StatsPeriod
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar-correct ranges for statistics: weeks start on the user's first day of week, months
 * and years follow the user's calendar (a Jalali year runs from Farvardin 1 to Esfand 29/30).
 */
object StatsPeriods {
    /** The period of [period] that contains [date]. */
    fun spanOf(period: StatsPeriod, date: LocalDate, engine: CalendarEngine, firstDayOfWeek: DayOfWeek): DateSpan = when (period) {
        StatsPeriod.WEEK -> MonthGrid.weekStart(date, firstDayOfWeek).let { DateSpan(it, it.plusDays(6)) }
        StatsPeriod.MONTH -> engine.monthOf(date).let { DateSpan(engine.firstDayOfMonth(it), engine.lastDayOfMonth(it)) }
        StatsPeriod.YEAR -> yearSpan(engine.toCalendarDate(date).year, engine)
    }

    /** The whole calendar [year] (Jalali or Gregorian, by [engine]). */
    fun yearSpan(year: Int, engine: CalendarEngine): DateSpan =
        DateSpan(engine.toLocalDate(year, 1, 1), engine.lastDayOfMonth(CalendarMonth(year, 12)))

    /** The period [steps] periods after (negative: before) [span]. */
    fun shift(period: StatsPeriod, span: DateSpan, steps: Int, engine: CalendarEngine, firstDayOfWeek: DayOfWeek): DateSpan {
        val anchor = when (period) {
            StatsPeriod.WEEK -> span.start.plusWeeks(steps.toLong())
            StatsPeriod.MONTH -> engine.plusMonths(span.start, steps)
            StatsPeriod.YEAR -> engine.plusYears(span.start, steps)
        }
        return spanOf(period, anchor, engine, firstDayOfWeek)
    }

    /** Chart buckets: days for a week or a month, months for a year. */
    fun buckets(period: StatsPeriod, span: DateSpan, engine: CalendarEngine): List<DateSpan> = when (period) {
        StatsPeriod.WEEK, StatsPeriod.MONTH -> span.dates().map { DateSpan(it, it) }.toList()
        StatsPeriod.YEAR -> {
            val first = engine.monthOf(span.start)
            (0 until 12).map { i ->
                val month = first.plus(i)
                DateSpan(engine.firstDayOfMonth(month), engine.lastDayOfMonth(month))
            }
        }
    }
}
