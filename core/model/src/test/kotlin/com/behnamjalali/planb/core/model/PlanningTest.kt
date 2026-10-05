package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class PlanningTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun overdue_usesDeadlineWhenPresent() {
        val plannedYesterday = Task(title = "t", dueDate = today.minusDays(1))
        assertThat(plannedYesterday.isOverdue(today)).isTrue()
        // Planned in the past but the hard deadline is ahead: carried over, not late.
        assertThat(plannedYesterday.copy(deadline = today.plusDays(5)).isOverdue(today)).isFalse()
        // Planned later but the deadline has passed: late.
        assertThat(Task(title = "t", dueDate = today.plusDays(3), deadline = today.minusDays(1)).isOverdue(today)).isTrue()
        assertThat(Task(title = "t", deadline = today.minusDays(1), status = TaskStatus.DONE).isOverdue(today)).isFalse()
        assertThat(Task(title = "t").isOverdue(today)).isFalse()
    }

    @Test
    fun deadlineUrgency() {
        assertThat(Deadlines.urgency(today.minusDays(1), today)).isEqualTo(DeadlineUrgency.OVERDUE)
        assertThat(Deadlines.urgency(today, today)).isEqualTo(DeadlineUrgency.TODAY)
        assertThat(Deadlines.urgency(today.plusDays(2), today)).isEqualTo(DeadlineUrgency.SOON)
        assertThat(Deadlines.urgency(today.plusDays(3), today)).isEqualTo(DeadlineUrgency.LATER)
        assertThat(Deadlines.daysLeft(today.plusDays(2), today)).isEqualTo(2)
    }

    @Test
    fun eisenhower_quadrants() {
        val important = Task(title = "a", priority = Priority.MEDIUM, dueDate = today.plusDays(1))
        assertThat(EisenhowerMatrix.quadrantOf(important, today, 2)).isEqualTo(EisenhowerQuadrant.DO)
        assertThat(EisenhowerMatrix.quadrantOf(important, today, 0)).isEqualTo(EisenhowerQuadrant.SCHEDULE)
        assertThat(EisenhowerMatrix.quadrantOf(Task(title = "b", priority = Priority.LOW, deadline = today), today, 2))
            .isEqualTo(EisenhowerQuadrant.DELEGATE)
        assertThat(EisenhowerMatrix.quadrantOf(Task(title = "c"), today, 7)).isEqualTo(EisenhowerQuadrant.ELIMINATE)
        // Overdue is always urgent, whatever the threshold.
        assertThat(EisenhowerMatrix.isUrgent(Task(title = "d", dueDate = today.minusDays(9)), today, 1)).isTrue()
    }

    @Test
    fun eisenhower_moveChangesPriorityAndDateSensibly() {
        val task = Task(title = "t", priority = Priority.NONE, dueDate = today.plusDays(10), startDate = today.plusDays(9))
        val toDo = EisenhowerMatrix.move(task, EisenhowerQuadrant.DO, today, 2).task
        assertThat(toDo.priority).isEqualTo(Priority.HIGH)
        assertThat(toDo.dueDate).isEqualTo(today)
        assertThat(toDo.startDate).isEqualTo(today)
        assertThat(EisenhowerMatrix.quadrantOf(toDo, today, 2)).isEqualTo(EisenhowerQuadrant.DO)

        val back = EisenhowerMatrix.move(toDo, EisenhowerQuadrant.ELIMINATE, today, 2)
        assertThat(back.task.priority).isEqualTo(Priority.LOW)
        assertThat(back.task.dueDate).isEqualTo(today.plusDays(3))
        assertThat(back.keptUrgentByDeadline).isFalse()
        assertThat(EisenhowerMatrix.quadrantOf(back.task, today, 2)).isEqualTo(EisenhowerQuadrant.ELIMINATE)

        // Medium stays medium when it is already important.
        val medium = task.copy(priority = Priority.MEDIUM)
        assertThat(EisenhowerMatrix.move(medium, EisenhowerQuadrant.SCHEDULE, today, 2).task.priority).isEqualTo(Priority.MEDIUM)

        // A near deadline is never moved; the result says it is still urgent.
        val deadline = Task(title = "d", priority = Priority.HIGH, deadline = today.plusDays(1))
        val kept = EisenhowerMatrix.move(deadline, EisenhowerQuadrant.SCHEDULE, today, 2)
        assertThat(kept.keptUrgentByDeadline).isTrue()
        assertThat(kept.task.deadline).isEqualTo(today.plusDays(1))
    }

    @Test
    fun dependencies_cycleDetection() {
        // 3 waits for 2, 2 waits for 1.
        val edges = mapOf(3L to listOf(2L), 2L to listOf(1L))
        assertThat(TaskDependencies.wouldCreateCycle(edges, 1, 3)).isTrue()
        assertThat(TaskDependencies.wouldCreateCycle(edges, 1, 2)).isTrue()
        assertThat(TaskDependencies.wouldCreateCycle(edges, 4, 4)).isTrue()
        assertThat(TaskDependencies.wouldCreateCycle(edges, 3, 1)).isFalse()
        assertThat(TaskDependencies.wouldCreateCycle(edges, 4, 3)).isFalse()
        // A cycle already in the data (restored backup) never makes the search loop.
        val broken = mapOf(1L to listOf(2L), 2L to listOf(1L))
        assertThat(TaskDependencies.wouldCreateCycle(broken, 5, 1)).isFalse()
        assertThat(TaskDependencies.wouldCreateCycle(broken, 1, 2)).isTrue()
    }

    @Test
    fun dependencies_largeChainIsFast() {
        val edges = (2L..5_000L).associateWith { listOf(it - 1) }
        assertThat(TaskDependencies.wouldCreateCycle(edges, 1, 5_000)).isTrue()
        assertThat(TaskDependencies.wouldCreateCycle(edges, 6_000, 5_000)).isFalse()
    }

    @Test
    fun smartFilter_jsonRoundTripAndVersion() {
        val filter = SmartFilter(
            projectIds = setOf(3, 1),
            noProject = true,
            tagIds = setOf(7),
            priorities = setOf(Priority.HIGH, Priority.MEDIUM),
            statuses = setOf(TaskStatus.IN_PROGRESS),
            dateRange = SmartDateRange.CUSTOM,
            from = today,
            to = today.plusDays(30),
            hasDeadline = true,
            text = "گزارش",
            sort = TaskSort.DEADLINE,
        )
        val json = SmartFilterCodec.encode(filter)
        assertThat(json).startsWith("{\"version\":1,")
        assertThat(json).contains("\"date\":\"CUSTOM\"")
        assertThat(SmartFilterCodec.decode(json)).isEqualTo(filter)
        assertThat(SmartFilterCodec.decode(SmartFilterCodec.encode(SmartFilter()))).isEqualTo(SmartFilter())
    }

    @Test
    fun smartFilter_decodeIsTolerant() {
        assertThat(SmartFilterCodec.decode(null)).isEqualTo(SmartFilter())
        assertThat(SmartFilterCodec.decode("not json")).isEqualTo(SmartFilter())
        val future = """{"version":7,"priorities":["HIGH","URGENT"],"date":"NEXT_YEAR","from":"bad","newField":{"x":1},"sort":"MAGIC"}"""
        assertThat(SmartFilterCodec.decode(future)).isEqualTo(SmartFilter(priorities = setOf(Priority.HIGH)))
    }

    @Test
    fun reminderRules() {
        assertThat(TaskReminderRules.normalizeNagInterval(15)).isEqualTo(15)
        assertThat(TaskReminderRules.normalizeNagInterval(7)).isEqualTo(TaskReminderRules.DEFAULT_NAG_INTERVAL)
        assertThat(TaskReminderRules.normalizeNagInterval(null)).isEqualTo(10)
        assertThat(TaskReminder(kind = TaskReminderKind.ABSOLUTE).isRelative).isFalse()
        assertThat(TaskReminder(kind = TaskReminderKind.DEADLINE, offsetMinutes = 60).isRelative).isTrue()
    }
}
