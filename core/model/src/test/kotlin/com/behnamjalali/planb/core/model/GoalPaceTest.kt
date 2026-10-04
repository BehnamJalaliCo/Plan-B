package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class GoalPaceTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val deadline = start.plusDays(100)

    private fun pace(current: Double, today: LocalDate) =
        GoalPace.of(Goal(title = "g", target = 100.0, currentValue = current, deadline = deadline), start, today)!!

    @Test
    fun onTrack_ahead_behind() {
        assertThat(pace(50.0, start.plusDays(50)).status).isEqualTo(GoalPace.Status.ON_TRACK)
        assertThat(pace(70.0, start.plusDays(50)).status).isEqualTo(GoalPace.Status.AHEAD)
        assertThat(pace(20.0, start.plusDays(50)).status).isEqualTo(GoalPace.Status.BEHIND)
    }

    @Test
    fun done_and_overdue() {
        assertThat(pace(100.0, start.plusDays(10)).status).isEqualTo(GoalPace.Status.DONE)
        assertThat(pace(10.0, deadline.plusDays(1)).status).isEqualTo(GoalPace.Status.OVERDUE)
    }

    @Test
    fun neededPerWeek() {
        val p = pace(30.0, start.plusDays(30)) // 70 left, 70 days = 10 weeks
        assertThat(p.neededPerWeek).isWithin(0.01).of(7.0)
        assertThat(p.daysLeft).isEqualTo(70)
    }

    @Test
    fun noDeadline_noPace() {
        assertThat(GoalPace.of(Goal(title = "g"), start, start)).isNull()
    }

    @Test
    fun largeGoal_justShortOfTarget_isNotDone() {
        val goal = Goal(title = "g", target = 100_000_000.0, currentValue = 99_999_999.0, deadline = deadline)
        assertThat(goal.isComplete).isFalse()
        assertThat(goal.progress).isLessThan(1f)
        assertThat(GoalPace.of(goal, start, start.plusDays(50))!!.status).isNotEqualTo(GoalPace.Status.DONE)
        assertThat(goal.copy(currentValue = 100_000_000.0).progress).isEqualTo(1f)
    }

    @Test
    fun deadlineDay_expectsEverything_andNeedsAllRemaining() {
        val today = LocalDate.of(2026, 3, 1)
        val createdToday = GoalPace.of(Goal(title = "g", target = 10.0, deadline = today), today, today)!!
        assertThat(createdToday.expectedProgress).isEqualTo(1f)
        assertThat(createdToday.status).isEqualTo(GoalPace.Status.BEHIND)
        assertThat(createdToday.neededPerWeek).isWithin(0.001).of(10.0)

        val onDeadline = pace(40.0, deadline)
        assertThat(onDeadline.daysLeft).isEqualTo(0)
        assertThat(onDeadline.expectedProgress).isEqualTo(1f)
        assertThat(onDeadline.neededPerWeek).isWithin(0.001).of(60.0)
        assertThat(pace(10.0, deadline.plusDays(1)).neededPerWeek).isEqualTo(0.0)
    }
}
