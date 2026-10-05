package com.behnamjalali.planb.core.notifications

import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.datetime.ReminderTime
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskPlanning
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskReminderRules
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A single scheduled alarm: what it is for and when it fires. */
data class PlannedReminder(val kind: ReminderKind, val id: Long, val at: Instant, val occurrenceDate: LocalDate)

enum class ReminderKind(val code: Int) { TASK(1), EVENT(2), HABIT(3), FOCUS(4) }

/**
 * Device-only state of a task's nagging reminder (Plan-B Pro #12): [stoppedAt] when the user
 * dismissed the notification or snoozed it (reminders that fired before stop nagging), and
 * [snoozedUntil], an extra one-off reminder set by "Snooze".
 */
data class NagState(val stoppedAt: Instant? = null, val snoozedUntil: Instant? = null)

/**
 * Pure calculation of the next reminder for an item. Returns null when no
 * future reminder exists (completed, archived, in the trash, past, or no reminder set).
 */
object ReminderPlanner {
    private const val EVENT_LOOKAHEAD_DAYS = 400L

    /**
     * The task's next reminder: the earliest future one of the primary reminder, the extra
     * reminders in [planning] (Plan-B Pro #12) and a snooze. With [Task.nag], every reminder
     * that already fired (and was not stopped, see [NagState]) repeats every
     * [TaskPlanning.nagIntervalMinutes] up to [TaskReminderRules.NAG_REPEATS] times, until the
     * task is completed. One alarm per task always holds this next moment, so the stale-alarm
     * check (the alarm's intended time must equal the plan just before it) covers all of them.
     */
    fun forTask(
        task: Task,
        now: Instant,
        zone: ZoneId,
        planning: TaskPlanning = TaskPlanning(),
        nag: NagState = NagState(),
    ): PlannedReminder? {
        if (task.isCompleted || task.archived || task.deletedAt != null) return null
        val due = task.dueDate
        val deadline = task.deadline
        val bases = buildList {
            val primary = task.reminderOffsetMinutes
            if (primary != null && due != null) add(ReminderTime.triggerAt(due, task.dueTime, primary, zone))
            planning.reminders.forEach { reminder ->
                val offset = reminder.offsetMinutes
                when (reminder.kind) {
                    TaskReminderKind.OFFSET -> if (offset != null && due != null) add(ReminderTime.triggerAt(due, task.dueTime, offset, zone))
                    TaskReminderKind.DEADLINE -> if (offset != null && deadline != null) add(ReminderTime.triggerAt(deadline, null, offset, zone))
                    TaskReminderKind.ABSOLUTE -> reminder.at?.let(::add)
                }
            }
            nag.snoozedUntil?.let(::add)
        }
        val candidates = bases.filter { it.isAfter(now) }.toMutableList()
        if (task.nag) {
            val step = Duration.ofMinutes(TaskReminderRules.normalizeNagInterval(planning.nagIntervalMinutes).toLong())
            val stopped = nag.stoppedAt
            bases.filter { !it.isAfter(now) && (stopped == null || it.isAfter(stopped)) }.forEach { fired ->
                (1..TaskReminderRules.NAG_REPEATS).asSequence()
                    .map { fired.plus(step.multipliedBy(it.toLong())) }
                    .firstOrNull { it.isAfter(now) }
                    ?.let(candidates::add)
            }
        }
        val at = candidates.minOrNull() ?: return null
        return PlannedReminder(ReminderKind.TASK, task.id, at, due ?: deadline ?: at.atZone(zone).toLocalDate())
    }

    fun forEvent(event: CalendarEvent, now: Instant, zone: ZoneId): PlannedReminder? {
        val offset = event.reminderOffsetMinutes ?: return null
        val today = now.atZone(zone).toLocalDate()
        val time = if (event.allDay) null else event.startTime
        val rule = event.recurrence
        val dates = if (rule == null) {
            listOf(event.date)
        } else {
            RecurrenceEngine.occurrencesBetween(rule, event.date, today.minusDays(1), today.plusDays(EVENT_LOOKAHEAD_DAYS), maxResults = 500)
        }
        return dates.asSequence()
            .map { date -> date to ReminderTime.triggerAt(date, time, offset, zone) }
            .firstOrNull { (_, at) -> at.isAfter(now) }
            ?.let { (date, at) -> PlannedReminder(ReminderKind.EVENT, event.id, at, date) }
    }

    /**
     * Next scheduled day at the habit's reminder time; today is skipped once the target is met
     * or the time has passed. The day comes straight from the schedule (no look-ahead window),
     * so "every 30 days" or a start date months away still get their reminder.
     */
    fun forHabit(habit: Habit, todayAmount: Int, now: Instant, zone: ZoneId): PlannedReminder? {
        val time = habit.reminderTime ?: return null
        if (habit.archived) return null
        val today = now.atZone(zone).toLocalDate()
        var from = today
        // Only today can be skipped, so the second candidate is always the answer.
        repeat(2) {
            val date = HabitStats.nextScheduledDate(habit, from) ?: return null
            val at = date.atTime(time).atZone(zone).toInstant()
            val metToday = date == today && todayAmount >= habit.target
            if (!metToday && at.isAfter(now)) return PlannedReminder(ReminderKind.HABIT, habit.id, at, date)
            from = date.plusDays(1)
        }
        return null
    }
}
