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
    fun timesPerWeekRate_ignoresDaysBeforeStart_andUnfinishedToday() {
        // Started Monday 5 Oct; today is Wednesday 7 Oct, nothing logged today yet.
        val h = Habit(title = "h", schedule = HabitSchedule.TimesPerWeek(7), startDate = LocalDate.of(2026, 10, 5))
        val amounts = mapOf(LocalDate.of(2026, 10, 5) to 1, LocalDate.of(2026, 10, 6) to 1)
        val weekStart = LocalDate.of(2026, 10, 3)
        assertThat(HabitStats.completionRate(h, amounts, weekStart, today, today)).isWithin(0.001f).of(1f)
        // Once today is done it counts too.
        assertThat(HabitStats.completionRate(h, amounts + (today to 1), weekStart, today, today)).isWithin(0.001f).of(1f)
        // Without "today" (the old contract) the open day still counts against the rate.
        assertThat(HabitStats.completionRate(h, amounts, weekStart, today)).isWithin(0.001f).of(2f / 3f)
    }

    @Test
    fun dailyRate_unfinishedTodayIsNotMissed() {
        val h = habit()
        assertThat(HabitStats.completionRate(h, days(1, 2), today.minusDays(2), today, today)).isWithin(0.001f).of(1f)
        assertThat(HabitStats.completionRate(h, days(1), today.minusDays(2), today, today)).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun timesPerWeek_bestStreakInWeeks() {
        val h = habit(HabitSchedule.TimesPerWeek(2))
        // Weeks start Saturday. Three good weeks in a row, a gap, then the current week (one so far).
        val amounts = listOf(
            LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30),
            LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6),
            LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13),
            LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27),
            LocalDate.of(2026, 10, 4),
        ).associateWith { 1 }
        assertThat(HabitStats.bestStreak(h, amounts, today, DayOfWeek.SATURDAY)).isEqualTo(Streak(3, Streak.Unit.WEEKS))
        assertThat(HabitStats.bestStreakDays(h, amounts, today, DayOfWeek.SATURDAY)).isEqualTo(3)
        // The current, unfinished week neither breaks nor (yet) extends the latest run.
        val current = HabitStats.currentStreak(h, amounts, today, DayOfWeek.SATURDAY)
        assertThat(current.count).isAtMost(HabitStats.bestStreak(h, amounts, today, DayOfWeek.SATURDAY).count)
    }

    @Test
    fun nextScheduledDate_followsEverySchedule_withoutHorizon() {
        val every30 = Habit(title = "h", schedule = HabitSchedule.EveryNDays(30), startDate = today.minusDays(5))
        assertThat(HabitStats.nextScheduledDate(every30, today)).isEqualTo(today.plusDays(25))
        assertThat(HabitStats.nextScheduledDate(every30, today.minusDays(5))).isEqualTo(today.minusDays(5))
        val startsLater = Habit(title = "h", startDate = today.plusDays(40))
        assertThat(HabitStats.nextScheduledDate(startsLater, today)).isEqualTo(today.plusDays(40))
        val fridays = habit(HabitSchedule.SelectedDays(setOf(DayOfWeek.FRIDAY)))
        assertThat(HabitStats.nextScheduledDate(fridays, today)).isEqualTo(LocalDate.of(2026, 10, 9))
        assertThat(HabitStats.nextScheduledDate(habit(HabitSchedule.SelectedDays(emptySet())), today)).isNull()
        listOf(every30, startsLater, fridays).forEach { h ->
            assertThat(HabitStats.isScheduled(h, HabitStats.nextScheduledDate(h, today)!!)).isTrue()
        }
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
