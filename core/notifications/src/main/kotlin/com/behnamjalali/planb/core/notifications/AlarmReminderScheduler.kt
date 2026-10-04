package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusStatus
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Schedules one alarm per item via [AlarmManager]. Exact alarms are used when
 * the user allowed them (planner/calendar reminders are time-critical); otherwise
 * an inexact while-idle alarm is used so reminders still arrive.
 *
 * Syncing an item after a data change replaces its alarm and removes a notification
 * that no longer applies. After an alarm was delivered, only the alarm is renewed
 * ([renewTaskAlarm] and friends): the notification that was just posted must stay.
 *
 * Repositories are injected lazily because they themselves depend on this scheduler.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: Lazy<TaskRepository>,
    private val events: Lazy<EventRepository>,
    private val habits: Lazy<HabitRepository>,
    private val focus: Lazy<FocusRepository>,
    private val notifier: Notifier,
    private val time: TimeProvider,
) : ReminderScheduler {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun syncTask(taskId: EntityId) {
        if (!schedule(ReminderKind.TASK, taskId, planTask(taskId))) cancel(ReminderKind.TASK, taskId)
    }

    override suspend fun syncEvent(eventId: EntityId) {
        if (!schedule(ReminderKind.EVENT, eventId, planEvent(eventId))) cancel(ReminderKind.EVENT, eventId)
    }

    override suspend fun syncHabit(habitId: EntityId) {
        if (!schedule(ReminderKind.HABIT, habitId, planHabit(habitId))) cancel(ReminderKind.HABIT, habitId)
    }

    /** After delivery: schedules the next reminder (recurring items) or clears the alarm, keeping the notification. */
    suspend fun renewTaskAlarm(taskId: EntityId) {
        if (!schedule(ReminderKind.TASK, taskId, planTask(taskId))) cancelAlarm(ReminderKind.TASK, taskId)
    }

    suspend fun renewEventAlarm(eventId: EntityId) {
        if (!schedule(ReminderKind.EVENT, eventId, planEvent(eventId))) cancelAlarm(ReminderKind.EVENT, eventId)
    }

    suspend fun renewHabitAlarm(habitId: EntityId) {
        if (!schedule(ReminderKind.HABIT, habitId, planHabit(habitId))) cancelAlarm(ReminderKind.HABIT, habitId)
    }

    private suspend fun planTask(taskId: EntityId): PlannedReminder? =
        tasks.get().getTask(taskId)?.let { ReminderPlanner.forTask(it, time.now(), time.zone()) }

    private suspend fun planEvent(eventId: EntityId): PlannedReminder? =
        events.get().getEvent(eventId)?.let { ReminderPlanner.forEvent(it, time.now(), time.zone()) }

    private suspend fun planHabit(habitId: EntityId): PlannedReminder? =
        habits.get().getHabit(habitId)?.let {
            ReminderPlanner.forHabit(it, habits.get().amountOn(habitId, time.today()), time.now(), time.zone())
        }

    override suspend fun cancelTask(taskId: EntityId) = cancel(ReminderKind.TASK, taskId)
    override suspend fun cancelEvent(eventId: EntityId) = cancel(ReminderKind.EVENT, eventId)
    override suspend fun cancelHabit(habitId: EntityId) = cancel(ReminderKind.HABIT, habitId)

    /**
     * Re-creates every pending alarm, including a running focus session's end. One broken
     * item never stops the others from being scheduled.
     */
    override suspend fun rescheduleAll() {
        val now = time.now()
        val zone = time.zone()
        each("task", { tasks.get().tasksWithReminders() }) { t -> ReminderPlanner.forTask(t, now, zone)?.let(::schedule) }
        each("event", { events.get().eventsWithReminders() }) { e -> ReminderPlanner.forEvent(e, now, zone)?.let(::schedule) }
        each("habit", { habits.get().habitsWithReminders() }) { h ->
            ReminderPlanner.forHabit(h, habits.get().amountOn(h.id, time.today()), now, zone)?.let(::schedule)
        }
        guarded("focus") {
            val active = focus.get().getActive()
            if (active != null && active.status == FocusStatus.RUNNING) {
                scheduleFocusEnd(now.plusMillis(active.remainingMillis(now)))
            }
        }
    }

    private suspend fun <T> each(label: String, load: suspend () -> List<T>, block: suspend (T) -> Unit) {
        val items = runCatchingLogged(label) { load() } ?: return
        items.forEach { item -> guarded(label) { block(item) } }
    }

    private suspend fun guarded(label: String, block: suspend () -> Unit) {
        runCatchingLogged(label) { block() }
    }

    private suspend fun <T> runCatchingLogged(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Only the kind of item and the error type: never titles or other user content.
        Log.w(TAG, "Could not schedule a $label reminder (${e.javaClass.simpleName})")
        null
    }

    /** Alarm for the end of a focus session. */
    override fun scheduleFocusEnd(at: Instant) = schedule(PlannedReminder(ReminderKind.FOCUS, 0, at, time.today()))

    override fun cancelFocusEnd() = cancelAlarm(ReminderKind.FOCUS, 0)

    /** Schedules [plan] when it belongs to this item; returns false when there is nothing to schedule. */
    private fun schedule(kind: ReminderKind, id: Long, plan: PlannedReminder?): Boolean {
        if (plan == null || plan.kind != kind || plan.id != id) return false
        schedule(plan)
        return true
    }

    private fun schedule(plan: PlannedReminder) {
        val manager = alarmManager ?: return
        val at = plan.at.toEpochMilli()
        val pending = pendingIntent(plan.kind, plan.id, plan.occurrenceDate.toEpochDay(), at)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Removes the alarm and any notification posted for the item. */
    private fun cancel(kind: ReminderKind, id: Long) {
        cancelAlarm(kind, id)
        if (kind != ReminderKind.FOCUS) notifier.cancel(kind, id)
    }

    private fun cancelAlarm(kind: ReminderKind, id: Long) {
        alarmManager?.cancel(pendingIntent(kind, id, 0, 0))
    }

    private fun pendingIntent(kind: ReminderKind, id: Long, occurrenceEpochDay: Long, atMillis: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            // The data URI makes each item's PendingIntent distinct for cancellation.
            .setData("planb-alarm://${kind.name.lowercase()}/$id".toUri())
            .putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_DATE, occurrenceEpochDay)
            // The intended trigger time lets the receiver drop an alarm that no longer matches the item.
            .putExtra(ReminderReceiver.EXTRA_AT, atMillis)
        return PendingIntent.getBroadcast(
            context,
            Notifier.notificationId(kind, id),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "PlanBReminders"
    }
}
