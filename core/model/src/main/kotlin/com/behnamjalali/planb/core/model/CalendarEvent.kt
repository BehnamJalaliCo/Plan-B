package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Events use "floating" local date/time: 09:00 stays 09:00 in whatever time zone
 * the device is in, which matches personal planning expectations.
 */
data class CalendarEvent(
    val id: EntityId = NEW_ID,
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val allDay: Boolean = startTime == null,
    val reminderOffsetMinutes: Int? = null,
    val recurrence: RecurrenceRule? = null,
    val color: AccentColor = AccentColor.POWDER_BLUE,
    val notes: String = "",
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
)

/** A concrete (possibly recurring) appearance of an event on a given day. */
data class EventOccurrence(
    val event: CalendarEvent,
    val date: LocalDate,
)
