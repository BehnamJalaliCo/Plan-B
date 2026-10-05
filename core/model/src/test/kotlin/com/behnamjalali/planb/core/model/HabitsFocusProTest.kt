package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

/** Plan-B Pro habits and focus rules (#26–#30). */
class HabitsFocusProTest {
    private val today = LocalDate.of(2026, 10, 7) // Wednesday
    private val zone = ZoneId.of("Asia/Tehran")

    private fun habit(schedule: HabitSchedule = HabitSchedule.Daily, target: Int = 1, startDaysAgo: Long = 120, id: Long = 1) =
        Habit(id = id, title = "h$id", schedule = schedule, target = target, startDate = today.minusDays(startDaysAgo))

    private fun days(vararg offsets: Long, amount: Int = 1) = offsets.associate { today.minusDays(it) to amount }
    private fun range(from: Long, to: Long) = (to..from).associate { today.minusDays(it) to 1 }

    // region #28 advanced statistics
    @Test
    fun periodRate_isNullWhenNothingWasDue_andTodayOnlyCountsOnceDone() {
        val h = habit(startDaysAgo = 0)
        val span = DateSpan(today, today.plusDays(6))
        assertThat(HabitAnalytics.periodRate(h, emptyMap(), span, today).rate).isNull()
        assertThat(HabitAnalytics.periodRate(h, days(0), span, today).rate).isEqualTo(1f)
        // A span entirely before the start.
        assertThat(HabitAnalytics.periodRate(h, emptyMap(), DateSpan(today.minusDays(9), today.minusDays(3)), today).rate).isNull()
    }

    @Test
    fun analyze_ratesWeekdaysAndTrend() {
        // Every day for the last 30 days except Tuesdays; nothing in the 30 days before.
        val amounts = (0L..29L).map { today.minusDays(it) }.filter { it.dayOfWeek != DayOfWeek.TUESDAY }.associateWith { 1 }
        val h = habit()
        val week = DateSpan(today.minusDays(4), today.plusDays(2)) // Saturday-start week
        val result = HabitAnalytics.analyze(h, amounts, today, DayOfWeek.SATURDAY, week, DateSpan(today.minusDays(14), today.plusDays(15)), DateSpan(today.minusDays(200), today.plusDays(165)), listOf(week), emptyList())
        assertThat(result.weekdayRates[DayOfWeek.TUESDAY]).isEqualTo(0f)
        assertThat(result.worstWeekday).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(result.bestWeekday).isNotEqualTo(DayOfWeek.TUESDAY)
        assertThat(result.trend!!.direction).isEqualTo(TrendDirection.UP)
        assertThat(result.trend!!.previous).isEqualTo(0f)
        assertThat(result.totalDoneDays).isEqualTo(amounts.size)
        // Saturday..Wednesday, Tuesday missed: 4 of 5.
        assertThat(result.week.rate).isWithin(0.001f).of(0.8f)
        assertThat(result.week.doneDays).isEqualTo(4)
    }

    @Test
    fun trend_needsSixtyDaysOfHistory() {
        assertThat(HabitAnalytics.trend(habit(startDaysAgo = 40), range(39, 0), today)).isNull()
        val steady = HabitAnalytics.trend(habit(), range(59, 0), today)!!
        assertThat(steady.direction).isEqualTo(TrendDirection.STEADY)
    }

    @Test
    fun streakMilestones_firstDayEachLengthWasReached() {
        val amounts = range(9, 0) + range(30, 20) // 11 days, gap, 10 days
        val result = HabitAnalytics.streakMilestones(habit(), amounts, today, listOf(7, 11, 12))
        assertThat(result[7]).isEqualTo(today.minusDays(24))
        assertThat(result[11]).isEqualTo(today.minusDays(20))
        assertThat(result).doesNotContainKey(12)
        assertThat(HabitAnalytics.streakMilestones(habit(HabitSchedule.TimesPerWeek(3)), amounts, today, listOf(7))).isEmpty()
    }
    // endregion

    // region #29 challenges
    private fun challenge(days: Int, startDaysAgo: Long, status: ChallengeStatus = ChallengeStatus.ACTIVE) =
        Challenge(title = "c", targetDays = days, startDate = today.minusDays(startDaysAgo), habitId = 1, status = status)

    @Test
    fun challenge_activeWhileEveryPastDueDayIsDone_todayIsNeverAFailure() {
        val p = ChallengeRules.evaluate(challenge(7, 3), habit(), range(3, 1), today)
        assertThat(p.status).isEqualTo(ChallengeStatus.ACTIVE)
        assertThat(p.done).isEqualTo(3)
        assertThat(p.required).isEqualTo(7)
        assertThat(p.days.map { it.second }).containsExactly(
            ChallengeDayState.DONE, ChallengeDayState.DONE, ChallengeDayState.DONE, ChallengeDayState.TODAY,
            ChallengeDayState.FUTURE, ChallengeDayState.FUTURE, ChallengeDayState.FUTURE,
        ).inOrder()
    }

    @Test
    fun challenge_failsOnAMissedDay_andComesBackWhenThatDayIsCheckedIn() {
        val failed = ChallengeRules.evaluate(challenge(7, 3), habit(), days(3, 1), today)
        assertThat(failed.status).isEqualTo(ChallengeStatus.FAILED)
        assertThat(failed.failedOn).isEqualTo(today.minusDays(2))
        assertThat(ChallengeRules.evaluate(challenge(7, 3), habit(), days(3, 2, 1), today).status).isEqualTo(ChallengeStatus.ACTIVE)
    }

    @Test
    fun challenge_completesOnItsLastDueDay_skippingDaysNotDue() {
        // Mondays and Wednesdays only; 7 days from last Thursday: Mon and Wed (today) are due.
        val h = habit(HabitSchedule.SelectedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val c = challenge(7, 6)
        assertThat(ChallengeRules.evaluate(c, h, days(2), today).status).isEqualTo(ChallengeStatus.ACTIVE)
        val done = ChallengeRules.evaluate(c, h, days(2, 0), today)
        assertThat(done.status).isEqualTo(ChallengeStatus.COMPLETED)
        assertThat(done.completedOn).isEqualTo(today)
        assertThat(done.days.count { it.second == ChallengeDayState.REST }).isEqualTo(5)
    }

    @Test
    fun challenge_timesPerWeekCountsBlocks() {
        val h = habit(HabitSchedule.TimesPerWeek(3))
        // 10 days that started 9 days ago: block 1 (7 days) needs 3, block 2 (3 days) needs 2.
        val c = challenge(10, 9)
        assertThat(ChallengeRules.evaluate(c, h, days(9, 8, 7, 1), today).status).isEqualTo(ChallengeStatus.ACTIVE)
        val done = ChallengeRules.evaluate(c, h, days(9, 8, 7, 1, 0), today)
        assertThat(done.status).isEqualTo(ChallengeStatus.COMPLETED)
        assertThat(done.completedOn).isEqualTo(today)
        assertThat(done.required).isEqualTo(5)
        assertThat(ChallengeRules.evaluate(c, h, days(9, 1, 0), today).status).isEqualTo(ChallengeStatus.FAILED)
    }

    @Test
    fun challenge_abandonedOrWithoutItsHabitStaysAbandoned() {
        assertThat(ChallengeRules.evaluate(challenge(7, 6, ChallengeStatus.ABANDONED), habit(), range(6, 0), today).status).isEqualTo(ChallengeStatus.ABANDONED)
        assertThat(ChallengeRules.evaluate(challenge(7, 6), null, emptyMap(), today).status).isEqualTo(ChallengeStatus.ABANDONED)
    }
    // endregion

    // region #29 badges
    @Test
    fun badges_areDeterministicFromTheData() {
        val input = AchievementInput(
            today = today,
            habits = listOf(habit() to range(28, 0)),
            focusSessions = (1..3).map { today.minusDays(it.toLong()) to 25 * 60_000L }.reversed() + (today to 60 * 60_000L),
            taskCompletions = (1..12).map { today.minusDays(it.toLong()) },
            journalDates = (0L..7L).map { today.minusDays(it) }.toSet() + today.minusDays(20),
            moodDates = List(10) { today },
            completedChallenges = listOf(21 to today.minusDays(3)),
        )
        val result = BadgeRules.evaluate(input).associateBy { it.badge }
        assertThat(result.getValue(BadgeDefinition.STREAK_7).earnedOn).isEqualTo(today.minusDays(22))
        assertThat(result.getValue(BadgeDefinition.STREAK_30).earned).isFalse()
        assertThat(result.getValue(BadgeDefinition.STREAK_30).current).isEqualTo(29)
        // 25 + 25 + 25 minutes on the three days before today: the first hour is reached yesterday.
        assertThat(result.getValue(BadgeDefinition.FOCUS_1).earnedOn).isEqualTo(today.minusDays(1))
        assertThat(result.getValue(BadgeDefinition.FOCUS_1).current).isEqualTo(2)
        assertThat(result.getValue(BadgeDefinition.TASKS_10).earnedOn).isEqualTo(today.minusDays(3))
        assertThat(result.getValue(BadgeDefinition.JOURNAL_7).earnedOn).isEqualTo(today.minusDays(1))
        assertThat(result.getValue(BadgeDefinition.JOURNAL_30).earned).isFalse()
        assertThat(result.getValue(BadgeDefinition.MOOD_10).earnedOn).isEqualTo(today)
        assertThat(result.getValue(BadgeDefinition.CHALLENGE_7).earnedOn).isEqualTo(today.minusDays(3))
        assertThat(result.getValue(BadgeDefinition.CHALLENGE_21).earned).isTrue()
        assertThat(result.getValue(BadgeDefinition.CHALLENGE_30).earned).isFalse()
        assertThat(BadgeRules.evaluate(input)).isEqualTo(BadgeRules.evaluate(input.copy(taskCompletions = input.taskCompletions.shuffled())))
        assertThat(BadgeDefinition.entries.map { it.key }.toSet()).hasSize(BadgeDefinition.entries.size)
    }
    // endregion

    // region #30 mood
    private fun mood(daysAgo: Long, mood: Int?, energy: Int? = null, time: LocalTime? = null) =
        MoodEntry(date = today.minusDays(daysAgo), mood = mood, energy = energy, time = time)

    @Test
    fun moodInsights_averagesByDayEnergyAndPartOfDay() {
        val entries = listOf(
            mood(0, 5, 5, LocalTime.of(8, 0)),
            mood(0, 3, 1, LocalTime.of(23, 0)),
            mood(1, 4, 5, LocalTime.of(13, 0)),
            mood(200, 1), // outside the window
            mood(2, null, 2),
        )
        val result = MoodInsights.analyze(entries, today)
        assertThat(result.checkIns).isEqualTo(4)
        assertThat(result.daily.first { it.date == today }.mood).isEqualTo(4f)
        assertThat(result.moodByEnergy[5]).isEqualTo(4.5f)
        assertThat(result.moodByEnergy[1]).isEqualTo(3f)
        assertThat(result.moodByEnergy[3]).isNull()
        assertThat(result.moodByDayPart[DayPart.MORNING]).isEqualTo(5f)
        assertThat(result.moodByDayPart[DayPart.NIGHT]).isEqualTo(3f)
        assertThat(result.averageEnergy).isWithin(0.001f).of(13f / 4)
        assertThat(result.correlations).isEmpty()
    }

    @Test
    fun moodInsights_correlatesWithHabitsFocusAndSleep_onlyWithEnoughDays() {
        // Ten days: mood follows focus minutes and sleep, habit done on the good days.
        val moods = (0L until 10L).map { mood(it, if (it % 2 == 0L) 5 else 2) }
        val focus = (0L until 10L).associate { today.minusDays(it) to if (it % 2 == 0L) 90 else 10 }
        val sleep = (0L until 10L).associate { today.minusDays(it) to if (it % 2 == 0L) 480L else 300L }
        val h = habit()
        val amounts = (0L until 10L).filter { it % 2 == 0L }.associate { today.minusDays(it) to 1 }
        val result = MoodInsights.analyze(moods, today, listOf(h to amounts), focus, sleep)
        assertThat(result.correlations.map { it.factor }).containsExactly(MoodFactor.HABITS, MoodFactor.FOCUS, MoodFactor.SLEEP)
        result.correlations.forEach {
            assertThat(it.r).isWithin(0.0001f).of(1f)
            assertThat(it.strength).isEqualTo(CorrelationStrength.STRONG)
        }
        val effect = result.habitEffects.single()
        assertThat(effect.difference).isEqualTo(3f)
        // Six days are not enough for a correlation.
        assertThat(MoodInsights.analyze(moods.take(6), today, focusMinutes = focus).correlations).isEmpty()
        assertThat(MoodInsights.pearson(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0))).isNull()
        assertThat(MoodInsights.pearson(listOf(1.0, 2.0, 3.0), listOf(3.0, 2.0, 1.0))).isWithin(1e-9).of(-1.0)
    }

    @Test
    fun dayParts() {
        assertThat(DayPart.of(LocalTime.of(4, 59))).isEqualTo(DayPart.NIGHT)
        assertThat(DayPart.of(LocalTime.of(5, 0))).isEqualTo(DayPart.MORNING)
        assertThat(DayPart.of(LocalTime.of(12, 0))).isEqualTo(DayPart.AFTERNOON)
        assertThat(DayPart.of(LocalTime.of(21, 59))).isEqualTo(DayPart.EVENING)
    }
    // endregion

    // region #27 Health Connect
    @Test
    fun health_thresholdChecksTheHabitOffOnce_neverRemovingCheckIns() {
        val h = habit(target = 2).copy(healthMetric = HealthMetric.STEPS, healthThreshold = 8_000)
        assertThat(HealthHabits.delta(h, 7_999, 0, alreadyChecked = false)).isEqualTo(0)
        assertThat(HealthHabits.delta(h, 8_000, 0, alreadyChecked = false)).isEqualTo(2)
        assertThat(HealthHabits.delta(h, 9_000, 1, alreadyChecked = false)).isEqualTo(1)
        assertThat(HealthHabits.delta(h, 9_000, 3, alreadyChecked = false)).isEqualTo(0)
        assertThat(HealthHabits.delta(h, 9_000, 0, alreadyChecked = true)).isEqualTo(0)
        assertThat(HealthHabits.delta(h, null, 0, alreadyChecked = false)).isEqualTo(0)
        assertThat(HealthHabits.delta(h.copy(healthThreshold = null), 9_000, 0, alreadyChecked = false)).isEqualTo(0)
    }

    @Test
    fun health_windowsAndDays() {
        val (from, to) = HealthHabits.window(HealthMetric.STEPS, today, zone)
        assertThat(from).isEqualTo(today.atStartOfDay(zone).toInstant())
        assertThat(to).isEqualTo(today.plusDays(1).atStartOfDay(zone).toInstant())
        val (night, morning) = HealthHabits.window(HealthMetric.SLEEP_MINUTES, today, zone)
        assertThat(night).isEqualTo(today.minusDays(1).atTime(18, 0).atZone(zone).toInstant())
        assertThat(morning).isEqualTo(today.atTime(18, 0).atZone(zone).toInstant())
        assertThat(HealthHabits.daysToCheck(habit(startDaysAgo = 2), today)).containsExactly(today.minusDays(2), today.minusDays(1), today).inOrder()
        val weekdays = habit(HabitSchedule.SelectedDays(setOf(DayOfWeek.MONDAY)))
        assertThat(HealthHabits.daysToCheck(weekdays, today)).containsExactly(today.minusDays(2))
        HealthMetric.entries.forEach { assertThat(HealthHabits.defaultThreshold(it)).isIn(HealthHabits.range(it)) }
    }
    // endregion

    // region #26 Focus Pro
    @Test
    fun strictMode_appliesOnlyOverAllAndRestoresOnlyItsOwnFilter() {
        val apply = StrictMode.decide(wanted = true, stored = null, current = StrictMode.FILTER_ALL, hasAccess = true)
        assertThat(apply).isEqualTo(StrictMode.Action.Apply(StrictMode.FILTER_PRIORITY, StrictModeState(StrictMode.FILTER_ALL, StrictMode.FILTER_PRIORITY)))
        val state = (apply as StrictMode.Action.Apply).state
        // Do Not Disturb that was already on is left alone.
        assertThat(StrictMode.decide(true, null, current = 3, hasAccess = true)).isEqualTo(StrictMode.Action.None)
        // No access: nothing happens.
        assertThat(StrictMode.decide(true, null, StrictMode.FILTER_ALL, hasAccess = false)).isEqualTo(StrictMode.Action.None)
        // Still running: keep.
        assertThat(StrictMode.decide(true, state, StrictMode.FILTER_PRIORITY, true)).isEqualTo(StrictMode.Action.None)
        // Paused or ended: restore.
        assertThat(StrictMode.decide(false, state, StrictMode.FILTER_PRIORITY, true)).isEqualTo(StrictMode.Action.Restore(StrictMode.FILTER_ALL))
        // The user changed it meanwhile, or took access away: forget without touching it.
        assertThat(StrictMode.decide(false, state, 4, true)).isEqualTo(StrictMode.Action.Restore(null))
        assertThat(StrictMode.decide(false, state, null, false)).isEqualTo(StrictMode.Action.Restore(null))
        assertThat(StrictMode.decide(false, null, StrictMode.FILTER_ALL, true)).isEqualTo(StrictMode.Action.None)
    }

    @Test
    fun focusCycle_longBreakAfterEveryNthSession() {
        assertThat(FocusCycle.after(0, 4, 5, 15).longBreak).isFalse()
        assertThat(FocusCycle.after(3, 4, 5, 15).breakMinutes).isEqualTo(5)
        val fourth = FocusCycle.after(4, 4, 5, 15)
        assertThat(fourth.longBreak).isTrue()
        assertThat(fourth.breakMinutes).isEqualTo(15)
        assertThat(fourth.positionInCycle).isEqualTo(1)
        assertThat(FocusCycle.after(5, 4, 5, 15).positionInCycle).isEqualTo(2)
        assertThat(FocusCycle.after(8, 0, 5, 15).longBreak).isTrue()
    }

    @Test
    fun ambientSoundIdsAreStable() {
        assertThat(AmbientSound.entries.map { it.id }).containsExactly("rain", "ocean", "brown", "pink", "white").inOrder()
        assertThat(AmbientSound.fromId("unknown")).isNull()
        assertThat(AmbientSound.fromId(null)).isNull()
    }
    // endregion
}
