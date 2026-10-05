package com.behnamjalali.planb.core.calendarsync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * [DeviceCalendarStore] over Android's calendar provider. Events Plan-B inserts carry
 * `CUSTOM_APP_PACKAGE` = Plan-B's package and a `CUSTOM_APP_URI` back to the Plan-B event.
 * Every call checks the permission first and turns provider errors into "nothing" results, so
 * a permission taken back in system settings never crashes the app.
 */
@Singleton
class ContentResolverCalendarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(PlanBDispatcher.IO) private val io: CoroutineDispatcher,
) : DeviceCalendarStore {
    private val resolver get() = context.contentResolver

    override fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> guarded(fallback: T, block: () -> T): T = withContext(io) {
        if (!hasPermission()) fallback else runCatching(block).getOrDefault(fallback)
    }

    override suspend fun calendars(): List<DeviceCalendar> = guarded(emptyList()) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val accountName = c.getString(2).orEmpty()
                    val accountType = c.getString(3).orEmpty()
                    add(
                        DeviceCalendar(
                            id = c.getLong(0),
                            displayName = c.getString(1) ?: accountName,
                            accountName = accountName,
                            accountType = accountType,
                            color = c.getInt(4),
                            writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            ownedByPlanB = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL && accountName == LOCAL_ACCOUNT,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    override suspend fun event(eventId: Long): DeviceEvent? = guarded(null) {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.CUSTOM_APP_PACKAGE,
            CalendarContract.Events.DELETED,
        )
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst() || c.getInt(11) != 0) return@use null
            val start = c.getLong(4)
            val end = if (c.isNull(5)) start + parseDuration(c.getString(6)) else c.getLong(5)
            DeviceEvent(
                id = c.getLong(0),
                calendarId = c.getLong(1),
                data = DeviceEventData(
                    title = c.getString(2).orEmpty(),
                    description = c.getString(3).orEmpty(),
                    startMillis = start,
                    endMillis = end,
                    allDay = c.getInt(7) != 0,
                    timeZone = c.getString(8) ?: "UTC",
                    rrule = c.stringOrNull(9),
                ),
                ownerPackage = c.stringOrNull(10),
            )
        }
    }

    override suspend fun insert(calendarId: Long, data: DeviceEventData, appUri: String): Long? = guarded(null) {
        val values = data.toValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, appUri)
        }
        resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let(ContentUris::parseId)
    }

    override suspend fun update(eventId: Long, data: DeviceEventData): Boolean = guarded(false) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.update(uri, data.toValues(), null, null) > 0
    }

    override suspend fun delete(eventId: Long): Boolean = guarded(false) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
    }

    override suspend fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long): List<DeviceEventInstance> =
        if (calendarIds.isEmpty()) {
            emptyList()
        } else {
            guarded(emptyList()) {
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, fromMillis)
                ContentUris.appendId(builder, toMillis)
                val projection = arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.CUSTOM_APP_PACKAGE,
                )
                val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})"
                val args = calendarIds.map { it.toString() }.toTypedArray()
                resolver.query(builder.build(), projection, selection, args, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                DeviceEventInstance(
                                    eventId = c.getLong(0),
                                    calendarId = c.getLong(1),
                                    title = c.getString(2).orEmpty(),
                                    beginMillis = c.getLong(3),
                                    endMillis = c.getLong(4),
                                    allDay = c.getInt(5) != 0,
                                    color = c.getInt(6),
                                    ownerPackage = c.stringOrNull(7),
                                ),
                            )
                        }
                    }
                }.orEmpty()
            }
        }

    override suspend fun createLocalCalendar(displayName: String, color: Int): Long? = guarded(null) {
        // A local calendar needs no account and no sync adapter of our own: the provider lets
        // any app with WRITE_CALENDAR create one when it says so for ACCOUNT_TYPE_LOCAL.
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        resolver.insert(uri, values)?.let(ContentUris::parseId)
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Without the permission the provider refuses observers; then there is nothing to watch.
        val registered = hasPermission() && runCatching {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) runCatching { resolver.unregisterContentObserver(observer) } }
    }.conflate()

    private fun DeviceEventData.toValues() = ContentValues().apply {
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
        if (rrule == null) {
            put(CalendarContract.Events.DTEND, endMillis)
            putNull(CalendarContract.Events.RRULE)
            putNull(CalendarContract.Events.DURATION)
        } else {
            // Recurring events need DURATION instead of DTEND.
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.RRULE, rrule)
            put(CalendarContract.Events.DURATION, formatDuration(endMillis - startMillis, allDay))
        }
    }

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        /** Account name of the local calendar Plan-B creates. */
        const val LOCAL_ACCOUNT = "Plan-B"

        /** RFC 5545 duration as the provider writes it ("P3600S", "P1D"); malformed values read as zero. */
        fun parseDuration(value: String?): Long {
            if (value.isNullOrBlank()) return 0
            val v = value.trim().uppercase()
            return runCatching {
                when {
                    v.contains('T') -> Duration.parse(v).toMillis()
                    v.endsWith("W") -> v.removePrefix("P").removeSuffix("W").toLong() * 7 * DAY_MS
                    v.endsWith("D") -> v.removePrefix("P").removeSuffix("D").toLong() * DAY_MS
                    v.endsWith("S") -> v.removePrefix("P").removeSuffix("S").toLong() * 1_000
                    else -> 0L
                }
            }.getOrDefault(0L)
        }

        fun formatDuration(millis: Long, allDay: Boolean): String =
            if (allDay) "P${maxOf(1, millis / DAY_MS)}D" else "P${maxOf(0, millis / 1_000)}S"

        private const val DAY_MS = 86_400_000L
    }
}
