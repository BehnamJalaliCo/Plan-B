package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Deterministic recurrence expansion. Occurrences are computed on demand from
 * the series [anchor]; nothing is materialized ahead of time.
 *
 * Rules:
 * - Only dates that match the rule and are >= anchor are occurrences.
 * - WEEKLY without explicit weekdays repeats on the anchor's weekday.
 * - MONTHLY/YEARLY use the rule's calendar system and clamp the day to the
 *   month length (31 Shahrivar → 30 Mehr, 31 Jan → 28/29 Feb), always deriving
 *   from the anchor so the day "springs back" in longer months.
 * - [RecurrenceRule.count] limits the total number of occurrences from the anchor;
 *   [RecurrenceRule.until] is inclusive.
 * - MONTHLY with weekdays (Plan-B Pro #4) takes, in each month of the rule's calendar, the
 *   days with those weekdays; [RecurrenceRule.setPosition] picks the n-th of them (negative
 *   counts from the month's end). A month that has no such day (a fifth Friday) is skipped,
 *   never replaced by another day.
 * - WEEKLY counts "every N weeks" in weeks starting on [RecurrenceRule.weekStart], or the
 *   calendar's default first day (Saturday for Jalali, Monday for Gregorian).
 * - [RecurrenceBasis.COMPLETION] rules have no fixed schedule: [nextAfterCompletion] gives the
 *   next date from the completion day. Read as a schedule (calendar views) they behave like
 *   the same rule with a fixed schedule.
 */
object RecurrenceEngine {
    private const val SAFETY_LIMIT = 200_000

    /** Months examined at most for a weekday-of-month rule (a thousand years). */
    private const val MONTH_LIMIT = 12_000

    fun occurrencesBetween(
        rule: RecurrenceRule,
        anchor: LocalDate,
        from: LocalDate,
        to: LocalDate,
        maxResults: Int = 5_000,
    ): List<LocalDate> {
        if (to < from) return emptyList()
        val result = ArrayList<LocalDate>()
        for (date in sequence(rule, anchor)) {
            if (date > to) break
            if (date >= from) {
                result += date
                if (result.size >= maxResults) break
            }
        }
        return result
    }

    /** The first occurrence strictly after [after], or null if the series has ended. */
    fun nextOccurrence(rule: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate? =
        sequence(rule, anchor).firstOrNull { it > after }

    /** The first occurrence on or after [date]. */
    fun firstOnOrAfter(rule: RecurrenceRule, anchor: LocalDate, date: LocalDate): LocalDate? =
        sequence(rule, anchor).firstOrNull { it >= date }

    fun occursOn(rule: RecurrenceRule, anchor: LocalDate, date: LocalDate): Boolean =
        firstOnOrAfter(rule, anchor, date) == date

    /** Lazily generates every occurrence in order, respecting count and until. */
    fun sequence(rule: RecurrenceRule, anchor: LocalDate): Sequence<LocalDate> {
        val raw = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> generateSequence(0L) { it + 1 }
                .map { anchor.plusDays(it * rule.interval) }
            RecurrenceFrequency.WEEKLY -> weekly(rule, anchor)
            RecurrenceFrequency.MONTHLY -> if (rule.weekdays.isNotEmpty()) {
                monthlyByWeekday(rule, anchor)
            } else {
                val engine = CalendarEngines.of(rule.calendarSystem)
                generateSequence(0) { it + 1 }.map { engine.plusMonths(anchor, it * rule.interval) }
            }
            RecurrenceFrequency.YEARLY -> {
                val engine = CalendarEngines.of(rule.calendarSystem)
                generateSequence(0) { it + 1 }.map { engine.plusYears(anchor, it * rule.interval) }
            }
        }
        var seq = raw.take(SAFETY_LIMIT)
        rule.until?.let { until -> seq = seq.takeWhile { it <= until } }
        rule.count?.let { count -> seq = seq.take(count) }
        return seq
    }

    /**
     * The first date after a completion on [completedOn] for a [RecurrenceBasis.COMPLETION]
     * rule: [RecurrenceRule.interval] days, weeks, months or years later (months and years in
     * the rule's calendar, clamping the day). Null once past [RecurrenceRule.until]. The count
     * is kept by the caller (each new occurrence carries one less).
     */
    fun nextAfterCompletion(rule: RecurrenceRule, completedOn: LocalDate): LocalDate? {
        val engine = CalendarEngines.of(rule.calendarSystem)
        val next = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> completedOn.plusDays(rule.interval.toLong())
            RecurrenceFrequency.WEEKLY -> completedOn.plusWeeks(rule.interval.toLong())
            RecurrenceFrequency.MONTHLY -> engine.plusMonths(completedOn, rule.interval)
            RecurrenceFrequency.YEARLY -> engine.plusYears(completedOn, rule.interval)
        }
        return next.takeUnless { rule.until != null && it > rule.until }
    }

    /** The default first day of the week of a calendar (used when a rule has no WKST). */
    fun defaultWeekStart(system: CalendarSystem): DayOfWeek =
        if (system == CalendarSystem.JALALI) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

    /**
     * The days of [month] (in [engine]'s calendar) whose weekday is in [weekdays], in order, or
     * only the [position]-th of them (1-based; negative counts from the end). Empty when the
     * month has no such day.
     */
    fun weekdaysInMonth(engine: CalendarEngine, month: CalendarMonth, weekdays: Set<DayOfWeek>, position: Int?): List<LocalDate> {
        val first = engine.firstDayOfMonth(month)
        val last = engine.lastDayOfMonth(month)
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.filter { it.dayOfWeek in weekdays }.toList()
        if (position == null) return days
        val index = if (position > 0) position - 1 else days.size + position
        return listOfNotNull(days.getOrNull(index))
    }

    private fun monthlyByWeekday(rule: RecurrenceRule, anchor: LocalDate): Sequence<LocalDate> {
        val engine = CalendarEngines.of(rule.calendarSystem)
        val firstMonth = engine.monthOf(anchor)
        return generateSequence(0) { it + 1 }.take(MONTH_LIMIT).flatMap { index ->
            weekdaysInMonth(engine, firstMonth.plus(index * rule.interval), rule.weekdays, rule.setPosition)
                .asSequence()
                .filter { it >= anchor }
        }
    }

    private fun weekly(rule: RecurrenceRule, anchor: LocalDate): Sequence<LocalDate> {
        val days = rule.weekdays.ifEmpty { setOf(anchor.dayOfWeek) }
        val weekStartDay = rule.weekStart ?: defaultWeekStart(rule.calendarSystem)
        val firstWeek = MonthGrid.weekStart(anchor, weekStartDay)
        return generateSequence(0L) { it + 1 }.flatMap { weekIndex ->
            val weekStart = firstWeek.plusWeeks(weekIndex * rule.interval)
            (0L until 7L).asSequence()
                .map { weekStart.plusDays(it) }
                .filter { it.dayOfWeek in days && it >= anchor }
        }
    }
}
