package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: EntityId = NEW_ID,
    val title: String,
    val description: String = "",
    val target: Double = 100.0,
    val currentValue: Double = 0.0,
    val unit: String = "",
    val deadline: LocalDate? = null,
    val projectId: EntityId? = null,
    val notes: String = "",
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
) {
    val progress: Float
        get() = if (target <= 0.0) 0f else (currentValue / target).toFloat().coerceIn(0f, 1f)
}

data class GoalMilestone(
    val id: EntityId = NEW_ID,
    val goalId: EntityId,
    val title: String,
    val target: Double? = null,
    val completed: Boolean = false,
    val sortOrder: Long = 0,
)

/** How a goal is tracking against a linear plan from its creation to its deadline. */
data class GoalPace(
    val expectedProgress: Float,
    val status: Status,
    val daysLeft: Long,
    /** Amount still needed per week to finish on time (0 when done or overdue). */
    val neededPerWeek: Double,
) {
    enum class Status { DONE, AHEAD, ON_TRACK, BEHIND, OVERDUE }

    companion object {
        private const val TOLERANCE = 0.05f

        fun of(goal: Goal, start: java.time.LocalDate, today: java.time.LocalDate): GoalPace? {
            val deadline = goal.deadline ?: return null
            val total = java.time.temporal.ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(1)
            val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
            val expected = elapsed.toFloat() / total
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadline)
            val remaining = (goal.target - goal.currentValue).coerceAtLeast(0.0)
            val status = when {
                goal.progress >= 1f -> Status.DONE
                daysLeft < 0 -> Status.OVERDUE
                goal.progress > expected + TOLERANCE -> Status.AHEAD
                goal.progress < expected - TOLERANCE -> Status.BEHIND
                else -> Status.ON_TRACK
            }
            val weeksLeft = daysLeft / 7.0
            val perWeek = if (remaining <= 0.0 || daysLeft <= 0) 0.0 else remaining / weeksLeft.coerceAtLeast(1.0)
            return GoalPace(expected, status, daysLeft, perWeek)
        }
    }
}
