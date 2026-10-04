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
    /** Exact fraction of the target reached, in 0..1. Status decisions use this, not [progress]. */
    val progressFraction: Double
        get() = if (target <= 0.0) 0.0 else (currentValue / target).coerceIn(0.0, 1.0)

    val isComplete: Boolean get() = target > 0.0 && currentValue >= target

    /**
     * Display progress in 0..1. Only a complete goal reaches 1: a float cannot tell
     * 99,999,999 / 100,000,000 from 1, so unfinished goals stop just below it.
     */
    val progress: Float
        get() = when {
            isComplete -> 1f
            else -> progressFraction.toFloat().coerceAtMost(Math.nextDown(1f))
        }
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
    /** Amount still needed per week to finish on time (0 when done or overdue; all of it on the deadline day). */
    val neededPerWeek: Double,
) {
    enum class Status { DONE, AHEAD, ON_TRACK, BEHIND, OVERDUE }

    companion object {
        private const val TOLERANCE = 0.05

        fun of(goal: Goal, start: java.time.LocalDate, today: java.time.LocalDate): GoalPace? {
            val deadline = goal.deadline ?: return null
            val total = java.time.temporal.ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(1)
            val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceIn(0, total)
            // On (or after) the deadline the whole target is due, even for a goal created that day.
            val expected = if (today >= deadline) 1.0 else elapsed.toDouble() / total
            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadline)
            val remaining = (goal.target - goal.currentValue).coerceAtLeast(0.0)
            val progress = goal.progressFraction
            val status = when {
                goal.isComplete -> Status.DONE
                daysLeft < 0 -> Status.OVERDUE
                progress > expected + TOLERANCE -> Status.AHEAD
                progress < expected - TOLERANCE -> Status.BEHIND
                else -> Status.ON_TRACK
            }
            val weeksLeft = daysLeft / 7.0
            val perWeek = when {
                remaining <= 0.0 || daysLeft < 0 -> 0.0
                // Deadline day: everything left is needed now.
                daysLeft == 0L -> remaining
                else -> remaining / weeksLeft.coerceAtLeast(1.0)
            }
            return GoalPace(expected.toFloat(), status, daysLeft, perWeek)
        }
    }
}
