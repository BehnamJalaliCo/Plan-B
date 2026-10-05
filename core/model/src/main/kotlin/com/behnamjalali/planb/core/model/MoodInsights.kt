package com.behnamjalali.planb.core.model

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sqrt

/** Part of the day a check-in was made in. */
enum class DayPart {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
    ;

    companion object {
        /** 05–12 morning, 12–17 afternoon, 17–22 evening, otherwise night. */
        fun of(time: LocalTime): DayPart = when (time.hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** The averages of one day's check-ins. */
data class DailyMood(val date: LocalDate, val mood: Float?, val energy: Float?, val checkIns: Int)

/** Something the mood is compared with. */
enum class MoodFactor { HABITS, FOCUS, SLEEP }

enum class CorrelationStrength { NONE, WEAK, MODERATE, STRONG }

/** Pearson correlation of the daily mood with [factor] over [days] days. */
data class MoodCorrelation(val factor: MoodFactor, val r: Float, val days: Int) {
    val strength: CorrelationStrength
        get() = when (abs(r)) {
            in 0f..<0.2f -> CorrelationStrength.NONE
            in 0.2f..<0.4f -> CorrelationStrength.WEAK
            in 0.4f..<0.6f -> CorrelationStrength.MODERATE
            else -> CorrelationStrength.STRONG
        }
    val positive: Boolean get() = r > 0
}

/** The average mood on days a habit was done and on due days it was not. */
data class HabitMoodEffect(val habitId: EntityId, val title: String, val doneMood: Float, val notDoneMood: Float, val doneDays: Int, val notDoneDays: Int) {
    val difference: Float get() = doneMood - notDoneMood
}

data class MoodTrackerInsights(
    /** Days with a check-in in the window, oldest first. */
    val daily: List<DailyMood>,
    val averageMood: Float?,
    val averageEnergy: Float?,
    val checkIns: Int,
    /** Average mood of the last 7 days and of the 7 before (null without check-ins). */
    val lastWeek: Float?,
    val weekBefore: Float?,
    /** Average mood for each energy level 1..5 (null where there is none). */
    val moodByEnergy: Map<Int, Float?>,
    val moodByDayPart: Map<DayPart, Float?>,
    val correlations: List<MoodCorrelation>,
    /** Habits with the largest difference, largest first (at most [MoodInsights.MAX_EFFECTS]). */
    val habitEffects: List<HabitMoodEffect>,
)

/**
 * The mood and energy tracker's insights (Plan-B Pro #30). Correlations are plain Pearson
 * coefficients over days that have a check-in; they describe the user's own data and never
 * claim a cause. A factor needs [MIN_DAYS] days and some variation, a habit [MIN_HABIT_DAYS]
 * days on each side, to be shown at all.
 */
object MoodInsights {
    const val WINDOW_DAYS = 90L
    const val MIN_DAYS = 7
    const val MIN_HABIT_DAYS = 3
    const val MAX_EFFECTS = 3

    fun daily(entries: List<MoodEntry>): Map<LocalDate, DailyMood> = entries.groupBy { it.date }.mapValues { (date, list) ->
        DailyMood(
            date = date,
            mood = list.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }.averageOrNull(),
            energy = list.mapNotNull { it.energy?.takeIf { e -> e in MoodEntry.RANGE } }.averageOrNull(),
            checkIns = list.size,
        )
    }.toSortedMap()

    /**
     * [entries] should cover the window ending [today]; [focusMinutes] and [sleepMinutes] are per
     * day (missing days: no focus, no sleep data); [habits] with their check-ins.
     */
    fun analyze(
        entries: List<MoodEntry>,
        today: LocalDate,
        habits: List<Pair<Habit, Map<LocalDate, Int>>> = emptyList(),
        focusMinutes: Map<LocalDate, Int> = emptyMap(),
        sleepMinutes: Map<LocalDate, Long> = emptyMap(),
    ): MoodTrackerInsights {
        val inWindow = entries.filter { it.date > today.minusDays(WINDOW_DAYS) && it.date <= today }
        val days = daily(inWindow)
        val moods = inWindow.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }
        val moodDays = days.values.filter { it.mood != null }
        fun weekAverage(from: LocalDate, to: LocalDate) =
            inWindow.filter { it.date in from..to }.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }.averageOrNull()
        val habitsDone: (LocalDate) -> Double = { date -> habits.count { (h, amounts) -> HabitStats.isDone(h, amounts, date) }.toDouble() }
        val correlations = listOfNotNull(
            correlation(MoodFactor.HABITS, moodDays) { habitsDone(it.date) }.takeIf { habits.isNotEmpty() },
            correlation(MoodFactor.FOCUS, moodDays) { (focusMinutes[it.date] ?: 0).toDouble() },
            correlation(MoodFactor.SLEEP, moodDays.filter { it.date in sleepMinutes }) { sleepMinutes.getValue(it.date).toDouble() },
        )
        return MoodTrackerInsights(
            daily = days.values.toList(),
            averageMood = moods.averageOrNull(),
            averageEnergy = inWindow.mapNotNull { it.energy?.takeIf { e -> e in MoodEntry.RANGE } }.averageOrNull(),
            checkIns = inWindow.size,
            lastWeek = weekAverage(today.minusDays(6), today),
            weekBefore = weekAverage(today.minusDays(13), today.minusDays(7)),
            moodByEnergy = MoodEntry.RANGE.associateWith { level ->
                inWindow.filter { it.energy == level }.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }.averageOrNull()
            },
            moodByDayPart = DayPart.entries.associateWith { part ->
                inWindow.filter { it.time?.let(DayPart::of) == part }.mapNotNull { it.mood?.takeIf { m -> m in MoodEntry.RANGE } }.averageOrNull()
            },
            correlations = correlations,
            habitEffects = habits.mapNotNull { (habit, amounts) -> habitEffect(habit, amounts, moodDays) }
                .sortedByDescending { abs(it.difference) }
                .take(MAX_EFFECTS),
        )
    }

    private fun correlation(factor: MoodFactor, days: List<DailyMood>, x: (DailyMood) -> Double): MoodCorrelation? {
        if (days.size < MIN_DAYS) return null
        val r = pearson(days.map(x), days.map { it.mood!!.toDouble() }) ?: return null
        return MoodCorrelation(factor, r.toFloat(), days.size)
    }

    private fun habitEffect(habit: Habit, amounts: Map<LocalDate, Int>, days: List<DailyMood>): HabitMoodEffect? {
        val due = days.filter { HabitStats.isScheduled(habit, it.date) }
        val (done, notDone) = due.partition { HabitStats.isDone(habit, amounts, it.date) }
        if (done.size < MIN_HABIT_DAYS || notDone.size < MIN_HABIT_DAYS) return null
        return HabitMoodEffect(
            habitId = habit.id,
            title = habit.title,
            doneMood = done.map { it.mood!! }.average().toFloat(),
            notDoneMood = notDone.map { it.mood!! }.average().toFloat(),
            doneDays = done.size,
            notDoneDays = notDone.size,
        )
    }

    /** Pearson's r, or null when either side has no variation (or the sizes differ). */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 2) return null
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        xs.indices.forEach { i ->
            val dx = xs[i] - mx
            val dy = ys[i] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx < EPSILON || syy < EPSILON) return null
        return (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
    }

    private const val EPSILON = 1e-9

    @JvmName("averageOfInts")
    private fun List<Int>.averageOrNull(): Float? = if (isEmpty()) null else sum().toFloat() / size
}
