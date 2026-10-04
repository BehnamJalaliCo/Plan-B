package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one alarm per item via [AlarmManager]. Exact alarms are used when
 * the user allowed them (planner/calendar reminders are time-critical); otherwise
 * an inexact while-idle alarm is used so reminders still arrive.
 *
 * Repositories are injected lazily because they themselves depend on this scheduler.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: Lazy<TaskRepository>,
    private val events: Lazy<EventRepository>,
    private val habits: Lazy<HabitRepository>,
    private val notifier: Notifier,
    private val time: TimeProvider,
) : ReminderScheduler {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun syncTask(taskId: EntityId) {
        val task = tasks.get().getTask(taskId)
        val plan = task?.let { ReminderPlanner.forTask(it, time.now(), time.zone()) }
        if (plan == null) cancel(ReminderKind.TASK, taskId) else schedule(plan)
    }

    override suspend fun syncEvent(eventId: EntityId) {
        val event = events.get().getEvent(eventId)
        val plan = event?.let { ReminderPlanner.forEvent(it, time.now(), time.zone()) }
        if (plan == null) cancel(ReminderKind.EVENT, eventId) else schedule(plan)
    }

    override suspend fun syncHabit(habitId: EntityId) {
        val habit = habits.get().getHabit(habitId)
        val plan = habit?.let {
            ReminderPlanner.forHabit(it, habits.get().amountOn(habitId, time.today()), time.now(), time.zone())
        }
        if (plan == null) cancel(ReminderKind.HABIT, habitId) else schedule(plan)
    }

    override suspend fun cancelTask(taskId: EntityId) = cancel(ReminderKind.TASK, taskId)
    override suspend fun cancelEvent(eventId: EntityId) = cancel(ReminderKind.EVENT, eventId)
    override suspend fun cancelHabit(habitId: EntityId) = cancel(ReminderKind.HABIT, habitId)

    override suspend fun rescheduleAll() {
        val now = time.now()
        val zone = time.zone()
        tasks.get().tasksWithReminders().forEach { t -> ReminderPlanner.forTask(t, now, zone)?.let(::schedule) }
        events.get().eventsWithReminders().forEach { e -> ReminderPlanner.forEvent(e, now, zone)?.let(::schedule) }
        habits.get().habitsWithReminders().forEach { h ->
            ReminderPlanner.forHabit(h, habits.get().amountOn(h.id, time.today()), now, zone)?.let(::schedule)
        }
    }

    /** Alarm for the end of a focus session. */
    override fun scheduleFocusEnd(at: Instant) = schedule(PlannedReminder(ReminderKind.FOCUS, 0, at, time.today()))

    override fun cancelFocusEnd() = cancel(ReminderKind.FOCUS, 0)

    private fun schedule(plan: PlannedReminder) {
        val manager = alarmManager ?: return
        val pending = pendingIntent(plan.kind, plan.id, plan.occurrenceDate.toEpochDay())
        val at = plan.at.toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(kind: ReminderKind, id: Long) {
        alarmManager?.cancel(pendingIntent(kind, id, 0))
        if (kind != ReminderKind.FOCUS) notifier.cancel(kind, id)
    }

    private fun pendingIntent(kind: ReminderKind, id: Long, occurrenceEpochDay: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            // The data URI makes each item's PendingIntent distinct for cancellation.
            .setData("planb-alarm://${kind.name.lowercase()}/$id".toUri())
            .putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_DATE, occurrenceEpochDay)
        return PendingIntent.getBroadcast(
            context,
            Notifier.notificationId(kind, id),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
