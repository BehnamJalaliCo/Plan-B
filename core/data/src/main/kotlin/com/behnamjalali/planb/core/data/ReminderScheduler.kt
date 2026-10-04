package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.model.EntityId

/**
 * Contract between the data layer and the platform reminder implementation
 * (:core:notifications). Repositories call it after every committed write that
 * can affect a reminder, so reminders never drift from stored data.
 */
interface ReminderScheduler {
    suspend fun syncTask(taskId: EntityId)
    suspend fun syncEvent(eventId: EntityId)
    suspend fun syncHabit(habitId: EntityId)
    suspend fun cancelTask(taskId: EntityId)
    suspend fun cancelEvent(eventId: EntityId)
    suspend fun cancelHabit(habitId: EntityId)

    /** Re-creates every pending reminder (boot, time-zone change, restore). */
    suspend fun rescheduleAll()
}
