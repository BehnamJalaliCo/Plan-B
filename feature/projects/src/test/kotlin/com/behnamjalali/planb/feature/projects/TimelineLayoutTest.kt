package com.behnamjalali.planb.feature.projects

import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** The project timeline's rows and geometry (Plan-B Pro #9). */
class TimelineLayoutTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun barsSpanStartToDeadlineOrPlannedDate_milestonesAreDiamonds() {
        val layout = TimelineLayout.build(
            tasks = listOf(
                Task(id = 1, title = "Design", startDate = today, dueDate = today.plusDays(3)),
                Task(id = 2, title = "Build", dueDate = today.plusDays(5), deadline = today.plusDays(9)),
                Task(id = 3, title = "Only deadline", deadline = today.plusDays(2)),
                Task(id = 4, title = "Undated"),
                Task(id = 5, title = "Late", dueDate = today.minusDays(4), status = TaskStatus.TODO),
                Task(id = 6, title = "End before start", startDate = today.plusDays(4), dueDate = today.plusDays(1)),
            ),
            milestones = listOf(ProjectMilestone(projectId = 1, title = "Beta", date = today.plusDays(7)), ProjectMilestone(projectId = 1, title = "Someday")),
            today = today,
        )
        assertThat(layout.bars.map { it.title }).containsExactly("Late", "Design", "Only deadline", "End before start", "Build", "Beta").inOrder()
        val byTitle = layout.bars.associateBy { it.title }
        assertThat(byTitle.getValue("Build").start).isEqualTo(today.plusDays(5))
        assertThat(byTitle.getValue("Build").end).isEqualTo(today.plusDays(9))
        assertThat(byTitle.getValue("Only deadline").start).isEqualTo(today.plusDays(2))
        assertThat(byTitle.getValue("End before start").end).isEqualTo(today.plusDays(4))
        assertThat(byTitle.getValue("Late").overdue).isTrue()
        assertThat(byTitle.getValue("Beta").milestone).isTrue()
        assertThat(byTitle.getValue("Beta").taskId).isNull()
        assertThat(layout.undatedTasks).isEqualTo(1)
        // The range leads the earliest item and follows the latest one, today included.
        assertThat(layout.from).isEqualTo(today.minusDays(4 + TimelineLayout.LEAD_DAYS))
        assertThat(layout.to).isEqualTo(today.plusDays(9 + TimelineLayout.TAIL_DAYS))
        assertThat(layout.rowOfTask[2]).isEqualTo(4)
    }

    @Test
    fun emptyProject_stillShowsToday() {
        val layout = TimelineLayout.build(emptyList(), emptyList(), today)
        assertThat(layout.bars).isEmpty()
        assertThat(layout.dayIndex(today)).isEqualTo(TimelineLayout.LEAD_DAYS.toInt())
    }

    @Test
    fun timeFlowsFromTheReadingStart() {
        // Day 0 is at the left edge in English and at the right edge in Persian.
        assertThat(TimelineLayout.dayLeft(0, 10f, 100f, rtl = false)).isEqualTo(0f)
        assertThat(TimelineLayout.dayLeft(0, 10f, 100f, rtl = true)).isEqualTo(90f)
        assertThat(TimelineLayout.dayLeft(9, 10f, 100f, rtl = true)).isEqualTo(0f)
        assertThat(TimelineLayout.dayLeft(3, 10f, 100f, rtl = true)).isLessThan(TimelineLayout.dayLeft(2, 10f, 100f, rtl = true))
    }

    @Test
    fun veryLongRanges_areCut() {
        val layout = TimelineLayout.build(listOf(Task(id = 1, title = "Far", dueDate = today.plusYears(30))), emptyList(), today)
        assertThat(layout.days.toLong()).isEqualTo(TimelineLayout.MAX_DAYS + 1)
    }

    @Test
    fun threeHundredFiftyTasks_areLaidOutQuickly() {
        val tasks = (1L..350L).map { i ->
            Task(id = i, title = "Task $i", startDate = today.plusDays(i % 40), dueDate = today.plusDays(i % 40 + i % 7), deadline = if (i % 5 == 0L) today.plusDays(60) else null)
        }
        val milestones = (1..20).map { ProjectMilestone(projectId = 1, title = "M$it", date = today.plusDays(it * 3L)) }
        TimelineLayout.build(tasks, milestones, today) // warm-up
        val started = System.nanoTime()
        val layout = TimelineLayout.build(tasks, milestones, today)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertThat(layout.bars).hasSize(370)
        assertThat(layout.rowOfTask).hasSize(350)
        assertThat(millis).isLessThan(200L)
    }
}
