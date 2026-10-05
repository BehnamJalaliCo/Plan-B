package com.behnamjalali.planb.core.calendarsync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * An in-memory calendar provider for tests and screenshots (like `FakeBillingClient`): it
 * behaves like CalendarContract for what Plan-B uses, including events of other apps, deleted
 * calendars and a permission that can be taken away.
 */
class FakeDeviceCalendarStore(
    calendars: List<DeviceCalendar> = emptyList(),
    var permission: Boolean = true,
    private val ownPackage: String = "com.behnamjalali.planb",
) : DeviceCalendarStore {
    val calendars = calendars.toMutableList()
    val events = linkedMapOf<Long, DeviceEvent>()
    private var nextId = 1000L
    private val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    /** Every insert, update and delete Plan-B made, for assertions ("insert:12", "update:12"). */
    val writes = mutableListOf<String>()

    override fun hasPermission(): Boolean = permission

    override suspend fun calendars(): List<DeviceCalendar> = if (permission) calendars.toList() else emptyList()

    override suspend fun event(eventId: Long): DeviceEvent? = if (permission) events[eventId] else null

    override suspend fun insert(calendarId: Long, data: DeviceEventData, appUri: String): Long? {
        if (!permission || calendars.none { it.id == calendarId }) return null
        val id = nextId++
        events[id] = DeviceEvent(id, calendarId, data, ownPackage)
        writes += "insert:$id"
        notifyChange()
        return id
    }

    override suspend fun update(eventId: Long, data: DeviceEventData): Boolean {
        val existing = events[eventId] ?: return false
        if (!permission) return false
        events[eventId] = existing.copy(data = data)
        writes += "update:$eventId"
        notifyChange()
        return true
    }

    override suspend fun delete(eventId: Long): Boolean {
        if (!permission || events.remove(eventId) == null) return false
        writes += "delete:$eventId"
        notifyChange()
        return true
    }

    override suspend fun instances(calendarIds: Set<Long>, fromMillis: Long, toMillis: Long): List<DeviceEventInstance> {
        if (!permission) return emptyList()
        return events.values
            .filter { it.calendarId in calendarIds && it.data.startMillis < toMillis && it.data.endMillis > fromMillis }
            .sortedBy { it.data.startMillis }
            .map { e ->
                DeviceEventInstance(
                    eventId = e.id,
                    calendarId = e.calendarId,
                    title = e.data.title,
                    beginMillis = e.data.startMillis,
                    endMillis = e.data.endMillis,
                    allDay = e.data.allDay,
                    color = calendars.firstOrNull { it.id == e.calendarId }?.color ?: 0,
                    ownerPackage = e.ownerPackage,
                )
            }
    }

    override suspend fun createLocalCalendar(displayName: String, color: Int): Long? {
        if (!permission) return null
        val id = (calendars.maxOfOrNull { it.id } ?: 0) + 1
        calendars += DeviceCalendar(id, displayName, ContentResolverCalendarStore.LOCAL_ACCOUNT, "LOCAL", color, writable = true, ownedByPlanB = true)
        notifyChange()
        return id
    }

    override fun changes(): Flow<Unit> = changeFlow

    /** An edit made by the user in another calendar app (not by Plan-B). */
    fun editOnDevice(eventId: Long, transform: (DeviceEventData) -> DeviceEventData) {
        val existing = events.getValue(eventId)
        events[eventId] = existing.copy(data = transform(existing.data))
        notifyChange()
    }

    /** An event created by another app (no Plan-B marker). */
    fun addForeign(calendarId: Long, data: DeviceEventData, ownerPackage: String? = "com.google.android.calendar"): Long {
        val id = nextId++
        events[id] = DeviceEvent(id, calendarId, data, ownerPackage)
        notifyChange()
        return id
    }

    fun deleteOnDevice(eventId: Long) {
        events.remove(eventId)
        notifyChange()
    }

    fun removeCalendar(calendarId: Long) {
        calendars.removeAll { it.id == calendarId }
        events.values.removeAll { it.calendarId == calendarId }
        notifyChange()
    }

    private fun notifyChange() {
        changeFlow.tryEmit(Unit)
    }
}
