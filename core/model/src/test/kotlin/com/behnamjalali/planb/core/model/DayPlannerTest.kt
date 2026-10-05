package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DayPlannerTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val day = LocalDate.of(2026, 10, 4)

    private fun at(h: Int, m: Int = 0, date: LocalDate = day, zone: ZoneId = tehran): Instant =
        ZonedDateTime.of(date, LocalTime.of(h, m), zone).toInstant()

    private fun range(h1: Int, m1: Int, h2: Int, m2: Int) = TimeRange(at(h1, m1), at(h2, m2))

    private fun request(
        now: Instant = at(7),
        busy: List<TimeRange> = emptyList(),
        tasks: List<PlanTask> = emptyList(),
        settings: DayPlanSettings = DayPlanSettings(),
        date: LocalDate = day,
        zone: ZoneId = tehran,
    ) = DayPlanRequest(date, zone, now, settings, busy, tasks)

    private fun DayPlan.slot(id: Long): Pair<LocalTime, LocalTime>? = blocks.firstOrNull { it.taskId == id }?.let {
        it.start.atZone(tehran).toLocalTime() to it.end.atZone(tehran).toLocalTime()
    }

    @Test
    fun emptyDay_tasksFillFromTheStartWithBuffers() {
        val plan = DayPlanner.plan(request(tasks = listOf(PlanTask(1, estimatedMinutes = 60), PlanTask(2))))
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(10, 0))
        // Default estimate 30 minutes, after a 10-minute buffer.
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(10, 10) to LocalTime.of(10, 40))
        assertThat(plan.unplanned).isEmpty()
    }

    @Test
    fun busyEvents_areAvoidedWithBuffersOnBothSides() {
        val plan = DayPlanner.plan(
            request(busy = listOf(range(9, 30, 10, 0)), tasks = listOf(PlanTask(1, estimatedMinutes = 30), PlanTask(2, estimatedMinutes = 15))),
        )
        // 09:00–09:20 is free (buffer before the event): 30 minutes don't fit, 15 minutes do.
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(10, 10) to LocalTime.of(10, 40))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(9, 15))
    }

    @Test
    fun overlappingBusyRanges_areMerged() {
        val plan = DayPlanner.plan(
            request(
                busy = listOf(range(9, 0, 11, 0), range(10, 0, 12, 0), range(11, 30, 11, 45)),
                tasks = listOf(PlanTask(1, estimatedMinutes = 45)),
            ),
        )
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(12, 10) to LocalTime.of(12, 55))
    }

    @Test
    fun noBlockOverlapsAnotherOrBusyTime() {
        val busy = listOf(range(9, 45, 10, 30), range(13, 0, 14, 0), range(15, 5, 15, 50))
        val tasks = (1L..12L).map { PlanTask(it, estimatedMinutes = (it * 7 % 50 + 10).toInt(), priority = Priority.fromWeight((it % 4).toInt())) }
        val plan = DayPlanner.plan(request(busy = busy, tasks = tasks))
        val blocks = plan.blocks
        for (i in blocks.indices) {
            for (j in blocks.indices) if (i != j) assertThat(blocks[i].range.overlaps(blocks[j].range)).isFalse()
            busy.forEach { assertThat(blocks[i].range.overlaps(it)).isFalse() }
            assertThat(blocks[i].start).isAtLeast(at(9))
            assertThat(blocks[i].end).isAtMost(at(18))
        }
        assertThat(blocks.size + plan.unplanned.size).isEqualTo(tasks.size)
    }

    @Test
    fun buffersSeparateConsecutiveBlocks() {
        val plan = DayPlanner.plan(request(tasks = (1L..4L).map { PlanTask(it, estimatedMinutes = 20) }))
        plan.blocks.zipWithNext().forEach { (a, b) -> assertThat(java.time.Duration.between(a.end, b.start).toMinutes()).isAtLeast(10) }
    }

    @Test
    fun zeroBuffer_packsBlocksBackToBack() {
        val plan = DayPlanner.plan(
            request(settings = DayPlanSettings(bufferMinutes = 0), busy = listOf(range(10, 0, 11, 0)), tasks = listOf(PlanTask(1, estimatedMinutes = 60), PlanTask(2, estimatedMinutes = 60))),
        )
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(10, 0))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(11, 0) to LocalTime.of(12, 0))
    }

    @Test
    fun priorityAndDeadlines_decideTheOrder() {
        val tasks = listOf(
            PlanTask(1, priority = Priority.LOW),
            PlanTask(2, priority = Priority.HIGH),
            PlanTask(3, priority = Priority.NONE, deadline = day),
            PlanTask(4, priority = Priority.MEDIUM, deadline = day.plusDays(2)),
            PlanTask(5, priority = Priority.HIGH, deadline = day.minusDays(1)),
        )
        assertThat(DayPlanner.order(tasks, day).map { it.id }).containsExactly(5L, 3L, 4L, 2L, 1L).inOrder()
        val plan = DayPlanner.plan(request(tasks = tasks))
        assertThat(plan.blocks.map { it.taskId }).containsExactly(5L, 3L, 4L, 2L, 1L).inOrder()
    }

    @Test
    fun overdueFirst_thenSortOrder_thenId() {
        val tasks = listOf(
            PlanTask(7, dueDate = day, sortOrder = 2),
            PlanTask(3, dueDate = day, sortOrder = 2),
            PlanTask(9, dueDate = day.minusDays(3)),
            PlanTask(1, dueDate = day, sortOrder = 1),
            PlanTask(2),
        )
        assertThat(DayPlanner.order(tasks, day).map { it.id }).containsExactly(9L, 1L, 3L, 7L, 2L).inOrder()
    }

    @Test
    fun deterministic_inputOrderDoesNotMatter() {
        val tasks = (1L..8L).map { PlanTask(it, priority = Priority.fromWeight((it % 3).toInt()), estimatedMinutes = 25) }
        val a = DayPlanner.plan(request(tasks = tasks))
        val b = DayPlanner.plan(request(tasks = tasks.reversed()))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun blockedTasks_areNeverScheduled() {
        val plan = DayPlanner.plan(request(tasks = listOf(PlanTask(1, blocked = true, priority = Priority.HIGH), PlanTask(2))))
        assertThat(plan.blocks.map { it.taskId }).containsExactly(2L)
        assertThat(plan.unplanned).containsExactly(UnplannedTask(1, UnplannedReason.BLOCKED))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(9, 30))
    }

    @Test
    fun tasksThatDoNotFit_areReported() {
        val plan = DayPlanner.plan(
            request(
                busy = listOf(range(9, 0, 17, 0)),
                tasks = listOf(PlanTask(1, estimatedMinutes = 120), PlanTask(2, estimatedMinutes = 30), PlanTask(3, estimatedMinutes = 30)),
            ),
        )
        // Only 17:10–18:00 is free: one 30-minute task fits (and its buffer leaves 10 minutes).
        assertThat(plan.blocks.map { it.taskId }).containsExactly(2L)
        assertThat(plan.unplanned).containsExactly(UnplannedTask(1, UnplannedReason.NO_FIT), UnplannedTask(3, UnplannedReason.NO_FIT))
    }

    @Test
    fun longerThanTheDay_doesNotFit_andIsNeverSplit() {
        val plan = DayPlanner.plan(request(tasks = listOf(PlanTask(1, estimatedMinutes = 10 * 60))))
        assertThat(plan.blocks).isEmpty()
        assertThat(plan.unplanned.single().reason).isEqualTo(UnplannedReason.NO_FIT)
    }

    @Test
    fun aLaterShortTask_fillsAnEarlierGap() {
        val plan = DayPlanner.plan(
            request(busy = listOf(range(9, 40, 12, 0)), tasks = listOf(PlanTask(1, priority = Priority.HIGH, estimatedMinutes = 60), PlanTask(2, estimatedMinutes = 20))),
        )
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(12, 10) to LocalTime.of(13, 10))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(9, 20))
    }

    @Test
    fun now_startsTheDayAtTheNextFiveMinuteMark() {
        val plan = DayPlanner.plan(request(now = at(11, 2), tasks = listOf(PlanTask(1))))
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(11, 5) to LocalTime.of(11, 35))
        assertThat(DayPlanner.ceilToGrid(at(11, 5), tehran)).isEqualTo(at(11, 5))
        assertThat(DayPlanner.ceilToGrid(at(11, 5).plusSeconds(1), tehran)).isEqualTo(at(11, 10))
    }

    @Test
    fun afterWorkingHours_nothingFits() {
        val plan = DayPlanner.plan(request(now = at(18, 30), tasks = listOf(PlanTask(1))))
        assertThat(plan.blocks).isEmpty()
        assertThat(DayPlanner.freeTime(request(now = at(18, 30)))).isEmpty()
    }

    @Test
    fun aFutureDay_usesTheWholeWorkingDay() {
        val plan = DayPlanner.plan(request(now = at(17, 0), date = day.plusDays(1), tasks = listOf(PlanTask(1))))
        assertThat(plan.blocks.single().start).isEqualTo(at(9, 0, day.plusDays(1)))
    }

    @Test
    fun lunchBreak_isKeptFree_withoutBuffer() {
        val settings = DayPlanSettings(lunchEnabled = true, lunchStart = LocalTime.of(12, 0), lunchEnd = LocalTime.of(13, 0))
        val plan = DayPlanner.plan(request(now = at(11, 0), settings = settings, tasks = listOf(PlanTask(1, estimatedMinutes = 60), PlanTask(2, estimatedMinutes = 30))))
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(11, 0) to LocalTime.of(12, 0))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(13, 0) to LocalTime.of(13, 30))
    }

    @Test
    fun customAndInvalidWorkingHours() {
        val early = DayPlanner.plan(request(now = at(5), settings = DayPlanSettings(workStart = LocalTime.of(6, 30), workEnd = LocalTime.of(8, 0)), tasks = listOf(PlanTask(1, estimatedMinutes = 90), PlanTask(2))))
        assertThat(early.slot(1)).isEqualTo(LocalTime.of(6, 30) to LocalTime.of(8, 0))
        assertThat(early.unplanned.map { it.taskId }).containsExactly(2L)
        val inverted = DayPlanSettings(workStart = LocalTime.of(18, 0), workEnd = LocalTime.of(9, 0))
        assertThat(DayPlanner.workingWindow(day, tehran, inverted)).isNull()
        assertThat(DayPlanner.plan(request(settings = inverted, tasks = listOf(PlanTask(1)))).blocks).isEmpty()
    }

    @Test
    fun estimates_haveAMinimumAndDuplicatesArePlannedOnce() {
        val plan = DayPlanner.plan(request(tasks = listOf(PlanTask(1, estimatedMinutes = 1), PlanTask(1, estimatedMinutes = 1))))
        assertThat(plan.blocks).hasSize(1)
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(9, 5))
    }

    @Test
    fun dst_springForward_dayIsShorter_andAGapStartMovesForward() {
        val ny = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8) // clocks jump from 02:00 to 03:00
        val settings = DayPlanSettings(workStart = LocalTime.of(1, 0), workEnd = LocalTime.of(5, 0), bufferMinutes = 0)
        val window = DayPlanner.workingWindow(date, ny, settings)!!
        assertThat(window.minutes).isEqualTo(180)
        val plan = DayPlanner.plan(request(now = at(0, 0, date, ny), date = date, zone = ny, settings = settings, tasks = (1L..4L).map { PlanTask(it, estimatedMinutes = 60) }))
        assertThat(plan.blocks).hasSize(3)
        assertThat(plan.unplanned.single().taskId).isEqualTo(4L)
        assertThat(plan.blocks[1].start.atZone(ny).toLocalTime()).isEqualTo(LocalTime.of(3, 0))
        val inGap = DayPlanner.workingWindow(date, ny, settings.copy(workStart = LocalTime.of(2, 30)))!!
        assertThat(inGap.start.atZone(ny).toLocalTime()).isEqualTo(LocalTime.of(3, 30))
    }

    @Test
    fun dst_fallBack_dayIsLonger() {
        val ny = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 11, 1) // 01:00–02:00 happens twice
        val settings = DayPlanSettings(workStart = LocalTime.of(0, 0), workEnd = LocalTime.of(3, 0), bufferMinutes = 0)
        assertThat(DayPlanner.workingWindow(date, ny, settings)!!.minutes).isEqualTo(240)
        val plan = DayPlanner.plan(request(now = at(0, 0, date.minusDays(1), ny), date = date, zone = ny, settings = settings, tasks = (1L..4L).map { PlanTask(it, estimatedMinutes = 60) }))
        assertThat(plan.blocks).hasSize(4)
        plan.blocks.zipWithNext().forEach { (a, b) -> assertThat(a.end).isEqualTo(b.start) }
    }

    @Test
    fun timeZone_workingHoursFollowTheZoneOfTheRequest() {
        val utc = ZoneId.of("UTC")
        val plan = DayPlanner.plan(request(now = at(0, 0, day, utc), zone = utc, tasks = listOf(PlanTask(1))))
        assertThat(plan.blocks.single().start).isEqualTo(Instant.parse("2026-10-04T09:00:00Z"))
    }

    @Test
    fun replan_movesPastAndConflictingBlocks_keepsGoodOnes() {
        val now = at(11, 0)
        val existing = listOf(
            PlannedBlock(1, at(9, 0), at(9, 30)), // in the past, unfinished
            PlannedBlock(2, at(12, 0), at(12, 45)), // a new event now overlaps
            PlannedBlock(3, at(15, 0), at(15, 30)), // fine
        )
        val busy = listOf(range(12, 30, 13, 30))
        val request = request(now = now, busy = busy)
        assertThat(DayPlanner.replanCandidates(request, existing).map { it.taskId }).containsExactly(1L, 2L)
        val plan = DayPlanner.replan(request, existing)
        assertThat(plan.slot(1)).isEqualTo(LocalTime.of(11, 0) to LocalTime.of(11, 30))
        assertThat(plan.slot(2)).isEqualTo(LocalTime.of(13, 40) to LocalTime.of(14, 25))
        assertThat(plan.blocks.map { it.taskId }).doesNotContain(3L)
        plan.blocks.forEach { b -> assertThat(b.range.overlaps(TimeRange(at(14, 50), at(15, 40)))).isFalse() }
    }

    @Test
    fun replan_blocksFromAnEarlierDay_moveToToday() {
        val yesterday = day.minusDays(1)
        val existing = listOf(PlannedBlock(5, at(16, 0, yesterday), at(17, 0, yesterday)))
        val plan = DayPlanner.replan(request(now = at(8, 0)), existing)
        assertThat(plan.slot(5)).isEqualTo(LocalTime.of(9, 0) to LocalTime.of(10, 0))
    }

    @Test
    fun replan_withoutRoom_reportsNoFit() {
        val existing = listOf(PlannedBlock(1, at(9, 0), at(11, 0)))
        val plan = DayPlanner.replan(request(now = at(17, 0)), existing)
        assertThat(plan.blocks).isEmpty()
        assertThat(plan.unplanned).containsExactly(UnplannedTask(1, UnplannedReason.NO_FIT))
    }

    @Test
    fun replan_nothingToDo_whenAllBlocksAreAhead() {
        val existing = listOf(PlannedBlock(1, at(14, 0), at(14, 30)))
        val request = request(now = at(10, 0))
        assertThat(DayPlanner.needsReplan(request, existing)).isFalse()
        assertThat(DayPlanner.replan(request, existing).blocks).isEmpty()
    }

    @Test
    fun busyRanges_fromEventsAndTasks() {
        val event = CalendarEvent(title = "e", date = day, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), allDay = false)
        val overnight = CalendarEvent(title = "o", date = day, startTime = LocalTime.of(22, 0), endTime = LocalTime.of(1, 0), allDay = false)
        val allDay = CalendarEvent(title = "a", date = day, allDay = true)
        val ranges = DayPlanner.eventRanges(
            listOf(EventOccurrence(event, day), EventOccurrence(overnight, day), EventOccurrence(allDay, day), EventOccurrence(event, day.plusDays(1))),
            day,
            tehran,
        )
        assertThat(ranges).containsExactly(range(9, 0, 10, 0), TimeRange(at(22), day.plusDays(1).atStartOfDay(tehran).toInstant())).inOrder()

        val blocked = Task(id = 1, title = "b", scheduledStart = at(11), scheduledEnd = at(12))
        val timed = Task(id = 2, title = "t", dueDate = day, dueTime = LocalTime.of(15, 0))
        val timedLong = Task(id = 3, title = "l", dueDate = day, dueTime = LocalTime.of(16, 0), estimatedMinutes = 90)
        val otherDay = Task(id = 4, title = "o", dueDate = day.plusDays(1), dueTime = LocalTime.of(9, 0))
        assertThat(DayPlanner.taskRanges(listOf(blocked, timed, timedLong, otherDay), day, tehran))
            .containsExactly(range(11, 0, 12, 0), range(15, 0, 15, 30), range(16, 0, 17, 30)).inOrder()
        val open = Task(id = 5, title = "open", dueDate = day)
        val done = Task(id = 6, title = "done", dueDate = day, status = TaskStatus.DONE)
        assertThat(DayPlanner.candidates(listOf(blocked, timed, open, done, otherDay), day).map { it.id }).containsExactly(5L, 4L).inOrder()
    }

    @Test
    fun ritualState_focusOnlyForItsDay() {
        val state = RitualState(focusDate = day, focusTaskIds = listOf(3, 1))
        assertThat(state.focusFor(day)).containsExactly(3L, 1L).inOrder()
        assertThat(state.focusFor(day.plusDays(1))).isEmpty()
    }

    @Test
    fun planTask_fromTask() {
        val task = Task(id = 4, title = "t", priority = Priority.HIGH, dueDate = day, deadline = day.plusDays(1), estimatedMinutes = 50, openBlockerCount = 1)
        assertThat(PlanTask.of(task)).isEqualTo(PlanTask(4, Priority.HIGH, day, day.plusDays(1), 50, blocked = true))
    }
}
