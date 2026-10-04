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
}
