package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Converts between Plan-B events (floating local date and times) and device events (instants
 * plus a zone). All-day events are UTC midnights, as CalendarContract requires.
 */
object EventMapping {
    private const val DEFAULT_MINUTES = 60L

    /**
     * True when a Plan-B event can be mirrored faithfully. Recurrences that a standard RRULE
     * cannot express — months and years of the Jalali calendar, and "after completion" — stay
     * in Plan-B only.
     */
    fun isMirrorable(event: CalendarEvent): Boolean = event.recurrence?.let { rrule(it) != null } ?: true

    fun toDevice(event: CalendarEvent, zone: ZoneId): DeviceEventData {
        val rule = event.recurrence?.let(::rrule)
        return if (event.allDay || event.startTime == null) {
            val start = event.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            DeviceEventData(
                title = event.title,
                description = event.description,
                startMillis = start,
                endMillis = event.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                allDay = true,
                timeZone = "UTC",
                rrule = rule,
            )
        } else {
            val start = event.date.atTime(event.startTime).atZone(zone)
            val end = event.endTime?.takeIf { it > event.startTime!! }?.let { event.date.atTime(it).atZone(zone) }
                ?: start.plusMinutes(DEFAULT_MINUTES)
            DeviceEventData(
                title = event.title,
                description = event.description,
                startMillis = start.toInstant().toEpochMilli(),
                endMillis = end.toInstant().toEpochMilli(),
                allDay = false,
                timeZone = zone.id,
                rrule = rule,
            )
        }
    }

    /**
     * Applies a device event's title, notes and times to [local]. The recurrence, color and
     * reminder stay Plan-B's own (the device rule may use features Plan-B does not have).
     */
    fun applyRemote(local: CalendarEvent, remote: DeviceEventData, zone: ZoneId): CalendarEvent {
        val title = remote.title.ifBlank { local.title }
        return if (remote.allDay) {
            local.copy(
                title = title,
                description = remote.description,
                date = Instant.ofEpochMilli(remote.startMillis).atZone(ZoneOffset.UTC).toLocalDate(),
                startTime = null,
                endTime = null,
                allDay = true,
            )
        } else {
            val start = Instant.ofEpochMilli(remote.startMillis).atZone(zone)
            val end = Instant.ofEpochMilli(remote.endMillis).atZone(zone)
            // Plan-B events stay within one day: an end on a later day becomes 23:59.
            val endTime = if (end.toLocalDate() == start.toLocalDate()) end.toLocalTime() else LocalTime.of(23, 59)
            local.copy(
                title = title,
                description = remote.description,
                date = start.toLocalDate(),
                startTime = start.toLocalTime().withSecond(0).withNano(0),
                endTime = endTime.withSecond(0).withNano(0).takeIf { it > start.toLocalTime() },
                allDay = false,
            )
        }
    }

    /** A stable fingerprint of what Plan-B syncs; a change means the device event was edited. */
    fun fingerprint(data: DeviceEventData): String {
        val text = listOf(data.title, data.description, data.startMillis, data.endMillis, data.allDay, data.rrule.orEmpty())
            .joinToString("\u001F")
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return "v1:" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /** The RFC 5545 rule for [rule], or null when it has no standard equivalent. */
    fun rrule(rule: RecurrenceRule): String? {
        if (rule.basis != RecurrenceBasis.SCHEDULE) return null
        val calendarDependent = rule.frequency == RecurrenceFrequency.MONTHLY || rule.frequency == RecurrenceFrequency.YEARLY
        if (calendarDependent && rule.calendarSystem != CalendarSystem.GREGORIAN) return null
        return buildString {
            append("FREQ=").append(rule.frequency.name)
            if (rule.interval > 1) append(";INTERVAL=").append(rule.interval)
            if (rule.weekdays.isNotEmpty()) append(";BYDAY=").append(rule.weekdays.sorted().joinToString(",") { it.name.take(2) })
            rule.setPosition?.let { append(";BYSETPOS=").append(it) }
            rule.weekStart?.let { append(";WKST=").append(it.name.take(2)) }
            rule.until?.let { append(";UNTIL=").append(it.format(UNTIL_FORMAT)) }
            rule.count?.let { append(";COUNT=").append(it) }
        }
    }

    private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /** The local date an instant of a device event falls on (all-day instants are UTC midnights). */
    fun localDate(millis: Long, allDay: Boolean, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}
