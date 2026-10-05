package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A habit's linked Health Connect metric and the day's amount it read (Plan-B Pro #27). */
data class HealthReading(val metric: HealthMetric, val date: LocalDate, val value: Long)

/**
 * Rules for checking habits off from Health Connect (Plan-B Pro #27). Thresholds are stored in
 * the metric's base unit: steps, minutes of sleep, millilitres of water, minutes of exercise,
 * metres.
 */
object HealthHabits {
    /** How many days back (today included) a sync looks, so a day the app was not opened is filled in later. */
    const val LOOKBACK_DAYS = 7

    /** Sleep that ends between 18:00 the evening before and 18:00 counts for a day ("last night"). */
    val SLEEP_DAY_BOUNDARY: LocalTime = LocalTime.of(18, 0)

    /** The instants whose data counts for [date]: the local day, or the night that ends on it for sleep. */
    fun window(metric: HealthMetric, date: LocalDate, zone: ZoneId): Pair<Instant, Instant> = when (metric) {
        HealthMetric.SLEEP_MINUTES ->
            date.minusDays(1).atTime(SLEEP_DAY_BOUNDARY).atZone(zone).toInstant() to date.atTime(SLEEP_DAY_BOUNDARY).atZone(zone).toInstant()
        else -> date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()
    }

    /** Scheduled days of the last [LOOKBACK_DAYS] (not before the habit started), oldest first. */
    fun daysToCheck(habit: Habit, today: LocalDate): List<LocalDate> =
        (LOOKBACK_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }.filter { HabitStats.isScheduled(habit, it) }

    /**
     * The check-in amount to add to [date] after Health Connect reported [value]: enough to reach
     * the habit's target when the threshold is met, otherwise nothing. A day the sync already
     * checked once ([alreadyChecked]) is never checked again, so unchecking it by hand sticks;
     * manual check-ins are never removed.
     */
    fun delta(habit: Habit, value: Long?, amountOnDay: Int, alreadyChecked: Boolean): Int {
        val threshold = habit.healthThreshold ?: return 0
        if (habit.healthMetric == null || value == null || alreadyChecked) return 0
        if (threshold <= 0 || value < threshold) return 0
        return (habit.target - amountOnDay).coerceAtLeast(0)
    }

    fun defaultThreshold(metric: HealthMetric): Long = when (metric) {
        HealthMetric.STEPS -> 8_000
        HealthMetric.SLEEP_MINUTES -> 7 * 60
        HealthMetric.HYDRATION_ML -> 2_000
        HealthMetric.ACTIVE_MINUTES -> 30
        HealthMetric.DISTANCE_METERS -> 3_000
    }

    /** Thresholds the editor accepts, in the base unit. */
    fun range(metric: HealthMetric): LongRange = when (metric) {
        HealthMetric.STEPS -> 100L..100_000L
        HealthMetric.SLEEP_MINUTES -> 60L..16 * 60L
        HealthMetric.HYDRATION_ML -> 100L..10_000L
        HealthMetric.ACTIVE_MINUTES -> 5L..600L
        HealthMetric.DISTANCE_METERS -> 100L..100_000L
    }
}
