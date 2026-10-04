package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test

class StatisticsCalculatorTest {
    // Saturday 2026-10-03 .. Friday 2026-10-09; "today" is Wednesday.
    private val start = LocalDate.of(2026, 10, 3)
    private val span = DateSpan(start, start.plusDays(6))
    private val today = LocalDate.of(2026, 10, 7)
    private val days = span.dates().map { DateSpan(it, it) }.toList()

    private fun at(dayOffset: Long, hour: Int) = start.plusDays(dayOffset).atTime(hour, 15)
    private fun done(id: Long, time: LocalDateTime, due: LocalDate? = null, project: Long? = null, tags: List<Long> = emptyList()) =
        CompletedTaskRecord(id, time, due, project, tags)

    private fun compute(input: StatsInput) = StatisticsCalculator.compute(input, today, DayOfWeek.SATURDAY)

    @Test
    fun countsCompletedTasksPerDayAndIgnoresOtherPeriods() {
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                completedTasks = listOf(
                    done(1, at(0, 9)),
                    done(2, at(0, 10)),
                    done(3, at(2, 21)),
                    done(4, start.minusDays(1).atTime(12, 0)), // previous week
                ),
            ),
        )
        assertThat(stats.completedTotal).isEqualTo(3)
        assertThat(stats.completedPerBucket).containsExactly(2, 0, 1, 0, 0, 0, 0).inOrder()
        assertThat(stats.bestBucket).isEqualTo(0)
        assertThat(stats.activeDays).isEqualTo(2)
    }

    @Test
    fun completionRateOnlyCountsPlannedDaysUpToToday() {
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                dueTasks = listOf(
                    DueTaskRecord(start, completedOn = start),
                    DueTaskRecord(start.plusDays(1), completedOn = null),
                    DueTaskRecord(today, completedOn = today),
                    DueTaskRecord(today, completedOn = null),
                    DueTaskRecord(today.plusDays(1), completedOn = null), // future: not counted yet
                ),
            ),
        )
        assertThat(stats.plannedTotal).isEqualTo(4)
        assertThat(stats.completionRate).isWithin(1e-6f).of(0.5f)
    }

    @Test
    fun completionRateIsNullWithoutPlannedTasks() {
        assertThat(compute(StatsInput(span, days)).completionRate).isNull()
    }

    @Test
    fun onTimeVersusLate() {
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                completedTasks = listOf(
                    done(1, at(1, 9), due = start.plusDays(1)), // on the day: on time
                    done(2, at(1, 9), due = start.plusDays(3)), // early: on time
                    done(3, at(4, 9), due = start.plusDays(2)), // late
                    done(4, at(4, 9)), // no due date: neither
                ),
            ),
        )
        assertThat(stats.onTime).isEqualTo(2)
        assertThat(stats.late).isEqualTo(1)
    }

    @Test
    fun busiestWeekdayAndHour() {
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                completedTasks = listOf(done(1, at(2, 8)), done(2, at(2, 8)), done(3, at(2, 17)), done(4, at(0, 17))),
            ),
        )
        assertThat(stats.busiestWeekday).isEqualTo(DayOfWeek.MONDAY)
        assertThat(stats.completedPerWeekday[DayOfWeek.MONDAY.value - 1]).isEqualTo(3)
        // 8:00 and 17:00 tie with two each; the earlier hour wins.
        assertThat(stats.busiestHour).isEqualTo(8)
        assertThat(stats.completedPerHour.sum()).isEqualTo(4)
    }

    @Test
    fun emptyPeriodHasNoBusiestValues() {
        val stats = compute(StatsInput(span, days))
        assertThat(stats.busiestWeekday).isNull()
        assertThat(stats.busiestHour).isNull()
        assertThat(stats.bestBucket).isNull()
        assertThat(stats.isEmpty).isTrue()
    }

    @Test
    fun focusMinutesPerBucketSkipEmptySessions() {
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                focus = listOf(FocusRecord(at(0, 9), 25), FocusRecord(at(0, 11), 50), FocusRecord(at(3, 9), 0)),
            ),
        )
        assertThat(stats.focusMinutes).isEqualTo(75)
        assertThat(stats.focusSessions).isEqualTo(2)
        assertThat(stats.focusMinutesPerBucket.first()).isEqualTo(75)
    }

    @Test
    fun habitSuccessUsesScheduledDaysUntilToday() {
        val habit = Habit(id = 1, title = "Read", startDate = start.minusDays(30))
        // Done Saturday..Monday, missed Tuesday, today (Wednesday) not done yet: 3 of 4 days.
        val amounts = (0L..2L).associate { start.plusDays(it) to 1 }
        val stats = compute(StatsInput(span, days, habits = listOf(HabitRecord(habit, amounts))))
        val success = stats.habits.single()
        assertThat(success.rate).isWithin(1e-6f).of(0.75f)
        assertThat(success.doneDays).isEqualTo(3)
        assertThat(success.bestStreak).isEqualTo(3)
        assertThat(stats.habitSuccessRate).isWithin(1e-6f).of(0.75f)
    }

    @Test
    fun habitsStartingAfterThePeriodAreLeftOut() {
        val habit = Habit(id = 1, title = "Later", startDate = span.end.plusDays(1))
        assertThat(compute(StatsInput(span, days, habits = listOf(HabitRecord(habit, emptyMap())))).habitSuccessRate).isNull()
    }

    @Test
    fun notesAndTopTagsAndProjects() {
        val work = Tag(id = 10, name = "work")
        val home = Tag(id = 11, name = "home")
        val launch = ProjectSummary(Project(id = 5, title = "Launch"), totalTasks = 4, completedTasks = 3)
        val garden = ProjectSummary(Project(id = 6, title = "Garden"), totalTasks = 4, completedTasks = 1)
        val stats = compute(
            StatsInput(
                span = span,
                buckets = days,
                completedTasks = listOf(
                    done(1, at(0, 9), project = 5, tags = listOf(10)),
                    done(2, at(1, 9), project = 5, tags = listOf(10, 11)),
                    done(3, at(1, 9), project = 6, tags = listOf(10)),
                    done(4, at(1, 9), tags = listOf(99)), // unknown tag is ignored
                ),
                notesCreated = listOf(start, today, start.minusDays(3)),
                projects = listOf(garden, launch),
                tags = listOf(work, home),
            ),
        )
        assertThat(stats.notesWritten).isEqualTo(2)
        assertThat(stats.topTags).containsExactly(RankedTag(work, 3), RankedTag(home, 1)).inOrder()
        assertThat(stats.topProjects.map { it.project.title to it.count }).containsExactly("Launch" to 2, "Garden" to 1).inOrder()
        // Projects are listed by progress.
        assertThat(stats.projects.map { it.project.title }).containsExactly("Launch", "Garden").inOrder()
    }

    @Test
    fun yearBucketsFindTheBestMonthAndLongestStreak() {
        val year = DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        val months = (1..12).map { m ->
            val first = LocalDate.of(2026, m, 1)
            DateSpan(first, first.plusMonths(1).minusDays(1))
        }
        val tasks = listOf(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 5),
            LocalDate.of(2026, 7, 9),
        ).mapIndexed { i, d -> done(i.toLong(), d.atTime(10, 0)) }
        val stats = StatisticsCalculator.compute(StatsInput(year, months, completedTasks = tasks), LocalDate.of(2026, 12, 31))
        assertThat(stats.bestBucket).isEqualTo(2) // March
        assertThat(stats.completedPerBucket[6]).isEqualTo(1)
        assertThat(stats.longestStreak).isEqualTo(3)
    }

    @Test
    fun longestRunHandlesGapsAndEmptyInput() {
        assertThat(StatisticsCalculator.longestRun(emptyList())).isEqualTo(0)
        val d = LocalDate.of(2026, 1, 1)
        assertThat(StatisticsCalculator.longestRun(listOf(d, d.plusDays(2), d.plusDays(3)))).isEqualTo(2)
    }
}
