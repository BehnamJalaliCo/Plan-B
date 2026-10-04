package com.behnamjalali.planb.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.datetime.ReminderTime
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fires a reminder, re-validating the item first so stale alarms never notify. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var events: EventRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var focus: FocusRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: AlarmReminderScheduler
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val occurrence = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, time.today().toEpochDay()))
        val pending = goAsync()
        scope.launch {
            try {
                handle(context, kind, id, occurrence)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, kind: ReminderKind, id: Long, occurrence: LocalDate) {
        when (kind) {
            ReminderKind.TASK -> {
                val task = tasks.getTask(id) ?: return
                if (task.isCompleted || task.archived || task.dueDate == null) return
                val due = task.dueDate!!
                val text = buildString {
                    append(task.title)
                    task.dueTime?.let { append(" · ").append(it.toString()) }
                }
                notifier.showReminder(kind, id, context.getString(R.string.notif_task_title), text, DeepLinks.task(id))
                if (ReminderTime.triggerAt(due, task.dueTime, task.reminderOffsetMinutes ?: 0, time.zone()).isAfter(time.now())) {
                    scheduler.syncTask(id)
                }
            }
            ReminderKind.EVENT -> {
                val event = events.getEvent(id) ?: return
                val text = listOfNotNull(event.title, event.startTime?.toString()).joinToString(" · ")
                notifier.showReminder(kind, id, context.getString(R.string.notif_event_title), text, DeepLinks.event(id))
                // Recurring events: schedule the next occurrence.
                scheduler.syncEvent(id)
            }
            ReminderKind.HABIT -> {
                val habit = habits.getHabit(id) ?: return
                if (habits.amountOn(id, occurrence) < habit.target) {
                    notifier.showReminder(
                        kind, id, context.getString(R.string.notif_habit_title),
                        context.getString(R.string.notif_habit_text, habit.title), DeepLinks.habit(id),
                    )
                }
                scheduler.syncHabit(id)
            }
            ReminderKind.FOCUS -> {
                val completed = focus.completeIfElapsed()
                if (completed != null && completed.endedAt != null) notifier.showFocusComplete()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.behnamjalali.planb.action.REMIND"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_DATE = "date"
    }
}

/** Restores reminders after reboot, app update, clock or time-zone changes. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                runCatching { scheduler.rescheduleAll() }
            } finally {
                pending.finish()
            }
        }
    }
}
