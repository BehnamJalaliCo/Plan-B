package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.CalendarSyncSettings
import com.behnamjalali.planb.core.model.EntityId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/** A device calendar event shown (read only) in Plan-B's calendar views. */
data class DeviceCalendarItem(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    /** The day this appearance is shown on (an event spanning days appears on each). */
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val allDay: Boolean,
    /** ARGB color of the event or its calendar. */
    val color: Int,
    val calendarName: String,
)

/** Device events for the calendar views; empty while sync is off or not allowed. */
interface DeviceCalendarSource {
    fun observeItems(from: LocalDate, to: LocalDate): Flow<List<DeviceCalendarItem>>

    /** Copies a device event into Plan-B (an unlinked copy; the device event is left alone). */
    suspend fun import(item: DeviceCalendarItem): EntityId?

    companion object {
        val None = object : DeviceCalendarSource {
            override fun observeItems(from: LocalDate, to: LocalDate): Flow<List<DeviceCalendarItem>> = flowOf(emptyList())
            override suspend fun import(item: DeviceCalendarItem): EntityId? = null
        }
    }
}

/** Settings and actions of device calendar sync (Plan-B Pro #3) for the settings screen. */
@Singleton
class CalendarSyncRepository @Inject constructor(
    private val store: DeviceCalendarStore,
    private val engine: CalendarSyncEngine,
    private val preferences: UserPreferencesDataSource,
    private val db: PlanBDatabase,
    private val events: EventRepository,
    private val time: TimeProvider,
    @param:Named(CalendarSyncEngine.PACKAGE_NAME) private val packageName: String,
) : DeviceCalendarSource {
    val settings: Flow<CalendarSyncSettings> = preferences.calendarSync

    fun hasPermission(): Boolean = store.hasPermission()

    /** Device calendars, refreshed when the provider changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCalendars(): Flow<List<DeviceCalendar>> =
        store.changes().onStart { emit(Unit) }.mapLatest { store.calendars() }

    /**
     * Turns sync on (after the permission was granted). The first time, every device calendar
     * is shown; nothing is written until a target calendar is chosen.
     */
    suspend fun enable() {
        val calendars = store.calendars()
        preferences.updateCalendarSync { current ->
            val firstTime = current.visibleCalendarIds.isEmpty() && current.targetCalendarId == null
            current.copy(
                enabled = true,
                visibleCalendarIds = if (firstTime) calendars.filterNot { it.ownedByPlanB }.map { it.id }.toSet() else current.visibleCalendarIds,
                lastSyncFailed = false,
            )
        }
        engine.sync()
    }

    /** Turns sync off. Mirrored events stay in the device calendar; nothing is deleted. */
    suspend fun disable() = preferences.updateCalendarSync { it.copy(enabled = false) }

    suspend fun setVisible(calendarId: Long, visible: Boolean) = preferences.updateCalendarSync {
        it.copy(visibleCalendarIds = if (visible) it.visibleCalendarIds + calendarId else it.visibleCalendarIds - calendarId)
    }

    suspend fun setTarget(calendarId: Long?) {
        preferences.updateCalendarSync { it.copy(targetCalendarId = calendarId) }
        engine.sync()
    }

    /** Creates the local "Plan-B" calendar and makes it the target. */
    suspend fun createPlanBCalendar(displayName: String, color: Int): Long? {
        val existing = store.calendars().firstOrNull { it.ownedByPlanB }
        val id = existing?.id ?: store.createLocalCalendar(displayName, color) ?: return null
        setTarget(id)
        return id
    }

    suspend fun syncNow(): SyncReport = engine.sync()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeItems(from: LocalDate, to: LocalDate): Flow<List<DeviceCalendarItem>> =
        combine(preferences.calendarSync, store.changes().onStart { emit(Unit) }, db.calendarLinkDao().observeAll()) { settings, _, links ->
            settings to links
        }.mapLatest { (settings, links) ->
            if (!settings.enabled || settings.visibleCalendarIds.isEmpty() || !store.hasPermission()) return@mapLatest emptyList()
            val zone = time.zone()
            val names = store.calendars().associate { it.id to it.displayName }
            val imported = links.filter { it.remoteVersion?.startsWith(CalendarSyncEngine.IMPORT_PREFIX) == true }
                .map { it.calendarId to it.externalEventId }.toSet()
            val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            store.instances(settings.visibleCalendarIds, fromMillis, toMillis)
                // Plan-B's own mirrors are already shown as Plan-B events, imported ones as copies.
                .filter { it.ownerPackage != packageName && (it.calendarId to it.eventId) !in imported }
                .flatMap { instance -> split(instance, names[instance.calendarId].orEmpty(), zone, from, to) }
        }

    /** One item per day the instance covers, within [from]..[to]. */
    private fun split(instance: DeviceEventInstance, calendarName: String, zone: java.time.ZoneId, from: LocalDate, to: LocalDate): List<DeviceCalendarItem> {
        val startDate = EventMapping.localDate(instance.beginMillis, instance.allDay, zone)
        // The end is exclusive: an all-day event ending at the next midnight covers one day.
        val lastDate = EventMapping.localDate(maxOf(instance.beginMillis, instance.endMillis - 1), instance.allDay, zone)
        val start = Instant.ofEpochMilli(instance.beginMillis).atZone(zone)
        val end = Instant.ofEpochMilli(instance.endMillis).atZone(zone)
        return generateSequence(maxOf(startDate, from)) { it.plusDays(1) }.takeWhile { it <= minOf(lastDate, to) }.map { date ->
            val timed = !instance.allDay
            DeviceCalendarItem(
                eventId = instance.eventId,
                calendarId = instance.calendarId,
                title = instance.title,
                date = date,
                startTime = if (timed && date == start.toLocalDate()) start.toLocalTime().withSecond(0).withNano(0) else if (timed) LocalTime.MIDNIGHT else null,
                endTime = if (timed && date == end.toLocalDate()) end.toLocalTime().withSecond(0).withNano(0) else if (timed) LocalTime.of(23, 59) else null,
                allDay = instance.allDay,
                color = instance.color,
                calendarName = calendarName,
            )
        }.toList()
    }

    override suspend fun import(item: DeviceCalendarItem): EntityId? {
        val links = db.calendarLinkDao()
        links.forExternal(item.calendarId, item.eventId)?.let { return it.localId }
        val remote = store.event(item.eventId) ?: return null
        val zone = time.zone()
        val blank = CalendarEvent(title = remote.data.title.ifBlank { item.title }, date = item.date)
        // The copy is one event on the day the user picked (one appearance of a recurring event).
        val copy = EventMapping.applyRemote(blank, remote.data, zone).let { if (remote.data.rrule != null) it.copy(date = item.date) else it }
        val id = events.save(copy)
        val saved = events.getEvent(id) ?: return id
        links.upsert(
            CalendarLinkEntity(
                localType = CalendarSyncEngine.LOCAL_TYPE_EVENT,
                localId = id,
                calendarId = item.calendarId,
                externalEventId = item.eventId,
                lastSyncedAt = time.now(),
                localVersion = saved.updatedAt.toEpochMilli(),
                remoteVersion = CalendarSyncEngine.IMPORT_PREFIX + EventMapping.fingerprint(remote.data),
            ),
        )
        return id
    }
}
