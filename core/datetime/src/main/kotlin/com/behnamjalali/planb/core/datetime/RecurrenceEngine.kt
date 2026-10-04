package com.behnamjalali.planb.core.datetime

import com.behnamjalali.planb.core.model.CalendarSystem
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
 */
object RecurrenceEngine {
    private const val SAFETY_LIMIT = 200_000

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
            RecurrenceFrequency.MONTHLY -> {
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

    private fun weekly(rule: RecurrenceRule, anchor: LocalDate): Sequence<LocalDate> {
        val days = rule.weekdays.ifEmpty { setOf(anchor.dayOfWeek) }
        val weekStartDay = if (rule.calendarSystem == CalendarSystem.JALALI) DayOfWeek.SATURDAY else DayOfWeek.MONDAY
        val firstWeek = MonthGrid.weekStart(anchor, weekStartDay)
        return generateSequence(0L) { it + 1 }.flatMap { weekIndex ->
            val weekStart = firstWeek.plusWeeks(weekIndex * rule.interval)
            (0L until 7L).asSequence()
                .map { weekStart.plusDays(it) }
                .filter { it.dayOfWeek in days && it >= anchor }
        }
    }
}
