package com.behnamjalali.planb.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The period the statistics screen summarizes (Plan-B Pro, reports). */
enum class StatsPeriod { WEEK, MONTH, YEAR }

/**
 * A closed date range ([start]..[end], both inclusive). Ranges are computed by the calendar
 * engine of the user's calendar (Jalali or Gregorian), so this model stays calendar-agnostic.
 */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun dates(): Sequence<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
}

/** A completed task as the statistics need it (local completion time, not the instant). */
data class CompletedTaskRecord(
    val taskId: EntityId,
    val completedAt: LocalDateTime,
    val dueDate: LocalDate? = null,
    val projectId: EntityId? = null,
    val tagIds: List<EntityId> = emptyList(),
)

/** A task planned for a date (completed or not), for the completion rate. */
data class DueTaskRecord(val dueDate: LocalDate, val completedOn: LocalDate?)

/** A finished focus session: its local start and its focused minutes. */
data class FocusRecord(val startedAt: LocalDateTime, val minutes: Int)

/** A habit with its check-ins (amount per date). */
data class HabitRecord(val habit: Habit, val amounts: Map<LocalDate, Int>)

/** Everything the aggregation reads; built by the data layer for [span]. */
data class StatsInput(
    val span: DateSpan,
    /** Chart buckets covering [span] in order: days of a week or month, or months of a year. */
    val buckets: List<DateSpan>,
    val completedTasks: List<CompletedTaskRecord> = emptyList(),
    val dueTasks: List<DueTaskRecord> = emptyList(),
    val focus: List<FocusRecord> = emptyList(),
    val habits: List<HabitRecord> = emptyList(),
    /** Creation dates of the notes written in [span]. */
    val notesCreated: List<LocalDate> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

data class HabitSuccess(val habit: Habit, val rate: Float, val doneDays: Int, val bestStreak: Int)

data class RankedTag(val tag: Tag, val count: Int)

data class RankedProject(val project: Project, val count: Int)

/** The aggregated statistics of one period. All counts only include days up to "today". */
data class PeriodStatistics(
    val span: DateSpan,
    val buckets: List<DateSpan>,
    val completedPerBucket: List<Int>,
    val focusMinutesPerBucket: List<Int>,
    val completedTotal: Int,
    /** Tasks planned in the period (up to today) that are done, over all planned; null when none. */
    val completionRate: Float?,
    val plannedTotal: Int,
    val onTime: Int,
    val late: Int,
    /** Completed tasks per weekday, indexed by [DayOfWeek.value] - 1 (Monday first). */
    val completedPerWeekday: List<Int>,
    val busiestWeekday: DayOfWeek?,
    /** Completed tasks per hour of day (0..23). */
    val completedPerHour: List<Int>,
    val busiestHour: Int?,
    val focusMinutes: Int,
    val focusSessions: Int,
    /** Average success rate of habits active in the period; null when there are none. */
    val habitSuccessRate: Float?,
    val habits: List<HabitSuccess>,
    val notesWritten: Int,
    val projects: List<ProjectSummary>,
    val topTags: List<RankedTag>,
    val topProjects: List<RankedProject>,
    /** Index into [buckets] with the most completed tasks; null when nothing was completed. */
    val bestBucket: Int?,
    /** Days with at least one completed task. */
    val activeDays: Int,
    /** Longest run of consecutive days with at least one completed task. */
    val longestStreak: Int,
) {
    val isEmpty: Boolean
        get() = completedTotal == 0 && focusMinutes == 0 && notesWritten == 0 && habits.none { it.doneDays > 0 }
}

/**
 * Pure aggregation of planner data into [PeriodStatistics]. No clock, no calendar: the caller
 * passes [today] and calendar-correct buckets, which keeps it easy to test.
 */
object StatisticsCalculator {
    const val TOP_LIMIT = 5

    fun compute(input: StatsInput, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.SATURDAY): PeriodStatistics {
        val span = input.span
        val tasks = input.completedTasks.filter { it.completedAt.toLocalDate() in span }
        val focus = input.focus.filter { it.startedAt.toLocalDate() in span && it.minutes > 0 }

        val perBucket = input.buckets.map { b -> tasks.count { it.completedAt.toLocalDate() in b } }
        val focusPerBucket = input.buckets.map { b -> focus.filter { it.startedAt.toLocalDate() in b }.sumOf { it.minutes } }

        val countedUntil = minOf(span.end, today)
        val planned = if (countedUntil.isBefore(span.start)) {
            emptyList()
        } else {
            input.dueTasks.filter { it.dueDate in span && !it.dueDate.isAfter(countedUntil) }
        }
        val plannedDone = planned.count { it.completedOn != null }

        val withDue = tasks.filter { it.dueDate != null }
        val onTime = withDue.count { !it.completedAt.toLocalDate().isAfter(it.dueDate) }

        val perWeekday = MutableList(7) { 0 }
        val perHour = MutableList(24) { 0 }
        tasks.forEach {
            perWeekday[it.completedAt.dayOfWeek.value - 1]++
            perHour[it.completedAt.hour]++
        }

        val habitEnd = minOf(span.end, today)
        val habits = input.habits
            .filter { !it.habit.startDate.isAfter(habitEnd) && !habitEnd.isBefore(span.start) }
            .map { (habit, amounts) ->
                val inSpan = amounts.filterKeys { it in span }
                HabitSuccess(
                    habit = habit,
                    rate = HabitStats.completionRate(habit, amounts, span.start, habitEnd, today),
                    doneDays = inSpan.count { (_, amount) -> amount >= habit.target },
                    bestStreak = HabitStats.bestStreakDays(habit, inSpan, habitEnd, weekStart),
                )
            }
            .sortedByDescending { it.rate }

        val tagsById = input.tags.associateBy { it.id }
        val topTags = tasks.flatMap { it.tagIds }.groupingBy { it }.eachCount()
            .mapNotNull { (id, count) -> tagsById[id]?.let { RankedTag(it, count) } }
            .sortedWith(compareByDescending<RankedTag> { it.count }.thenBy { it.tag.name })
            .take(TOP_LIMIT)
        val projectsById = input.projects.associate { it.project.id to it.project }
        val topProjects = tasks.mapNotNull { it.projectId }.groupingBy { it }.eachCount()
            .mapNotNull { (id, count) -> projectsById[id]?.let { RankedProject(it, count) } }
            .sortedWith(compareByDescending<RankedProject> { it.count }.thenBy { it.project.title })
            .take(TOP_LIMIT)

        val activeDates = tasks.map { it.completedAt.toLocalDate() }.toSortedSet()

        return PeriodStatistics(
            span = span,
            buckets = input.buckets,
            completedPerBucket = perBucket,
            focusMinutesPerBucket = focusPerBucket,
            completedTotal = tasks.size,
            completionRate = if (planned.isEmpty()) null else plannedDone.toFloat() / planned.size,
            plannedTotal = planned.size,
            onTime = onTime,
            late = withDue.size - onTime,
            completedPerWeekday = perWeekday,
            busiestWeekday = maxIndex(perWeekday)?.let { DayOfWeek.of(it + 1) },
            completedPerHour = perHour,
            busiestHour = maxIndex(perHour),
            focusMinutes = focus.sumOf { it.minutes },
            focusSessions = focus.size,
            habitSuccessRate = if (habits.isEmpty()) null else habits.map { it.rate }.average().toFloat(),
            habits = habits,
            notesWritten = input.notesCreated.count { it in span },
            projects = input.projects.sortedByDescending { it.progress },
            topTags = topTags,
            topProjects = topProjects,
            bestBucket = maxIndex(perBucket),
            activeDays = activeDates.size,
            longestStreak = longestRun(activeDates),
        )
    }

    /** Index of the largest positive value (the first one on ties); null when all are zero. */
    internal fun maxIndex(values: List<Int>): Int? {
        var best: Int? = null
        values.forEachIndexed { i, v -> if (v > 0 && (best == null || v > values[best!!])) best = i }
        return best
    }

    /** Longest run of consecutive dates in an ascending set. */
    internal fun longestRun(dates: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in dates.sorted()) {
            run = if (previous != null && previous.plusDays(1) == date) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }
        return best
    }
}
