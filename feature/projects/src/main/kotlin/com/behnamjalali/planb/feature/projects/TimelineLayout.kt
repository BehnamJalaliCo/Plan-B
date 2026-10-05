package com.behnamjalali.planb.feature.projects

import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One row of the project timeline: a task bar or a milestone diamond. */
data class TimelineBar(
    /** The task to open, or null for a milestone. */
    val taskId: EntityId?,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val milestone: Boolean = false,
    val completed: Boolean = false,
    val overdue: Boolean = false,
)

/**
 * What the project timeline (Plan-B Pro #9) draws: the rows in order, the day range and how
 * many tasks have no date at all. Pure, so it is tested without a screen.
 */
data class TimelineLayout(
    val bars: List<TimelineBar>,
    val from: LocalDate,
    val to: LocalDate,
    val undatedTasks: Int,
) {
    val days: Int get() = ChronoUnit.DAYS.between(from, to).toInt() + 1

    fun dayIndex(date: LocalDate): Int = ChronoUnit.DAYS.between(from, date).toInt()

    /** Row of each task, for dependency connectors. */
    val rowOfTask: Map<EntityId, Int> by lazy {
        bars.withIndex().mapNotNull { (i, bar) -> bar.taskId?.let { it to i } }.toMap()
    }

    companion object {
        /** Days shown before the earliest item and after the latest one. */
        const val LEAD_DAYS = 3L
        const val TAIL_DAYS = 7L

        /** The longest range drawn (about four years); items further out are cut at the edge. */
        const val MAX_DAYS = 1_500L

        /**
         * A task spans from its start date (or planned date, or deadline) to its deadline (or
         * planned date); an end before the start is drawn as a one-day bar. Milestones with a
         * date are diamonds. Rows are ordered by start, end, then title; the range always
         * includes [today].
         */
        fun build(tasks: List<Task>, milestones: List<ProjectMilestone>, today: LocalDate): TimelineLayout {
            val taskBars = tasks.mapNotNull { task ->
                val start = task.startDate ?: task.dueDate ?: task.deadline ?: return@mapNotNull null
                val end = listOfNotNull(task.deadline, task.dueDate).maxOrNull()?.takeIf { it >= start } ?: start
                TimelineBar(task.id, task.title, start, end, completed = task.isCompleted, overdue = task.isOverdue(today))
            }
            val milestoneBars = milestones.mapNotNull { m ->
                m.date?.let { TimelineBar(null, m.title, it, it, milestone = true, completed = m.completed) }
            }
            val bars = (taskBars + milestoneBars).sortedWith(compareBy<TimelineBar> { it.start }.thenBy { it.end }.thenBy { it.title })
            val earliest = (bars.map { it.start } + today).min().minusDays(LEAD_DAYS)
            val latest = (bars.map { it.end } + today).max().plusDays(TAIL_DAYS)
            val to = if (ChronoUnit.DAYS.between(earliest, latest) > MAX_DAYS) earliest.plusDays(MAX_DAYS) else latest
            return TimelineLayout(bars, earliest, to, undatedTasks = tasks.size - taskBars.size)
        }

        /**
         * The left edge (in pixels) of day [index] on a canvas [width] wide: time flows from
         * the reading start, so right-to-left in Persian.
         */
        fun dayLeft(index: Int, dayWidth: Float, width: Float, rtl: Boolean): Float =
            if (rtl) width - (index + 1) * dayWidth else index * dayWidth
    }
}
