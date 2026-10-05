package com.behnamjalali.planb.core.datetime.iran

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** A date in the Hijri (lunar) calendar. Months are 1-based (1 = Muharram). */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/** A Hijri month, the key of month-start tables. */
data class HijriMonth(val year: Int, val month: Int) : Comparable<HijriMonth> {
    fun plus(months: Int): HijriMonth {
        val zeroBased = year * 12L + (month - 1) + months
        return HijriMonth(Math.floorDiv(zeroBased, 12L).toInt(), Math.floorMod(zeroBased, 12L).toInt() + 1)
    }

    override fun compareTo(other: HijriMonth): Int = compareValuesBy(this, other, HijriMonth::year, HijriMonth::month)
}

/**
 * The Hijri (lunar) calendar as used in Iran.
 *
 * **Accuracy.** Iran's official lunar dates are not computed: each month starts when the new
 * crescent is sighted (confirmed by the official moon-sighting committee), so the real date can
 * differ by a day from any arithmetic calendar and is only certain shortly before it happens.
 * The base here is ICU's `islamic-civil` (tabular) calendar: against the Iranian calendar
 * published for 1405 and 1406 it gave the same first day for 13 of the 25 months and was one
 * day off for the others, while `islamic-umalqura` matched only 6 (see docs/PRO.md). Months in
 * [published] (the month starts of a published calendar, see [PublishedHijriMonths]) replace
 * the computed start; an override more than two days away from the computation is ignored as
 * implausible. The UI always says that lunar dates may shift by a day.
 */
class HijriCalendar(published: Map<HijriMonth, LocalDate> = emptyMap()) {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val computedStarts = ConcurrentHashMap<HijriMonth, LocalDate>()
    private val overrides: Map<HijriMonth, LocalDate> =
        published.filter { (month, date) -> kotlin.math.abs(date.toEpochDay() - computedStart(month).toEpochDay()) <= MAX_SHIFT_DAYS }

    /** Months whose start comes from a published calendar rather than the computation. */
    val publishedMonths: Set<HijriMonth> get() = overrides.keys

    private fun newIcu(): IslamicCalendar = IslamicCalendar(utc).apply {
        calculationType = IslamicCalendar.CalculationType.ISLAMIC_CIVIL
    }

    private fun computedStart(month: HijriMonth): LocalDate = computedStarts.getOrPut(month) {
        val cal = newIcu()
        cal.clear()
        cal.set(month.year, month.month - 1, 1)
        LocalDate.ofEpochDay(Math.floorDiv(cal.timeInMillis, DAY_MS))
    }

    /**
     * The first day of [month] in Iran's calendar: published when known, else computed. A
     * computed month next to a published one moves with it when needed, so every month keeps 29
     * or 30 days.
     */
    fun monthStart(month: HijriMonth): LocalDate {
        overrides[month]?.let { return it }
        var start = computedStart(month)
        overrides[month.plus(1)]?.let { next ->
            val length = next.toEpochDay() - start.toEpochDay()
            if (length > MAX_LENGTH) start = start.plusDays(length - MAX_LENGTH)
            if (length < MIN_LENGTH) start = start.minusDays(MIN_LENGTH - length)
        }
        overrides[month.plus(-1)]?.let { previous ->
            val length = start.toEpochDay() - previous.toEpochDay()
            if (length > MAX_LENGTH) start = start.minusDays(length - MAX_LENGTH)
            if (length < MIN_LENGTH) start = start.plusDays(MIN_LENGTH - length)
        }
        return start
    }

    fun monthLength(month: HijriMonth): Int = (monthStart(month.plus(1)).toEpochDay() - monthStart(month).toEpochDay()).toInt()

    fun toHijri(date: LocalDate): HijriDate {
        val cal = newIcu()
        cal.timeInMillis = date.toEpochDay() * DAY_MS
        var month = HijriMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        // A published start can move the boundary by a day or two in either direction.
        while (date < monthStart(month)) month = month.plus(-1)
        while (date >= monthStart(month.plus(1))) month = month.plus(1)
        return HijriDate(month.year, month.month, (date.toEpochDay() - monthStart(month).toEpochDay()).toInt() + 1)
    }

    /** [day] beyond the month's length gives the last day (a holiday on "30 Safar" in a 29-day Safar). */
    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        val m = HijriMonth(year, month)
        return monthStart(m).plusDays((day.coerceIn(1, monthLength(m)) - 1).toLong())
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_SHIFT_DAYS = 2
        const val MIN_LENGTH = 29L
        const val MAX_LENGTH = 30L
    }
}
