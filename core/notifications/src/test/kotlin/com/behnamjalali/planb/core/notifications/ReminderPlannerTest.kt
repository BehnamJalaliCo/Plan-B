package com.behnamjalali.planb.core.notifications

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskPlanning
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val today = LocalDate.of(2026, 10, 4)
    private val now = today.atTime(9, 0).atZone(zone).toInstant()

    @Test
    fun task_reminderBeforeDueTime() {
        val task = Task(id = 1, title = "t", dueDate = today, dueTime = LocalTime.of(10, 0), reminderOffsetMinutes = 15)
        val planned = ReminderPlanner.forTask(task, now, zone)!!
        assertThat(planned.at).isEqualTo(today.atTime(9, 45).atZone(zone).toInstant())
    }

    @Test
    fun task_pastCompletedOrWithoutReminder_isNotScheduled() {
        val base = Task(id = 1, title = "t", dueDate = today, dueTime = LocalTime.of(8, 0), reminderOffsetMinutes = 0)
        assertThat(ReminderPlanner.forTask(base, now, zone)).isNull()
        val future = base.copy(dueTime = LocalTime.of(12, 0))
        assertThat(ReminderPlanner.forTask(future.copy(reminderOffsetMinutes = null), now, zone)).isNull()
        assertThat(ReminderPlanner.forTask(future.copy(archived = true), now, zone)).isNull()
        assertThat(ReminderPlanner.forTask(future.copy(deletedAt = now), now, zone)).isNull()
        assertThat(ReminderPlanner.forTask(future, now, zone)).isNotNull()
    }

    @Test
    fun task_extraReminders_earliestFutureWins() {
        val task = Task(id = 1, title = "t", dueDate = today, dueTime = LocalTime.of(12, 0), reminderOffsetMinutes = 0, deadline = today.plusDays(1))
        val absolute = today.atTime(9, 30).atZone(zone).toInstant()
        val planning = TaskPlanning(
            reminders = listOf(
                TaskReminder(kind = TaskReminderKind.OFFSET, offsetMinutes = 60),
                TaskReminder(kind = TaskReminderKind.DEADLINE, offsetMinutes = 30),
                TaskReminder(kind = TaskReminderKind.ABSOLUTE, at = absolute),
            ),
        )
        assertThat(ReminderPlanner.forTask(task, now, zone, planning)!!.at).isEqualTo(absolute)
        val after = today.atTime(9, 45).atZone(zone).toInstant()
        assertThat(ReminderPlanner.forTask(task, after, zone, planning)!!.at).isEqualTo(today.atTime(11, 0).atZone(zone).toInstant())
        val evening = today.atTime(13, 0).atZone(zone).toInstant()
        // The deadline reminder: 30 minutes before the deadline day's default time (09:00).
        assertThat(ReminderPlanner.forTask(task, evening, zone, planning)!!.at).isEqualTo(today.plusDays(1).atTime(8, 30).atZone(zone).toInstant())
        // Without a planned date only the deadline and fixed reminders remain.
        val undated = task.copy(dueDate = null, dueTime = null)
        assertThat(ReminderPlanner.forTask(undated, after, zone, planning)!!.occurrenceDate).isEqualTo(today.plusDays(1))
        // Without extra reminders nothing changes for the primary one.
        assertThat(ReminderPlanner.forTask(task, now, zone)!!.at).isEqualTo(today.atTime(12, 0).atZone(zone).toInstant())
    }

    @Test
    fun task_nagging_repeatsAtTheIntervalUpToTheCap_andRespectsStopAndSnooze() {
        val task = Task(id = 1, title = "t", dueDate = today, dueTime = LocalTime.of(8, 0), reminderOffsetMinutes = 0, nag = true)
        val fired = today.atTime(8, 0).atZone(zone).toInstant()
        val planning = TaskPlanning(nagIntervalMinutes = 15)
        // 09:00 is the fourth repetition (08:15, 08:30, 08:45, 09:00), so the next is 09:15.
        assertThat(ReminderPlanner.forTask(task, now, zone, planning)!!.at).isEqualTo(fired.plusSeconds(75 * 60))
        // Twelve repetitions at most: none after 11:00.
        assertThat(ReminderPlanner.forTask(task, fired.plusSeconds(180 * 60), zone, planning)).isNull()
        // Not nagging: the past reminder is simply over.
        assertThat(ReminderPlanner.forTask(task.copy(nag = false), now, zone, planning)).isNull()
        // Dismissed at 08:50: no more repetitions of the 08:00 reminder.
        val stopped = NagState(stoppedAt = fired.plusSeconds(50 * 60))
        assertThat(ReminderPlanner.forTask(task, now, zone, planning, stopped)).isNull()
        // Snoozed until 09:05: that one comes, then nags from it.
        val snoozed = NagState(stoppedAt = fired.plusSeconds(55 * 60), snoozedUntil = fired.plusSeconds(65 * 60))
        assertThat(ReminderPlanner.forTask(task, now, zone, planning, snoozed)!!.at).isEqualTo(fired.plusSeconds(65 * 60))
        assertThat(ReminderPlanner.forTask(task, fired.plusSeconds(66 * 60), zone, planning, snoozed)!!.at).isEqualTo(fired.plusSeconds(80 * 60))
        // Completed tasks never nag.
        assertThat(ReminderPlanner.forTask(task.copy(status = com.behnamjalali.planb.core.model.TaskStatus.DONE), now, zone, planning)).isNull()
    }

    @Test
    fun recurringEvent_picksNextFutureOccurrence() {
        val event = CalendarEvent(
            id = 2, title = "e", date = today.minusDays(10), startTime = LocalTime.of(8, 0),
            reminderOffsetMinutes = 0, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )
        val planned = ReminderPlanner.forEvent(event, now, zone)!!
        assertThat(planned.occurrenceDate).isEqualTo(today.plusDays(1))
    }

    @Test
    fun habit_skipsTodayWhenTargetMet_andRespectsSchedule() {
        val habit = Habit(id = 3, title = "h", startDate = today.minusDays(5), reminderTime = LocalTime.of(20, 0))
        assertThat(ReminderPlanner.forHabit(habit, 0, now, zone)!!.occurrenceDate).isEqualTo(today)
        assertThat(ReminderPlanner.forHabit(habit, 1, now, zone)!!.occurrenceDate).isEqualTo(today.plusDays(1))
        val weekly = habit.copy(schedule = HabitSchedule.SelectedDays(setOf(DayOfWeek.FRIDAY)))
        assertThat(ReminderPlanner.forHabit(weekly, 0, now, zone)!!.occurrenceDate.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
    }

    @Test
    fun habit_farAhead_hasNoLookaheadLimit() {
        val every30 = Habit(id = 4, title = "h", schedule = HabitSchedule.EveryNDays(30), startDate = today, reminderTime = LocalTime.of(8, 0))
        // 08:00 today has passed (it is 09:00), so the next one is 30 days later.
        assertThat(ReminderPlanner.forHabit(every30, 0, now, zone)!!.occurrenceDate).isEqualTo(today.plusDays(30))
        val startsLater = Habit(id = 5, title = "h", startDate = today.plusDays(60), reminderTime = LocalTime.of(20, 0))
        assertThat(ReminderPlanner.forHabit(startsLater, 0, now, zone)!!.occurrenceDate).isEqualTo(today.plusDays(60))
        val noDays = Habit(id = 6, title = "h", schedule = HabitSchedule.SelectedDays(emptySet()), startDate = today, reminderTime = LocalTime.of(20, 0))
        assertThat(ReminderPlanner.forHabit(noDays, 0, now, zone)).isNull()
    }
}
