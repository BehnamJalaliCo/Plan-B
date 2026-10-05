package com.behnamjalali.planb.core.datetime

import android.content.res.Resources
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.datetime.iran.HijriDate
import com.behnamjalali.planb.core.model.CalendarSystem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Locale-aware date/time presentation. All user-visible words come from
 * Android resources; only the digit shaping is done here (via [NumberFormatter]).
 */
class PlannerDateFormatter(
    private val resources: Resources,
    val calendarSystem: CalendarSystem,
    val firstDayOfWeek: DayOfWeek,
    val numbers: NumberFormatter,
    private val use24Hour: Boolean,
) {
    val engine: CalendarEngine = CalendarEngines.of(calendarSystem)

    private val jalaliMonths = resources.getStringArray(R.array.jalali_month_names)
    private val gregorianMonths = resources.getStringArray(R.array.gregorian_month_names)
    private val weekdayNames = resources.getStringArray(R.array.weekday_names)
    private val weekdayShort = resources.getStringArray(R.array.weekday_short_names)
    private val weekdayNarrow = resources.getStringArray(R.array.weekday_narrow_names)
    private val hijriMonths by lazy { resources.getStringArray(R.array.hijri_month_names) }

    private val isJalali get() = calendarSystem == CalendarSystem.JALALI

    fun monthName(month: Int): String =
        if (isJalali) jalaliMonths[month - 1] else gregorianMonths[month - 1]

    fun weekdayName(day: DayOfWeek): String = weekdayNames[day.value - 1]

    fun weekdayShort(day: DayOfWeek): String = weekdayShort[day.value - 1]

    fun weekdayNarrow(day: DayOfWeek): String = weekdayNarrow[day.value - 1]

    fun dayNumber(date: LocalDate): String = numbers.format(engine.toCalendarDate(date).day)

    /** e.g. "یکشنبه ۱۲ مهر ۱۴۰۵" / "Sunday, October 4, 2026". */
    fun fullDate(date: LocalDate): String {
        val c = engine.toCalendarDate(date)
        val pattern = if (isJalali) R.string.date_pattern_full_jalali else R.string.date_pattern_full_gregorian
        return resources.getString(
            pattern,
            weekdayName(date.dayOfWeek),
            numbers.format(c.day),
            monthName(c.month),
            numbers.format(c.year),
        )
    }

    /** e.g. "۱۲ مهر" / "Oct 3" style (full month name). */
    fun dayMonth(date: LocalDate): String {
        val c = engine.toCalendarDate(date)
        val pattern = if (isJalali) R.string.date_pattern_day_month_jalali else R.string.date_pattern_day_month_gregorian
        return resources.getString(pattern, numbers.format(c.day), monthName(c.month))
    }

    fun mediumDate(date: LocalDate): String {
        val c = engine.toCalendarDate(date)
        val pattern = if (isJalali) R.string.date_pattern_medium_jalali else R.string.date_pattern_medium_gregorian
        return resources.getString(pattern, numbers.format(c.day), monthName(c.month), numbers.format(c.year))
    }

    /** Short date that omits the year when it matches [today]'s year. */
    fun shortDate(date: LocalDate, today: LocalDate): String =
        if (engine.toCalendarDate(date).year == engine.toCalendarDate(today).year) dayMonth(date) else mediumDate(date)

    /** "Today", "Tomorrow", "Yesterday" or a short date. */
    fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
        today -> resources.getString(R.string.date_today)
        today.plusDays(1) -> resources.getString(R.string.date_tomorrow)
        today.minusDays(1) -> resources.getString(R.string.date_yesterday)
        else -> shortDate(date, today)
    }

    /** e.g. "۲ ربیع‌الثانی ۱۴۴۸" / "2 Rabi' al-Thani 1448 AH" (Plan-B Pro #2). */
    fun hijriDate(date: HijriDate): String =
        resources.getString(R.string.date_pattern_hijri, numbers.format(date.day), hijriMonths[date.month - 1], numbers.format(date.year))

    fun monthYear(month: CalendarMonth): String =
        resources.getString(R.string.date_pattern_month_year, monthName(month.month), numbers.format(month.year))

    fun weekdayDate(date: LocalDate, today: LocalDate): String =
        resources.getString(R.string.date_pattern_weekday_date, weekdayName(date.dayOfWeek), shortDate(date, today))

    fun time(time: LocalTime): String {
        if (use24Hour) return "${numbers.twoDigits(time.hour)}:${numbers.twoDigits(time.minute)}"
        val hour12 = when (val h = time.hour % 12) { 0 -> 12; else -> h }
        val suffix = resources.getString(if (time.hour < 12) R.string.time_am else R.string.time_pm)
        return "${numbers.format(hour12)}:${numbers.twoDigits(time.minute)} $suffix"
    }

    fun timeRange(start: LocalTime, end: LocalTime?): String =
        if (end == null) time(start) else resources.getString(R.string.time_range, time(start), time(end))

    fun duration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0 -> resources.getQuantityString(R.plurals.duration_minutes, minutes, numbers.format(minutes))
            minutes == 0 -> resources.getQuantityString(R.plurals.duration_hours, hours, numbers.format(hours))
            else -> resources.getString(R.string.duration_hours_minutes, numbers.format(hours), numbers.format(minutes))
        }
    }

    /** mm:ss (or h:mm:ss) for timers. */
    fun timer(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val body = "${numbers.twoDigits(m.toInt())}:${numbers.twoDigits(s.toInt())}"
        return if (h > 0) "${numbers.format(h)}:$body" else body
    }

    fun monthOf(date: LocalDate): CalendarMonth = engine.monthOf(date)

    fun weekdays(): List<DayOfWeek> = MonthGrid.weekdays(firstDayOfWeek)
}
