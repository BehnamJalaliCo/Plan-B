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

    /**
     * The first scheduled day on or after [from] (never before the start date), computed
     * directly from the schedule, so there is no look-ahead limit. Null when the habit has
     * no scheduled days at all (an empty weekday set).
     */
    fun nextScheduledDate(habit: Habit, from: LocalDate): LocalDate? {
        val start = maxOf(from, habit.startDate)
        return when (val s = habit.schedule) {
            HabitSchedule.Daily, is HabitSchedule.TimesPerWeek -> start
            is HabitSchedule.SelectedDays -> (0L until 7L).map { start.plusDays(it) }.firstOrNull { it.dayOfWeek in s.days }
            is HabitSchedule.EveryNDays -> {
                val interval = s.interval.coerceAtLeast(1).toLong()
                val rest = Math.floorMod(ChronoUnit.DAYS.between(habit.startDate, start), interval)
                if (rest == 0L) start else start.plusDays(interval - rest)
            }
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

    private fun weekStartOf(d: LocalDate, weekStart: DayOfWeek): LocalDate =
        d.minusDays(Math.floorMod(d.dayOfWeek.value - weekStart.value, 7).toLong())

    private fun doneDaysInWeek(habit: Habit, amounts: Map<LocalDate, Int>, start: LocalDate): Int =
        (0L until 7L).count { isDone(habit, amounts, start.plusDays(it)) }

    private fun weekStreak(
        habit: Habit,
        times: Int,
        amounts: Map<LocalDate, Int>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Int {
        var start = weekStartOf(today, weekStart)
        var streak = 0
        if (doneDaysInWeek(habit, amounts, start) >= times) streak++
        start = start.minusWeeks(1)
        val limit = weekStartOf(habit.startDate, weekStart)
        while (start >= limit && streak < MAX_LOOKBACK_DAYS / 7) {
            if (doneDaysInWeek(habit, amounts, start) >= times) streak++ else break
            start = start.minusWeeks(1)
        }
        return streak
    }

    /**
     * Fraction of scheduled days in [from, to] that met the target (0 when none scheduled).
     * Days before the habit's start date never count. When [today] is given, an unfinished
     * [today] is not counted as missed yet: it only counts once it is done.
     */
    fun completionRate(
        habit: Habit,
        amounts: Map<LocalDate, Int>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate? = null,
    ): Float {
        val start = maxOf(from, habit.startDate)
        val end = if (today != null && to >= today && !isDone(habit, amounts, today)) minOf(to, today.minusDays(1)) else to
        if (habit.schedule is HabitSchedule.TimesPerWeek) {
            if (end < start) return 0f
            val days = ChronoUnit.DAYS.between(start, end) + 1
            val expected = (habit.schedule.times * days / 7.0).coerceAtLeast(1.0)
            val done = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.count { isDone(habit, amounts, it) }
            return (done / expected).toFloat().coerceIn(0f, 1f)
        }
        var scheduled = 0
        var done = 0
        var d = start
        while (d <= end) {
            if (isScheduled(habit, d)) {
                scheduled++
                if (isDone(habit, amounts, d)) done++
            }
            d = d.plusDays(1)
        }
        return if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    /**
     * Longest streak so far, in the same unit as [currentStreak]: scheduled days, or weeks
     * that met the target for "N times per week". The current day/week only extends a run.
     */
    fun bestStreak(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek): Streak {
        val s = habit.schedule
        if (s !is HabitSchedule.TimesPerWeek) return Streak(bestDayStreak(habit, amounts, today), Streak.Unit.DAYS)
        val current = weekStartOf(today, weekStart)
        var start = maxOf(weekStartOf(habit.startDate, weekStart), weekStartOf(today.minusDays(MAX_LOOKBACK_DAYS), weekStart))
        var best = 0
        var run = 0
        while (start <= current) {
            if (doneDaysInWeek(habit, amounts, start) >= s.times) {
                run++
                best = maxOf(best, run)
            } else if (start != current) {
                run = 0
            }
            start = start.plusWeeks(1)
        }
        return Streak(best, Streak.Unit.WEEKS)
    }

    /** Count of [bestStreak]; weeks for "N times per week" habits (counted from [weekStart]). */
    fun bestStreakDays(
        habit: Habit,
        amounts: Map<LocalDate, Int>,
        today: LocalDate,
        weekStart: DayOfWeek = DayOfWeek.SATURDAY,
    ): Int = bestStreak(habit, amounts, today, weekStart).count

    private fun bestDayStreak(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate): Int {
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
