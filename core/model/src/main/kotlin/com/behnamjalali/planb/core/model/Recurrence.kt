package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Which calendar month/year arithmetic is evaluated in (Jalali months differ from Gregorian). */
enum class CalendarSystem { JALALI, GREGORIAN }

/**
 * What the next occurrence is counted from (Plan-B Pro #4). [SCHEDULE] is the classic fixed
 * schedule; [COMPLETION] counts [RecurrenceRule.interval] units from the day the previous
 * occurrence was completed ("3 days after done").
 */
enum class RecurrenceBasis { SCHEDULE, COMPLETION }

/**
 * Deterministic recurrence description, stored as a compact string (see [encode]).
 * "Weekdays" is WEEKLY with Saturday..Wednesday or Monday..Friday selected,
 * "every N days" is DAILY with [interval] N.
 *
 * Plan-B Pro #4 (advanced recurrence) adds, all optional so older rules encode unchanged:
 * - [setPosition] (`BYSETPOS`) with MONTHLY and [weekdays]: the n-th matching day of each month
 *   (1..5) or counted from its end (-1 = last, down to -5). `FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2`
 *   is "the second Monday". A month without that day (no fifth Friday) is **skipped**.
 *   MONTHLY with [weekdays] and no position repeats on every matching day of the month.
 * - [basis] (`BASIS=COMPLETION`): the next occurrence is [interval] units after completion.
 * - [weekStart] (`WKST`): the first day of the week that "every N weeks" counts weeks from;
 *   null uses the calendar's default (Saturday for Jalali, Monday for Gregorian).
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val calendarSystem: CalendarSystem = CalendarSystem.GREGORIAN,
    val until: LocalDate? = null,
    val count: Int? = null,
    val setPosition: Int? = null,
    val basis: RecurrenceBasis = RecurrenceBasis.SCHEDULE,
    val weekStart: DayOfWeek? = null,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(count == null || count >= 1) { "count must be >= 1" }
        require(setPosition == null || isValidSetPosition(setPosition)) { "setPosition must be 1..5 or -5..-1" }
    }

    /** True for rules that use Plan-B Pro #4 options (ordinal weekdays, after-completion). */
    val isAdvanced: Boolean
        get() = basis == RecurrenceBasis.COMPLETION || setPosition != null ||
            (frequency == RecurrenceFrequency.MONTHLY && weekdays.isNotEmpty())

    fun encode(): String = buildString {
        append("FREQ=").append(frequency.name)
        append(";INTERVAL=").append(interval)
        if (weekdays.isNotEmpty()) {
            append(";BYDAY=").append(weekdays.sorted().joinToString(",") { it.name.take(2) })
        }
        setPosition?.let { append(";BYSETPOS=").append(it) }
        append(";CAL=").append(calendarSystem.name)
        weekStart?.let { append(";WKST=").append(it.name.take(2)) }
        if (basis != RecurrenceBasis.SCHEDULE) append(";BASIS=").append(basis.name)
        until?.let { append(";UNTIL=").append(it) }
        count?.let { append(";COUNT=").append(it) }
    }

    companion object {
        private val dayCodes = DayOfWeek.entries.associateBy { it.name.take(2) }

        fun isValidSetPosition(position: Int): Boolean = position in 1..5 || position in -5..-1

        /**
         * Returns null for blank or malformed input instead of throwing. Unknown keys are
         * ignored; an invalid `BYSETPOS` or `BASIS` is dropped (the rule degrades to a plain one).
         */
        fun decode(value: String?): RecurrenceRule? {
            if (value.isNullOrBlank()) return null
            return runCatching {
                val parts = value.split(';').associate { part ->
                    val (k, v) = part.split('=', limit = 2)
                    k.trim() to v.trim()
                }
                RecurrenceRule(
                    frequency = RecurrenceFrequency.valueOf(parts.getValue("FREQ")),
                    interval = parts["INTERVAL"]?.toInt() ?: 1,
                    weekdays = parts["BYDAY"]?.split(',')?.mapNotNull { dayCodes[it] }?.toSet() ?: emptySet(),
                    calendarSystem = parts["CAL"]?.let { CalendarSystem.valueOf(it) } ?: CalendarSystem.GREGORIAN,
                    until = parts["UNTIL"]?.let(LocalDate::parse),
                    count = parts["COUNT"]?.toInt(),
                    setPosition = parts["BYSETPOS"]?.toIntOrNull()?.takeIf(::isValidSetPosition),
                    basis = parts["BASIS"]?.let { b -> RecurrenceBasis.entries.firstOrNull { it.name == b } } ?: RecurrenceBasis.SCHEDULE,
                    weekStart = parts["WKST"]?.let { dayCodes[it] },
                )
            }.getOrNull()
        }
    }
}
