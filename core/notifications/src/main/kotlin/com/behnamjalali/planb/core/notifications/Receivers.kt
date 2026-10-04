package com.behnamjalali.planb.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.data.repository.recordFocusSession
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.FocusStatus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
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
        fun stillPlanned(plan: () -> PlannedReminder?): Boolean = asOf == null || plan()?.at == plannedAt

        when (kind) {
            ReminderKind.TASK -> {
                val task = tasks.getTask(id) ?: return
                if (!task.isCompleted && !task.archived && task.deletedAt == null && task.dueDate != null && stillPlanned { ReminderPlanner.forTask(task, asOf!!, zone) }) {
                    val text = buildString {
                        append(task.title)
                        task.dueTime?.let { append(separator).append(formatter.time(it)) }
                    }
                    notifier.showReminder(kind, id, context.getString(R.string.notif_task_title), text, DeepLinks.task(id), context)
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
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Only react to the system broadcasts declared in the manifest.
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll()
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
