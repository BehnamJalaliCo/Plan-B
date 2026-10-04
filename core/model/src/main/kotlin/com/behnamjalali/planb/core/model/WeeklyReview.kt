package com.behnamjalali.planb.core.model

import java.time.LocalDate

data class HabitWeekSummary(val habit: Habit, val rate: Float, val doneDays: Int)

data class WeeklyReview(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** Completed task count for each of the 7 days, starting at [weekStart]. */
    val completedPerDay: List<Int>,
    val missedTasks: List<Task>,
    val habits: List<HabitWeekSummary>,
    val focusMinutes: Int,
    val projects: List<ProjectSummary>,
    val notesCreated: Int,
    val goals: List<Goal>,
    val nextWeekPriorities: List<Task>,
) {
    val completedTasks: Int get() = completedPerDay.sum()

    /** Average of individual habit rates (0 when there are no habits). */
    val habitCompletionRate: Float
        get() = if (habits.isEmpty()) 0f else habits.map { it.rate }.average().toFloat()
}
