package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** The completion rate of one period; [rate] is null when nothing was due in it (yet). */
data class HabitPeriodRate(val span: DateSpan, val rate: Float?, val doneDays: Int)

/** Whether the recent completion rate went up, down or stayed about the same. */
enum class TrendDirection { UP, DOWN, STEADY }

data class HabitTrend(val recent: Float, val previous: Float, val direction: TrendDirection) {
    val change: Float get() = recent - previous
}

/** Advanced statistics of one habit (Plan-B Pro #28). */
data class HabitAnalyticsResult(
    val currentStreak: Streak,
    val bestStreak: Streak,
    /** Days the target was met since the habit started. */
    val totalDoneDays: Int,
    val week: HabitPeriodRate,
    val month: HabitPeriodRate,
    val year: HabitPeriodRate,
    /** Since the start. */
    val overall: Float?,
    /** Share of due days done per weekday over the last year; null for weekdays never due. */
    val weekdayRates: Map<DayOfWeek, Float?>,
    val bestWeekday: DayOfWeek?,
    val worstWeekday: DayOfWeek?,
    /** Oldest first, in the user's calendar. */
    val weeks: List<HabitPeriodRate>,
    val months: List<HabitPeriodRate>,
    /** Last 30 days against the 30 before; null without enough history. */
    val trend: HabitTrend?,
)

/**
 * Pure calculations behind the habit statistics screen (Plan-B Pro #28). Calendar periods
 * (weeks from the user's first day, Jalali or Gregorian months and years) are computed by the
 * caller and passed in as [DateSpan]s, so this stays calendar-agnostic. Today counts once it is
 * done and is never counted as missed (like [HabitStats.completionRate]).
 */
object HabitAnalytics {
    const val TREND_DAYS = 30L
    private const val WEEKDAY_WINDOW = 364L
    private const val MIN_WEEKDAY_SAMPLES = 2
    private const val STEADY = 0.05f

    fun analyze(
        habit: Habit,
        amounts: Map<LocalDate, Int>,
        today: LocalDate,
        weekStart: DayOfWeek,
        week: DateSpan,
        month: DateSpan,
        year: DateSpan,
        weeks: List<DateSpan>,
        months: List<DateSpan>,
    ): HabitAnalyticsResult {
        val weekdayRates = weekdayRates(habit, amounts, today)
        val ranked = weekdayRates.entries.filter { it.value != null }
        return HabitAnalyticsResult(
            currentStreak = HabitStats.currentStreak(habit, amounts, today, weekStart),
            bestStreak = HabitStats.bestStreak(habit, amounts, today, weekStart),
            totalDoneDays = amounts.count { (date, amount) -> amount >= habit.target && date >= habit.startDate && date <= today },
            week = periodRate(habit, amounts, week, today),
            month = periodRate(habit, amounts, month, today),
            year = periodRate(habit, amounts, year, today),
            overall = rate(habit, amounts, DateSpan(minOf(habit.startDate, today), today), today),
            weekdayRates = weekdayRates,
            bestWeekday = ranked.maxByOrNull { it.value!! }?.key,
            worstWeekday = ranked.minByOrNull { it.value!! }?.key?.takeIf { ranked.size > 1 },
            weeks = weeks.map { periodRate(habit, amounts, it, today) },
            months = months.map { periodRate(habit, amounts, it, today) },
            trend = trend(habit, amounts, today),
        )
    }

    /** The rate of [span] (null when no day of it was due up to today). */
    fun periodRate(habit: Habit, amounts: Map<LocalDate, Int>, span: DateSpan, today: LocalDate): HabitPeriodRate {
        val done = span.dates().count { it >= habit.startDate && it <= today && HabitStats.isDone(habit, amounts, it) }
        return HabitPeriodRate(span, rate(habit, amounts, span, today), done)
    }

    private fun rate(habit: Habit, amounts: Map<LocalDate, Int>, span: DateSpan, today: LocalDate): Float? {
        val from = maxOf(span.start, habit.startDate)
        val todayCounts = HabitStats.isDone(habit, amounts, today)
        val to = minOf(span.end, if (todayCounts) today else today.minusDays(1))
        if (to < from) return null
        if (habit.schedule !is HabitSchedule.TimesPerWeek && generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.none { HabitStats.isScheduled(habit, it) }) {
            return null
        }
        return HabitStats.completionRate(habit, amounts, from, to)
    }

    /** Share of due days that were done, per weekday, over the last year (today only when done). */
    fun weekdayRates(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate): Map<DayOfWeek, Float?> {
        val due = IntArray(7)
        val done = IntArray(7)
        var d = maxOf(habit.startDate, today.minusDays(WEEKDAY_WINDOW - 1))
        while (d <= today) {
            val isDone = HabitStats.isDone(habit, amounts, d)
            if (HabitStats.isScheduled(habit, d) && (d < today || isDone)) {
                val i = d.dayOfWeek.value - 1
                due[i]++
                if (isDone) done[i]++
            }
            d = d.plusDays(1)
        }
        return DayOfWeek.entries.associateWith { day ->
            val i = day.value - 1
            if (due[i] < MIN_WEEKDAY_SAMPLES) null else done[i].toFloat() / due[i]
        }
    }

    fun trend(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate): HabitTrend? {
        val recentSpan = DateSpan(today.minusDays(TREND_DAYS - 1), today)
        val previousSpan = DateSpan(today.minusDays(2 * TREND_DAYS - 1), today.minusDays(TREND_DAYS))
        if (previousSpan.start < habit.startDate) return null
        val recent = rate(habit, amounts, recentSpan, today) ?: return null
        val previous = rate(habit, amounts, previousSpan, today) ?: return null
        val direction = when {
            recent - previous > STEADY -> TrendDirection.UP
            previous - recent > STEADY -> TrendDirection.DOWN
            else -> TrendDirection.STEADY
        }
        return HabitTrend(recent, previous, direction)
    }

    /**
     * For each length in [lengths], the first day a run of done scheduled days reached it (day
     * schedules only; "times per week" habits have week streaks and return nothing).
     */
    fun streakMilestones(habit: Habit, amounts: Map<LocalDate, Int>, today: LocalDate, lengths: List<Int>): Map<Int, LocalDate> {
        if (habit.schedule is HabitSchedule.TimesPerWeek) return emptyMap()
        val firstDone = amounts.filter { (date, amount) -> amount >= habit.target && date >= habit.startDate }.keys.minOrNull() ?: return emptyMap()
        val result = mutableMapOf<Int, LocalDate>()
        val pending = lengths.sorted().toMutableList()
        var run = 0
        var d = firstDone
        while (d <= today && pending.isNotEmpty()) {
            if (HabitStats.isScheduled(habit, d)) {
                if (HabitStats.isDone(habit, amounts, d)) {
                    run++
                    while (pending.isNotEmpty() && run >= pending.first()) result[pending.removeAt(0)] = d
                } else if (d != today) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }
        return result
    }
}
