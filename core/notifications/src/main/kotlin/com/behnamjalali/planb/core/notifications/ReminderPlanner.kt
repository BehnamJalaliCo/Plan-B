package com.behnamjalali.planb.core.notifications

import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.datetime.ReminderTime
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A single scheduled alarm: what it is for and when it fires. */
data class PlannedReminder(val kind: ReminderKind, val id: Long, val at: Instant, val occurrenceDate: LocalDate)

enum class ReminderKind(val code: Int) { TASK(1), EVENT(2), HABIT(3), FOCUS(4) }

/**
 * Pure calculation of the next reminder for an item. Returns null when no
 * future reminder exists (completed, archived, past, or no reminder set).
 */
object ReminderPlanner {
    private const val EVENT_LOOKAHEAD_DAYS = 400L
    private const val HABIT_LOOKAHEAD_DAYS = 14L

    fun forTask(task: Task, now: Instant, zone: ZoneId): PlannedReminder? {
        val offset = task.reminderOffsetMinutes ?: return null
        val due = task.dueDate ?: return null
        if (task.isCompleted || task.archived) return null
        val at = ReminderTime.triggerAt(due, task.dueTime, offset, zone)
        return if (at.isAfter(now)) PlannedReminder(ReminderKind.TASK, task.id, at, due) else null
    }

    fun forEvent(event: CalendarEvent, now: Instant, zone: ZoneId): PlannedReminder? {
        val offset = event.reminderOffsetMinutes ?: return null
        val today = LocalDate.ofInstant(now, zone)
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

    /** Next scheduled day at the habit's reminder time; today is skipped once the target is met. */
    fun forHabit(habit: Habit, todayAmount: Int, now: Instant, zone: ZoneId): PlannedReminder? {
        val time = habit.reminderTime ?: return null
        if (habit.archived) return null
        val today = LocalDate.ofInstant(now, zone)
        for (offset in 0..HABIT_LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset)
            if (!HabitStats.isScheduled(habit, date)) continue
            if (offset == 0L && todayAmount >= habit.target) continue
            val at = date.atTime(time).atZone(zone).toInstant()
            if (at.isAfter(now)) return PlannedReminder(ReminderKind.HABIT, habit.id, at, date)
        }
        return null
    }
}
