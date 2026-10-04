package com.behnamjalali.planb.core.datetime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderTime {
    /** Time used for date-only items that have a reminder. */
    val DEFAULT_DATE_ONLY_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * Converts a floating local date/time minus an offset to an absolute instant.
     * DST gaps resolve forward (java.time semantics), overlaps use the earlier offset.
     */
    fun triggerAt(date: LocalDate, time: LocalTime?, offsetMinutes: Int, zone: ZoneId): Instant =
        ZonedDateTime.of(date, time ?: DEFAULT_DATE_ONLY_TIME, zone)
            .minusMinutes(offsetMinutes.toLong())
            .toInstant()
}
