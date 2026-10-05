package com.behnamjalali.planb.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import androidx.core.net.toUri
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskPlanningRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.data.repository.recordFocusSession
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.TaskReminderRules
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "PlanBReminders"

/**
 * What a fired reminder alarm does, apart from the receiver so it can be tested:
 * - the item is re-read, and an alarm that no longer matches the item's planned reminder
 *   (changed reminder, deleted and restored item) is dropped instead of notifying;
 * - after posting, only the item's alarm is renewed (next occurrence or none). Syncing
 *   would also clear the notification that was just posted.
 */
class ReminderDelivery @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tasks: TaskRepository,
    private val events: EventRepository,
    private val habits: HabitRepository,
    private val focus: FocusRepository,
    private val notifier: Notifier,
    private val scheduler: AlarmReminderScheduler,
    private val time: TimeProvider,
    private val settings: SettingsRepository,
    private val planning: TaskPlanningRepository,
) {
    /** [plannedAt] is the trigger time the alarm was set for (null for alarms set by older versions). */
    suspend fun deliver(kind: ReminderKind, id: Long, occurrence: LocalDate, plannedAt: Instant?) {
        // Receivers run without an activity, so apply the user's language explicitly.
        val prefs = settings.current()
        val context = notifier.localizedContext(prefs.language.tag)
        val formatter = PlannerDateFormatter(
            context.resources, prefs.calendarSystem, prefs.firstDayOfWeek,
            NumberFormatter(prefs.usePersianDigits), DateFormat.is24HourFormat(appContext),
        )
        val separator = context.getString(R.string.notif_separator)
        val zone = time.zone()
        // The plan as seen just before the alarm's time must name this very alarm.
        val asOf = plannedAt?.minusMillis(1)
        suspend fun stillPlanned(plan: suspend () -> PlannedReminder?): Boolean = asOf == null || plan()?.at == plannedAt

        when (kind) {
            ReminderKind.TASK -> {
                val task = tasks.getTask(id) ?: return
                // Alarms from older versions (no intended time) were only set for a due date.
                val live = !task.isCompleted && !task.archived && task.deletedAt == null && (task.dueDate != null || plannedAt != null)
                if (live && stillPlanned { scheduler.planTask(task, asOf!!) }) {
                    val text = buildString {
                        append(task.title)
                        task.dueTime?.let { append(separator).append(formatter.time(it)) }
                    }
                    val planning = planning.planning(id)
                    // "Done"/"Snooze" come with Plan-B Pro reminders (extra ones or nagging) only.
                    val buttons = if (task.nag || planning.reminders.isNotEmpty()) {
                        TaskReminderButtons(
                            doneLabel = context.getString(R.string.notif_action_done),
                            snoozeLabel = context.getString(R.string.notif_action_snooze, formatter.numbers.format(TaskReminderRules.SNOOZE_MINUTES)),
                            nagging = task.nag,
                        )
                    } else {
                        null
                    }
                    notifier.showReminder(kind, id, context.getString(R.string.notif_task_title), text, DeepLinks.task(id), context, buttons)
                }
                scheduler.renewTaskAlarm(id)
            }
            ReminderKind.EVENT -> {
                val event = events.getEvent(id) ?: return
                if (stillPlanned { ReminderPlanner.forEvent(event, asOf!!, zone) }) {
                    val text = listOfNotNull(event.title, event.startTime?.let(formatter::time)).joinToString(separator)
                    notifier.showReminder(kind, id, context.getString(R.string.notif_event_title), text, DeepLinks.event(id), context)
                }
                // Recurring events: the next occurrence's alarm.
                scheduler.renewEventAlarm(id)
            }
            ReminderKind.HABIT -> {
                val habit = habits.getHabit(id) ?: return
                // Whether today's target is met is checked separately below.
                val planned = stillPlanned { ReminderPlanner.forHabit(habit, 0, asOf!!, zone) }
                if (planned && habits.amountOn(id, occurrence) < habit.target) {
                    notifier.showReminder(
                        kind, id, context.getString(R.string.notif_habit_title),
                        context.getString(R.string.notif_habit_text, habit.title), DeepLinks.habit(id), context,
                    )
                }
                scheduler.renewHabitAlarm(id)
            }
            ReminderKind.FOCUS -> {
                val session = focus.completeIfElapsed() ?: return
                if (session.status == FocusStatus.COMPLETED) {
                    tasks.recordFocusSession(session)
                    notifier.showFocusComplete(context)
                } else if (session.status == FocusStatus.RUNNING) {
                    // Fired before the session's end (e.g. the clock changed): wait for the real end.
                    val now = time.now()
                    scheduler.scheduleFocusEnd(now.plusMillis(session.remainingMillis(now)))
                }
            }
        }
    }
}

/** Fires a reminder, re-validating the item first so stale alarms never notify. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var delivery: ReminderDelivery
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val occurrence = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, time.today().toEpochDay()))
        val plannedAt = intent.getLongExtra(EXTRA_AT, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        val pending = goAsync()
        scope.launch {
            try {
                delivery.deliver(kind, id, occurrence, plannedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed delivery must not take the process down. Never log item content.
                Log.w(TAG, "Could not deliver a ${kind.name} reminder (${e.javaClass.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.behnamjalali.planb.action.REMIND"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_DATE = "date"
        const val EXTRA_AT = "at"
    }
}

/** Restores reminders after reboot, app update, clock or time-zone changes. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }

    @Inject lateinit var scheduler: AlarmReminderScheduler
    @Inject lateinit var rituals: RitualReminders
    @Inject lateinit var journal: JournalReminders
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Only react to the system broadcasts declared in the manifest.
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll()
                rituals.sync()
                journal.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore reminders (${e.javaClass.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * What the "Done", "Snooze" and swipe-away of a task reminder do (Plan-B Pro #12), apart from
 * the receiver so it can be tested. Completing goes through the repository like a tap in the
 * app (a recurring task continues; a blocked task is completed anyway, the user asked for it).
 */
class ReminderActions @Inject constructor(
    private val tasks: TaskRepository,
    private val notifier: Notifier,
    private val scheduler: AlarmReminderScheduler,
    private val nagState: NagStateStore,
    private val time: TimeProvider,
) {
    suspend fun done(taskId: Long) {
        nagState.clear(taskId)
        tasks.setCompleted(taskId, true)
        // The repository syncs after its write too; this makes sure alarm and notification are gone.
        scheduler.syncTask(taskId)
        notifier.cancel(ReminderKind.TASK, taskId)
    }

    /** One more reminder in [TaskReminderRules.SNOOZE_MINUTES]; earlier ones stop nagging. */
    suspend fun snooze(taskId: Long) {
        val now = time.now()
        nagState.snooze(taskId, now, now.plus(Duration.ofMinutes(TaskReminderRules.SNOOZE_MINUTES.toLong())))
        notifier.cancel(ReminderKind.TASK, taskId)
        scheduler.renewTaskAlarm(taskId)
    }

    /** The notification was swiped away: the reminders that fired stop nagging. */
    suspend fun dismiss(taskId: Long) {
        nagState.stop(taskId, time.now())
        scheduler.renewTaskAlarm(taskId)
    }
}

/** Handles the buttons of task reminders; not exported, reached only through our own PendingIntents. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ACTION_DONE -> actions.done(taskId)
                    ACTION_SNOOZE -> actions.snooze(taskId)
                    ACTION_DISMISS -> actions.dismiss(taskId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never log item content.
                Log.w(TAG, "Could not handle a reminder action (${e.javaClass.simpleName})")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.behnamjalali.planb.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.behnamjalali.planb.action.REMINDER_SNOOZE"
        const val ACTION_DISMISS = "com.behnamjalali.planb.action.REMINDER_DISMISS"
        const val EXTRA_TASK_ID = "task_id"
        private val HANDLED = setOf(ACTION_DONE, ACTION_SNOOZE, ACTION_DISMISS)

        /** Distinct per task and button, so each PendingIntent is its own. */
        fun dataUri(action: String, taskId: Long): Uri = "planb-action://${action.substringAfterLast('_').lowercase()}/$taskId".toUri()
    }
}
