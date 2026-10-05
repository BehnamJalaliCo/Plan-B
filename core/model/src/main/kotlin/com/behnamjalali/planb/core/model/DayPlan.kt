package com.behnamjalali.planb.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * Plan-B Pro "smart day": automatic day planning (#5) and the morning/evening rituals (#8).
 * Pure rules here; storage lives in core:data, preferences in core:datastore.
 */

/**
 * Working hours and ritual preferences (user preferences, part of a backup).
 * Ritual reminder times are kept while the reminder is off, so switching it on again
 * restores the time the user chose.
 */
data class DayPlanSettings(
    val workStart: LocalTime = DEFAULT_WORK_START,
    val workEnd: LocalTime = DEFAULT_WORK_END,
    val lunchEnabled: Boolean = false,
    val lunchStart: LocalTime = DEFAULT_LUNCH_START,
    val lunchEnd: LocalTime = DEFAULT_LUNCH_END,
    val bufferMinutes: Int = DEFAULT_BUFFER,
    val morningReminder: Boolean = false,
    val morningTime: LocalTime = DEFAULT_MORNING,
    val eveningReminder: Boolean = false,
    val eveningTime: LocalTime = DEFAULT_EVENING,
) {
    /** Working hours that end before they start (or are empty) plan nothing. */
    val hasWorkingHours: Boolean get() = workEnd > workStart

    companion object {
        val DEFAULT_WORK_START: LocalTime = LocalTime.of(9, 0)
        val DEFAULT_WORK_END: LocalTime = LocalTime.of(18, 0)
        val DEFAULT_LUNCH_START: LocalTime = LocalTime.of(13, 0)
        val DEFAULT_LUNCH_END: LocalTime = LocalTime.of(14, 0)
        val DEFAULT_MORNING: LocalTime = LocalTime.of(8, 0)
        val DEFAULT_EVENING: LocalTime = LocalTime.of(21, 0)
        const val DEFAULT_BUFFER = 10
        val BUFFER_CHOICES: List<Int> = listOf(0, 5, 10, 15, 30)
    }
}

/**
 * Today's ritual state (user preferences): the "top 3" picked in the morning ritual for
 * [focusDate], and the days the rituals were last completed.
 */
data class RitualState(
    val focusDate: LocalDate? = null,
    val focusTaskIds: List<EntityId> = emptyList(),
    val morningDoneOn: LocalDate? = null,
    val eveningDoneOn: LocalDate? = null,
) {
    /** The top tasks picked for [date], or none when they were picked for another day. */
    fun focusFor(date: LocalDate): List<EntityId> = if (focusDate == date) focusTaskIds else emptyList()

    companion object {
        const val TOP_COUNT = 3
    }
}

/** A half-open time range [start, end) on the instant timeline. */
data class TimeRange(val start: Instant, val end: Instant) {
    val isEmpty: Boolean get() = !end.isAfter(start)
    val minutes: Long get() = Duration.between(start, end).toMinutes()

    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end
}

/** What the planner needs to know about a task. */
data class PlanTask(
    val id: EntityId,
    val priority: Priority = Priority.NONE,
    val dueDate: LocalDate? = null,
    val deadline: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    /** Waits for open tasks (Pro #14): never scheduled before its blockers are done. */
    val blocked: Boolean = false,
    val sortOrder: Long = 0,
) {
    companion object {
        fun of(task: Task): PlanTask = PlanTask(
            id = task.id,
            priority = task.priority,
            dueDate = task.dueDate,
            deadline = task.deadline,
            estimatedMinutes = task.estimatedMinutes,
            blocked = task.isBlocked,
            sortOrder = task.sortOrder,
        )
    }
}

/** A proposed (or existing) time block for a task. */
data class PlannedBlock(val taskId: EntityId, val start: Instant, val end: Instant) {
    val range: TimeRange get() = TimeRange(start, end)
}

enum class UnplannedReason {
    /** Waits for another open task. */
    BLOCKED,

    /** No free gap is long enough in the rest of the working day. */
    NO_FIT,
}

data class UnplannedTask(val taskId: EntityId, val reason: UnplannedReason)

data class DayPlan(val blocks: List<PlannedBlock>, val unplanned: List<UnplannedTask>)

/**
 * Input of [DayPlanner]. [busy] holds what already occupies the day: timed events, tasks
 * with a fixed time and tasks that already have a time block. All times are instants; the
 * working hours are read in [zone] on [date], so days with a daylight-saving change have
 * 23 or 25 hours and a working-hours boundary that falls into a skipped hour moves forward.
 */
data class DayPlanRequest(
    val date: LocalDate,
    val zone: ZoneId,
    val now: Instant,
    val settings: DayPlanSettings = DayPlanSettings(),
    val busy: List<TimeRange> = emptyList(),
    val tasks: List<PlanTask> = emptyList(),
)

/**
 * Automatic day planning (Plan-B Pro #5). Deterministic: the same request always gives the
 * same plan.
 *
 * Rules:
 * - The day is the working hours of [DayPlanRequest.date] (default 09:00–18:00); on the
 *   current day it starts at the next 5-minute mark after now. The optional lunch break is
 *   never used.
 * - Busy ranges get a buffer of [DayPlanSettings.bufferMinutes] (default 10) on both sides,
 *   and each placed block keeps the same buffer before the next one.
 * - Tasks are taken in this order: deadline today or passed, then deadline within
 *   [Deadlines.SOON_DAYS] days, then the rest; within each group higher priority first, then
 *   the older planned date (overdue first), the nearer deadline, the manual order and the id.
 * - Each task gets its estimate (default [DEFAULT_ESTIMATE] minutes, at least
 *   [MIN_BLOCK_MINUTES]) in the earliest free gap that holds it whole (first fit; tasks are
 *   never split). Blocks start on 5-minute marks.
 * - Blocked tasks (waiting for others) are never scheduled; tasks that fit nowhere are
 *   reported, not dropped silently.
 */
object DayPlanner {
    const val DEFAULT_ESTIMATE = 30
    const val MIN_BLOCK_MINUTES = 5
    private const val GRID_MINUTES = 5

    /** The working hours of the request's day as instants, or null when they are empty. */
    fun workingWindow(date: LocalDate, zone: ZoneId, settings: DayPlanSettings): TimeRange? {
        if (!settings.hasWorkingHours) return null
        val range = TimeRange(
            ZonedDateTime.of(date, settings.workStart, zone).toInstant(),
            ZonedDateTime.of(date, settings.workEnd, zone).toInstant(),
        )
        return range.takeUnless { it.isEmpty }
    }

    /** The free gaps of the working day (busy ranges and their buffers, lunch and the past removed). */
    fun freeTime(request: DayPlanRequest): List<TimeRange> {
        val window = workingWindow(request.date, request.zone, request.settings) ?: return emptyList()
        val start = maxOf(window.start, ceilToGrid(request.now, request.zone))
        if (!window.end.isAfter(start)) return emptyList()
        val buffer = Duration.ofMinutes(request.settings.bufferMinutes.coerceAtLeast(0).toLong())
        val blocked = buildList {
            request.busy.filterNot { it.isEmpty }.forEach { add(TimeRange(it.start.minus(buffer), it.end.plus(buffer))) }
            lunch(request)?.let(::add)
        }
        return subtract(TimeRange(start, window.end), blocked)
    }

    /** Tasks in planning order (see the class rules). */
    fun order(tasks: List<PlanTask>, date: LocalDate): List<PlanTask> = tasks.sortedWith(
        compareBy<PlanTask> { urgency(it, date) }
            .thenByDescending { it.priority.weight }
            .thenBy(nullsLast()) { it.dueDate }
            .thenBy(nullsLast()) { it.deadline }
            .thenBy { it.sortOrder }
            .thenBy { it.id },
    )

    fun plan(request: DayPlanRequest): DayPlan {
        val unplanned = mutableListOf<UnplannedTask>()
        val candidates = request.tasks.distinctBy { it.id }.filter { task ->
            if (task.blocked) unplanned += UnplannedTask(task.id, UnplannedReason.BLOCKED)
            !task.blocked
        }
        val placer = Placer(freeTime(request), request)
        val blocks = order(candidates, request.date).mapNotNull { task ->
            placer.place(task.id, durationOf(task)).also { if (it == null) unplanned += UnplannedTask(task.id, UnplannedReason.NO_FIT) }
        }
        return DayPlan(blocks.sortedBy { it.start }, unplanned)
    }

    /**
     * "Replan": moves the [existing] blocks of unfinished tasks that are already past (started
     * before now), lie on another day, or now overlap something busy (with its buffer) into the
     * free time that is left, keeping their order and length. Blocks that are still fine stay
     * where they are. [DayPlanRequest.busy] must not contain these blocks themselves;
     * [DayPlanRequest.tasks] is ignored. The result lists only the moved blocks.
     */
    fun replan(request: DayPlanRequest, existing: List<PlannedBlock>): DayPlan {
        val window = workingWindow(request.date, request.zone, request.settings)
        val start = ceilToGrid(request.now, request.zone)
        val buffer = Duration.ofMinutes(request.settings.bufferMinutes.coerceAtLeast(0).toLong())
        val busy = request.busy.filterNot { it.isEmpty }.map { TimeRange(it.start.minus(buffer), it.end.plus(buffer)) }
        val (keep, move) = existing.distinctBy { it.taskId }.partition { block ->
            window != null && block.start >= start && block.start >= window.start && block.end <= window.end &&
                busy.none { it.overlaps(block.range) }
        }
        val placer = Placer(freeTime(request.copy(busy = request.busy + keep.map { it.range })), request)
        val unplanned = mutableListOf<UnplannedTask>()
        val moved = move.sortedWith(compareBy<PlannedBlock> { it.start }.thenBy { it.taskId }).mapNotNull { block ->
            val minutes = Duration.between(block.start, block.end).toMinutes().toInt().coerceAtLeast(MIN_BLOCK_MINUTES)
            placer.place(block.taskId, minutes).also { if (it == null) unplanned += UnplannedTask(block.taskId, UnplannedReason.NO_FIT) }
        }
        return DayPlan(moved, unplanned)
    }

    /** Blocks that need [replan]: past ones (started before now) and ones overlapping busy time. */
    fun needsReplan(request: DayPlanRequest, existing: List<PlannedBlock>): Boolean = replanCandidates(request, existing).isNotEmpty()

    fun replanCandidates(request: DayPlanRequest, existing: List<PlannedBlock>): List<PlannedBlock> {
        val start = ceilToGrid(request.now, request.zone)
        val buffer = Duration.ofMinutes(request.settings.bufferMinutes.coerceAtLeast(0).toLong())
        val busy = request.busy.filterNot { it.isEmpty }.map { TimeRange(it.start.minus(buffer), it.end.plus(buffer)) }
        return existing.filter { block -> block.start < start || busy.any { it.overlaps(block.range) } }
    }

    private fun durationOf(task: PlanTask): Int = (task.estimatedMinutes ?: DEFAULT_ESTIMATE).coerceAtLeast(MIN_BLOCK_MINUTES)

    private fun urgency(task: PlanTask, date: LocalDate): Int {
        val deadline = task.deadline ?: return 2
        return when {
            deadline <= date -> 0
            deadline <= date.plusDays(Deadlines.SOON_DAYS.toLong()) -> 1
            else -> 2
        }
    }

    private fun lunch(request: DayPlanRequest): TimeRange? {
        val s = request.settings
        if (!s.lunchEnabled || s.lunchEnd <= s.lunchStart) return null
        return TimeRange(
            ZonedDateTime.of(request.date, s.lunchStart, request.zone).toInstant(),
            ZonedDateTime.of(request.date, s.lunchEnd, request.zone).toInstant(),
        )
    }

    /** The next 5-minute mark of the wall clock in [zone] at or after [instant]. */
    fun ceilToGrid(instant: Instant, zone: ZoneId): Instant {
        val zoned = instant.atZone(zone)
        val truncated = zoned.truncatedTo(ChronoUnit.MINUTES)
        val rest = Math.floorMod(truncated.minute, GRID_MINUTES)
        if (rest == 0 && truncated.toInstant() == instant) return instant
        return truncated.plusMinutes((GRID_MINUTES - rest).toLong()).toInstant()
    }

    /** [range] minus every range in [blocked], as sorted, non-empty gaps. */
    internal fun subtract(range: TimeRange, blocked: List<TimeRange>): List<TimeRange> {
        var gaps = listOf(range)
        blocked.sortedBy { it.start }.forEach { cut ->
            gaps = gaps.flatMap { gap ->
                if (!gap.overlaps(cut)) {
                    listOf(gap)
                } else {
                    listOf(TimeRange(gap.start, cut.start), TimeRange(cut.end, gap.end)).filterNot { it.isEmpty }
                }
            }
        }
        return gaps
    }

    /** First-fit placement into free gaps; a placed block keeps a buffer before the next one. */
    private class Placer(free: List<TimeRange>, private val request: DayPlanRequest) {
        private val gaps = free.toMutableList()
        private val buffer = Duration.ofMinutes(request.settings.bufferMinutes.coerceAtLeast(0).toLong())

        fun place(taskId: EntityId, minutes: Int): PlannedBlock? {
            val length = Duration.ofMinutes(minutes.toLong())
            for (i in gaps.indices) {
                val gap = gaps[i]
                val start = ceilToGrid(gap.start, request.zone)
                val end = start.plus(length)
                if (end <= gap.end) {
                    val rest = TimeRange(end.plus(buffer), gap.end)
                    if (rest.isEmpty) gaps.removeAt(i) else gaps[i] = rest
                    return PlannedBlock(taskId, start, end)
                }
            }
            return null
        }
    }
}
