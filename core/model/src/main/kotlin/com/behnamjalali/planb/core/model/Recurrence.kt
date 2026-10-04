package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Which calendar month/year arithmetic is evaluated in (Jalali months differ from Gregorian). */
enum class CalendarSystem { JALALI, GREGORIAN }

/**
 * Deterministic recurrence description, stored as a compact string (see [encode]).
 * "Weekdays" is WEEKLY with Saturday..Wednesday or Monday..Friday selected,
 * "every N days" is DAILY with [interval] N.
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val calendarSystem: CalendarSystem = CalendarSystem.GREGORIAN,
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(count == null || count >= 1) { "count must be >= 1" }
    }

    fun encode(): String = buildString {
        append("FREQ=").append(frequency.name)
        append(";INTERVAL=").append(interval)
        if (weekdays.isNotEmpty()) {
            append(";BYDAY=").append(weekdays.sorted().joinToString(",") { it.name.take(2) })
        }
        append(";CAL=").append(calendarSystem.name)
        until?.let { append(";UNTIL=").append(it) }
        count?.let { append(";COUNT=").append(it) }
    }

    companion object {
        private val dayCodes = DayOfWeek.entries.associateBy { it.name.take(2) }

        /** Returns null for blank or malformed input instead of throwing. */
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
                )
            }.getOrNull()
        }
    }
}
