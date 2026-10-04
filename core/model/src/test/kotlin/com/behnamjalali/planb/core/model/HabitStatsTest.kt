package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class HabitStatsTest {
    private val today = LocalDate.of(2026, 10, 7) // Wednesday
    private fun habit(schedule: HabitSchedule = HabitSchedule.Daily, target: Int = 1) =
        Habit(title = "h", schedule = schedule, target = target, startDate = today.minusDays(60))

    private fun days(vararg offsets: Long, amount: Int = 1) = offsets.associate { today.minusDays(it) to amount }

    @Test
    fun daily_consecutiveDays() {
        val streak = HabitStats.currentStreak(habit(), days(0, 1, 2, 4), today, DayOfWeek.SATURDAY)
        assertThat(streak).isEqualTo(Streak(3, Streak.Unit.DAYS))
    }

    @Test
    fun daily_unfinishedTodayDoesNotBreakStreak() {
        val streak = HabitStats.currentStreak(habit(), days(1, 2), today, DayOfWeek.SATURDAY)
        assertThat(streak.count).isEqualTo(2)
    }

    @Test
    fun daily_missedYesterdayBreaksStreak() {
        assertThat(HabitStats.currentStreak(habit(), days(0, 2, 3), today, DayOfWeek.SATURDAY).count).isEqualTo(1)
    }

    @Test
    fun target_requiresFullAmount() {
        val h = habit(target = 8)
        val amounts = mapOf(today to 8, today.minusDays(1) to 5)
        assertThat(HabitStats.currentStreak(h, amounts, today, DayOfWeek.SATURDAY).count).isEqualTo(1)
    }

    @Test
    fun selectedDays_skipsUnscheduledDays() {
        // Mon/Wed/Fri: today Wed, Mon(2 days ago), Fri (5 days ago) done; Tue/Thu not scheduled
        val h = habit(HabitSchedule.SelectedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        val streak = HabitStats.currentStreak(h, days(0, 2, 5), today, DayOfWeek.SATURDAY)
        assertThat(streak.count).isEqualTo(3)
    }

    @Test
    fun everyNDays_onlyScheduledIntervals() {
        val h = Habit(title = "h", schedule = HabitSchedule.EveryNDays(3), startDate = today.minusDays(9))
        assertThat(HabitStats.isScheduled(h, today)).isTrue()
        assertThat(HabitStats.isScheduled(h, today.minusDays(1))).isFalse()
        assertThat(HabitStats.isScheduled(h, today.minusDays(9))).isTrue()
        assertThat(HabitStats.isScheduled(h, today.minusDays(3))).isTrue()
        assertThat(HabitStats.currentStreak(h, days(3, 6, 9), today, DayOfWeek.MONDAY).count).isEqualTo(3)
    }

    @Test
    fun timesPerWeek_countsWeeks() {
        val h = habit(HabitSchedule.TimesPerWeek(2))
        // Weeks start Saturday. Current week: Sat 3 Oct..Fri 9 Oct. Previous: 26 Sep..2 Oct.
        val amounts = listOf(
            LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 6), // current week: 2
            LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 30), // previous: 2
            LocalDate.of(2026, 9, 20), // two weeks ago: 1 → breaks
        ).associateWith { 1 }
        val streak = HabitStats.currentStreak(h, amounts, today, DayOfWeek.SATURDAY)
        assertThat(streak).isEqualTo(Streak(2, Streak.Unit.WEEKS))
    }

    @Test
    fun startDate_boundsStreak() {
        val h = Habit(title = "h", startDate = today.minusDays(1))
        assertThat(HabitStats.currentStreak(h, days(0, 1, 2, 3), today, DayOfWeek.SATURDAY).count).isEqualTo(2)
    }

    @Test
    fun completionRate_andBestStreak() {
        val h = habit()
        val amounts = days(0, 1, 3, 4, 5)
        assertThat(HabitStats.completionRate(h, amounts, today.minusDays(6), today)).isWithin(0.001f).of(5f / 7f)
        assertThat(HabitStats.bestStreakDays(h, amounts, today)).isEqualTo(3)
    }

    @Test
    fun scheduleEncoding_roundTrips() {
        listOf(
            HabitSchedule.Daily,
            HabitSchedule.SelectedDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)),
            HabitSchedule.TimesPerWeek(3),
            HabitSchedule.EveryNDays(2),
        ).forEach { assertThat(HabitSchedule.decode(it.encode())).isEqualTo(it) }
        assertThat(HabitSchedule.decode("nonsense")).isEqualTo(HabitSchedule.Daily)
    }
}
