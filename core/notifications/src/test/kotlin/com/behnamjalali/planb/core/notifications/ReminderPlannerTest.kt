package com.behnamjalali.planb.core.notifications

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
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
        assertThat(ReminderPlanner.forTask(future, now, zone)).isNotNull()
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
