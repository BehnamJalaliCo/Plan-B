package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.CalendarEvent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one sync run did; also what the tests check. */
data class SyncReport(
    val outcome: Outcome,
    val pushedNew: Int = 0,
    val pushedChanges: Int = 0,
    val pulledChanges: Int = 0,
    val deletedRemote: Int = 0,
    val deletedLocal: Int = 0,
    val droppedLinks: Int = 0,
) {
    enum class Outcome { SYNCED, DISABLED, NOT_PRO, NO_PERMISSION }
}

/**
 * Two-way sync of Plan-B events with one device calendar (Plan-B Pro #3).
 *
 * - **Write.** Plan-B events are mirrored into the target calendar the user chose; each mirror
 *   is recorded in `calendar_links` (local id ↔ device event id). Edits and deletions on either
 *   side follow the link. Recurring events whose rule a standard RRULE cannot express (Jalali
 *   months/years, "after completion") are not mirrored.
 * - **Read.** Other device events are only displayed (see [CalendarSyncRepository]); they are
 *   never copied unless the user imports one, and an imported copy is never written back.
 * - **Never touches foreign events.** A device event is updated or deleted only through a link
 *   *and* only when it carries Plan-B's package marker. Links whose calendar no longer exists
 *   on this device (for example restored from a backup made elsewhere), or whose device event
 *   is not Plan-B's, are dropped.
 * - **Conflicts: last writer wins.** When both sides changed since the last sync, the newer
 *   change wins: the local time is the event's `updated_at`; the device provider keeps no
 *   modification time, so the device side counts from the moment Plan-B noticed the change
 *   (the change notification, or this sync). A tie keeps Plan-B's version.
 * - A device event deleted on the device deletes the Plan-B event, unless the Plan-B event was
 *   edited since the last sync (then it is written again).
 */
@Singleton
class CalendarSyncEngine @Inject constructor(
    private val store: DeviceCalendarStore,
    private val events: EventRepository,
    private val db: PlanBDatabase,
    private val preferences: UserPreferencesDataSource,
    private val pro: ProStatusSource,
    private val time: TimeProvider,
    @param:Named(PACKAGE_NAME) private val packageName: String,
) {
    private val mutex = Mutex()
    private val links get() = db.calendarLinkDao()

    /**
     * Runs one sync. [remoteNoticedAt] is when a change on the device side was first noticed
     * (null = now); it is the device side's modification time in conflicts.
     */
    suspend fun sync(remoteNoticedAt: Instant? = null): SyncReport = mutex.withLock {
        val settings = preferences.calendarSync.first()
        if (!settings.enabled) return SyncReport(SyncReport.Outcome.DISABLED)
        if (!pro.isPro()) return SyncReport(SyncReport.Outcome.NOT_PRO)
        if (!store.hasPermission()) {
            preferences.updateCalendarSync { it.copy(lastSyncFailed = true) }
            return SyncReport(SyncReport.Outcome.NO_PERMISSION)
        }
        val now = time.now()
        val remoteTime = remoteNoticedAt ?: now
        val zone = time.zone()
        val calendars = store.calendars().associateBy { it.id }
        val target = settings.targetCalendarId?.let(calendars::get)?.takeIf { it.writable }
        val counter = Counter()

        val linked = HashSet<Long>()
        for (link in links.observeAll().first()) {
            val calendar = calendars[link.calendarId]
            if (calendar == null) {
                // The calendar is gone (or the link came from another device's backup).
                links.delete(link.id)
                counter.dropped++
                continue
            }
            if (link.localType != LOCAL_TYPE_EVENT) continue
            // One broken event must not stop the others; it is retried on the next sync.
            val keep = runCatchingSafely { syncLink(link, now, remoteTime, zone, counter) }.getOrDefault(true)
            if (keep) linked += link.localId
        }

        if (target != null) {
            for (id in db.backupDao().events().map { it.id }) {
                if (id in linked) continue
                val event = events.getEvent(id) ?: continue
                if (!EventMapping.isMirrorable(event)) continue
                if (runCatchingSafely { push(event, target.id, zone, now) }.getOrNull() != null) counter.pushedNew++
            }
        }
        preferences.updateCalendarSync { it.copy(lastSyncAt = now.toEpochMilli(), lastSyncFailed = false) }
        SyncReport(
            SyncReport.Outcome.SYNCED, counter.pushedNew, counter.pushedChanges, counter.pulled,
            counter.deletedRemote, counter.deletedLocal, counter.dropped,
        )
    }

    /** Returns true when the link stays (the local event is mirrored). */
    private suspend fun syncLink(link: CalendarLinkEntity, now: Instant, remoteTime: Instant, zone: java.time.ZoneId, counter: Counter): Boolean {
        val local = events.getEvent(link.localId)
        val remote = store.event(link.externalEventId)?.takeIf { it.calendarId == link.calendarId }
        if (link.remoteVersion?.startsWith(IMPORT_PREFIX) == true) {
            // An imported copy of someone else's event: never written back.
            if (local == null) links.delete(link.id)
            return local != null
        }
        if (remote != null && remote.ownerPackage != packageName) {
            // Not an event Plan-B created (a stale link): never touch it.
            links.delete(link.id)
            counter.dropped++
            return false
        }
        val localChanged = local != null && local.updatedAt.toEpochMilli() != link.localVersion
        when {
            local == null && remote == null -> links.delete(link.id)
            local == null -> {
                // Deleted in Plan-B: delete the mirror.
                store.delete(remote!!.id)
                links.delete(link.id)
                counter.deletedRemote++
            }
            !EventMapping.isMirrorable(local) -> {
                remote?.let { store.delete(it.id) }
                links.delete(link.id)
                if (remote != null) counter.deletedRemote++
                return false
            }
            remote == null -> {
                links.delete(link.id)
                if (localChanged) {
                    // Edited in Plan-B after it was deleted on the device: write it again.
                    if (push(local, link.calendarId, zone, now) != null) counter.pushedNew++
                    return true
                }
                events.delete(local.id)
                counter.deletedLocal++
                return false
            }
            else -> {
                val remoteChanged = EventMapping.fingerprint(remote.data) != link.remoteVersion
                val pushLocal = localChanged && (!remoteChanged || !local.updatedAt.isBefore(remoteTime))
                when {
                    pushLocal -> {
                        store.update(remote.id, EventMapping.toDevice(local, zone))
                        record(link, local, store.event(remote.id), now)
                        counter.pushedChanges++
                    }
                    remoteChanged -> {
                        events.save(EventMapping.applyRemote(local, remote.data, zone))
                        record(link, events.getEvent(local.id) ?: local, remote, now)
                        counter.pulled++
                    }
                }
                return true
            }
        }
        return false
    }

    /** Inserts [event] into [calendarId] and links it; null when the provider refused. */
    private suspend fun push(event: CalendarEvent, calendarId: Long, zone: java.time.ZoneId, now: Instant): Long? {
        val id = store.insert(calendarId, EventMapping.toDevice(event, zone), "planb://open/event/${event.id}") ?: return null
        val stored = store.event(id)
        links.upsert(
            CalendarLinkEntity(
                localType = LOCAL_TYPE_EVENT,
                localId = event.id,
                calendarId = calendarId,
                externalEventId = id,
                lastSyncedAt = now,
                localVersion = event.updatedAt.toEpochMilli(),
                remoteVersion = stored?.let { EventMapping.fingerprint(it.data) },
            ),
        )
        return id
    }

    private suspend fun record(link: CalendarLinkEntity, local: CalendarEvent, remote: DeviceEvent?, now: Instant) {
        links.upsert(
            link.copy(
                lastSyncedAt = now,
                localVersion = local.updatedAt.toEpochMilli(),
                remoteVersion = remote?.let { EventMapping.fingerprint(it.data) } ?: link.remoteVersion,
            ),
        )
    }

    private class Counter {
        var pushedNew = 0
        var pushedChanges = 0
        var pulled = 0
        var deletedRemote = 0
        var deletedLocal = 0
        var dropped = 0
    }

    companion object {
        const val PACKAGE_NAME = "planb_calendar_sync_package"
        const val LOCAL_TYPE_EVENT = "EVENT"

        /** `remote_version` of a link made by importing a device event (never written back). */
        const val IMPORT_PREFIX = "import:"
    }
}
