package com.behnamjalali.planb.core.calendarsync

import kotlinx.coroutines.flow.Flow

/** A calendar on the device (Google Calendar, Samsung, a local calendar, ...). */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    /** ARGB color as the device reports it. */
    val color: Int,
    /** The user may add events to it (access level contributor or above). */
    val writable: Boolean,
    /** The local "Plan-B" calendar this app created (no account, never synced anywhere). */
    val ownedByPlanB: Boolean = false,
)

/** The fields of a device event that Plan-B reads and writes. Times are epoch milliseconds. */
data class DeviceEventData(
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    /** IANA zone of a timed event; all-day events use UTC as CalendarContract requires. */
    val timeZone: String,
    /** RFC 5545 rule, or null for a single event. */
    val rrule: String? = null,
)

/** A device event as stored, with the marker that tells whether Plan-B created it. */
data class DeviceEvent(
    val id: Long,
    val calendarId: Long,
    val data: DeviceEventData,
    /** `CUSTOM_APP_PACKAGE`: Plan-B's package on events it created, otherwise another value or null. */
    val ownerPackage: String?,
)

/** One appearance of a device event in a time range (recurring events expand to many). */
data class DeviceEventInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val color: Int,
    val ownerPackage: String?,
)

/**
 * The device calendars (Android's CalendarContract) behind an interface, so the sync can be
 * tested with an in-memory fake. Implementations must only ever change events by id; callers
 * guarantee they touch only events Plan-B created.
 */
interface DeviceCalendarStore {
    /** READ_CALENDAR and WRITE_CALENDAR are granted. */
    fun hasPermission(): Boolean

    suspend fun calendars(): List<DeviceCalendar>

    /** The event, or null when it no longer exists (or was deleted and awaits its sync). */
    suspend fun event(eventId: Long): DeviceEvent?

    /** Inserts an event marked as Plan-B's; returns its id, or null when the provider refused. */
    suspend fun insert(calendarId: Long, data: DeviceEventData, appUri: String): Long?

    suspend fun update(eventId: Long, data: DeviceEventData): Boolean

    suspend fun delete(eventId: Long): Boolean

    suspend fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long): List<DeviceEventInstance>

    /** Creates the local "Plan-B" calendar (no account, not synced to any server). */
    suspend fun createLocalCalendar(displayName: String, color: Int): Long?

    /** Emits whenever any device calendar or event changes (silent without the permission). */
    fun changes(): Flow<Unit>
}
