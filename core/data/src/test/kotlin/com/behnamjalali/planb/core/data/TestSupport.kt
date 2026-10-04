package com.behnamjalali.planb.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.EntityId
import java.util.concurrent.Executors

class RecordingReminderScheduler : ReminderScheduler {
    val synced = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    var rescheduledAll = 0
    override suspend fun syncTask(taskId: EntityId) { synced += "task:$taskId" }
    override suspend fun syncEvent(eventId: EntityId) { synced += "event:$eventId" }
    override suspend fun syncHabit(habitId: EntityId) { synced += "habit:$habitId" }
    override suspend fun cancelTask(taskId: EntityId) { cancelled += "task:$taskId" }
    override suspend fun cancelEvent(eventId: EntityId) { cancelled += "event:$eventId" }
    override suspend fun cancelHabit(habitId: EntityId) { cancelled += "habit:$habitId" }
    override suspend fun rescheduleAll() { rescheduledAll++ }
    var focusEnd: java.time.Instant? = null
    override fun scheduleFocusEnd(at: java.time.Instant) { focusEnd = at }
    override fun cancelFocusEnd() { focusEnd = null }
}

object TestDatabase {
    fun create(): PlanBDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executors.newSingleThreadExecutor()
        return Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
    }
}
