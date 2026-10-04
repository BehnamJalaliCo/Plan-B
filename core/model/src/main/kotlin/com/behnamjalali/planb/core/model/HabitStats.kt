package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Streak(val count: Int, val unit: Unit) {
    enum class Unit { DAYS, WEEKS }
}

/**
 * Pure habit statistics. [amounts] maps a date to the amount checked in that day.
 *
 * Streak rules:
 * - Day-based schedules count consecutive *scheduled* days that met the target.
 *   Unscheduled days neither extend nor break a streak.
 * - Today (or the current week) only extends a streak; being unfinished does not break it.
 * - "N times per week" streaks are counted in consecutive weeks that met N.
 */
object HabitStats {
    private const val MAX_LOOKBACK_DAYS = 3660L

    fun isScheduled(habit: Habit, date: LocalDate): Boolean {
        if (date < habit.startDate) return false
        return when (val s = habit.schedule) {
            HabitSchedule.Daily -> true
            is HabitSchedule.SelectedDays -> date.dayOfWeek in s.days
            is HabitSchedule.TimesPerWeek -> true
            is HabitSchedule.EveryNDays -> ChronoUnit.DAYS.between(habit.startDate, date) % s.interval == 0L
        }
    }

    fun isDone(habit: Habit, amounts: Map<LocalDate, Int>, date: LocalDate): Boolean =
        (amounts[date] ?: 0) >= habit.target

    fun currentStreak(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek): Streak =
        when (val s = habit.schedule) {
            is HabitSchedule.TimesPerWeek -> Streak(weekStreak(habit, s.times, amounts, today, weekStart), Streak.Unit.WEEKS)
            else -> Streak(dayStreak(habit, amounts, today), Streak.Unit.DAYS)
        }

    private fun dayStreak(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate): Int {
        var streak = 0
        var date = today
        val limit = maxOf(habit.startDate, today.minusDays(MAX_LOOKBACK_DAYS))
        if (isScheduled(habit, date) && !isDone(habit, amounts, date)) date = date.minusDays(1)
        while (date >= limit) {
            if (isScheduled(habit, date)) {
                if (isDone(habit, amounts, date)) streak++ else break
            }
            date = date.minusDays(1)
        }
        return streak
    }

    private fun weekStreak(
        habit: Habit,
        times: Int,
        amounts: Map<LocalDate, Int>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Int {
        fun weekStartOf(d: LocalDate) = d.minusDays(Math.floorMod(d.dayOfWeek.value - weekStart.value, 7).toLong())
        fun doneDays(start: LocalDate) = (0L until 7L).count { isDone(habit, amounts, start.plusDays(it)) }
        var start = weekStartOf(today)
        var streak = 0
        if (doneDays(start) >= times) streak++
        start = start.minusWeeks(1)
        val limit = weekStartOf(habit.startDate)
        while (start >= limit && streak < MAX_LOOKBACK_DAYS / 7) {
            if (doneDays(start) >= times) streak++ else break
            start = start.minusWeeks(1)
        }
        return streak
    }

    /** Fraction of scheduled days in [from, to] that met the target (0 when none scheduled). */
    fun completionRate(habit: Habit, amounts: Map<LocalDate, Int>, from: LocalDate, to: LocalDate): Float {
        if (habit.schedule is HabitSchedule.TimesPerWeek) {
            val days = ChronoUnit.DAYS.between(from, to) + 1
            val expected = (habit.schedule.times * days / 7.0).coerceAtLeast(1.0)
            val done = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.count { isDone(habit, amounts, it) }
            return (done / expected).toFloat().coerceIn(0f, 1f)
        }
        var scheduled = 0
        var done = 0
        var d = from
        while (d <= to) {
            if (isScheduled(habit, d)) {
                scheduled++
                if (isDone(habit, amounts, d)) done++
            }
            d = d.plusDays(1)
        }
        return if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    fun bestStreakDays(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate): Int {
        if (habit.schedule is HabitSchedule.TimesPerWeek) return 0
        var best = 0
        var run = 0
        var d = maxOf(habit.startDate, today.minusDays(MAX_LOOKBACK_DAYS))
        while (d <= today) {
            if (isScheduled(habit, d)) {
                if (isDone(habit, amounts, d)) {
                    run++
                    best = maxOf(best, run)
                } else if (d != today) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }
}
