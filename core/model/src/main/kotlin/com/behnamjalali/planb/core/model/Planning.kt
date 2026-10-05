package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Plan-B Pro planning (#11–#14): extra reminders and nagging, deadlines, the Eisenhower matrix
 * and task dependencies. Pure rules here; storage lives in core:data.
 */

/**
 * Kind of an extra reminder (`task_reminders.kind`).
 * - [OFFSET]: [TaskReminder.offsetMinutes] before the planned due moment (like the primary one).
 * - [ABSOLUTE]: at the fixed instant [TaskReminder.at].
 * - [DEADLINE]: [TaskReminder.offsetMinutes] before the deadline day (at the date-only default
 *   time, 09:00).
 */
enum class TaskReminderKind { OFFSET, ABSOLUTE, DEADLINE }

/** One of the up to four extra reminders of a task (the primary one is [Task.reminderOffsetMinutes]). */
data class TaskReminder(
    val id: EntityId = NEW_ID,
    val kind: TaskReminderKind,
    val offsetMinutes: Int? = null,
    val at: Instant? = null,
) {
    /** Relative reminders follow the task to its next occurrence or its duplicate. */
    val isRelative: Boolean get() = kind != TaskReminderKind.ABSOLUTE
}

object TaskReminderRules {
    /** Extra reminders besides the primary one: at most five reminders per task. */
    const val MAX_EXTRA = 4

    /** "Nag until done": repeat every N minutes (offered choices and the default). */
    val NAG_INTERVALS: List<Int> = listOf(5, 10, 15, 30)
    const val DEFAULT_NAG_INTERVAL = 10

    /** A nagging reminder repeats at most this many times after each reminder. */
    const val NAG_REPEATS = 12

    /** "Snooze" from a reminder notification postpones it by this many minutes. */
    const val SNOOZE_MINUTES = 10

    fun normalizeNagInterval(minutes: Int?): Int = minutes?.takeIf { it in NAG_INTERVALS } ?: DEFAULT_NAG_INTERVAL
}

/** Extra planning data of one task: its extra reminders, nag interval and the tasks it waits for. */
data class TaskPlanning(
    val reminders: List<TaskReminder> = emptyList(),
    val nagIntervalMinutes: Int = TaskReminderRules.DEFAULT_NAG_INTERVAL,
    val blockedBy: List<EntityId> = emptyList(),
)

/** How close a deadline is, for badges and colors (Pro #11). */
enum class DeadlineUrgency { OVERDUE, TODAY, SOON, LATER }

object Deadlines {
    /** Today shows tasks whose deadline is this many days away or less, even when planned later. */
    const val TODAY_WINDOW_DAYS = 3

    /** "Soon" (warning color) up to this many days ahead. */
    const val SOON_DAYS = 2

    fun daysLeft(deadline: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, deadline)

    fun urgency(deadline: LocalDate, today: LocalDate): DeadlineUrgency {
        val days = daysLeft(deadline, today)
        return when {
            days < 0 -> DeadlineUrgency.OVERDUE
            days == 0L -> DeadlineUrgency.TODAY
            days <= SOON_DAYS -> DeadlineUrgency.SOON
            else -> DeadlineUrgency.LATER
        }
    }
}

/**
 * The Eisenhower matrix (Pro #13). Decision: **important** means priority HIGH or MEDIUM;
 * **urgent** means overdue, or the deadline or the planned date is at most
 * [EisenhowerMatrix.DEFAULT_THRESHOLD] days away (the view lets the user pick 1–7).
 */
enum class EisenhowerQuadrant(val important: Boolean, val urgent: Boolean) {
    DO(important = true, urgent = true),
    SCHEDULE(important = true, urgent = false),
    DELEGATE(important = false, urgent = true),
    ELIMINATE(important = false, urgent = false),
    ;

    companion object {
        fun of(important: Boolean, urgent: Boolean): EisenhowerQuadrant = entries.first { it.important == important && it.urgent == urgent }
    }
}

/** The result of moving a task to another quadrant. [keptUrgentByDeadline]: the deadline still makes it urgent. */
data class EisenhowerMove(val task: Task, val keptUrgentByDeadline: Boolean = false)

object EisenhowerMatrix {
    const val DEFAULT_THRESHOLD = 2
    val THRESHOLDS: IntRange = 1..7

    fun isImportant(task: Task): Boolean = task.priority == Priority.HIGH || task.priority == Priority.MEDIUM

    fun isUrgent(task: Task, today: LocalDate, thresholdDays: Int): Boolean {
        val limit = today.plusDays(thresholdDays.toLong())
        return task.isOverdue(today) || listOfNotNull(task.deadline, task.dueDate).any { it <= limit }
    }

    fun quadrantOf(task: Task, today: LocalDate, thresholdDays: Int): EisenhowerQuadrant =
        EisenhowerQuadrant.of(isImportant(task), isUrgent(task, today, thresholdDays))

    /**
     * Changes priority and planned date so the task lands in [target]:
     * - becomes important: priority HIGH; stops being important: priority LOW;
     * - becomes urgent: planned for today; stops being urgent: planned for the first day after
     *   the threshold. The deadline is never moved; when it alone keeps the task urgent, the
     *   result says so ([EisenhowerMove.keptUrgentByDeadline]).
     */
    fun move(task: Task, target: EisenhowerQuadrant, today: LocalDate, thresholdDays: Int): EisenhowerMove {
        var result = task
        if (target.important != isImportant(task)) {
            result = result.copy(priority = if (target.important) Priority.HIGH else Priority.LOW)
        }
        var keptByDeadline = false
        if (target.urgent != isUrgent(task, today, thresholdDays)) {
            if (target.urgent) {
                result = result.copy(dueDate = today)
            } else {
                result = result.copy(dueDate = today.plusDays(thresholdDays + 1L))
                keptByDeadline = isUrgent(result, today, thresholdDays)
            }
        }
        // A task that can't start after its new planned date keeps a consistent start.
        if (result.startDate != null && result.dueDate != null && result.startDate > result.dueDate) {
            result = result.copy(startDate = result.dueDate)
        }
        return EisenhowerMove(result, keptByDeadline)
    }
}

/** Task dependencies (Pro #14): "task waits for blocker". */
object TaskDependencies {
    /**
     * Whether adding "[taskId] waits for [blockerId]" would close a cycle, given the existing
     * [edges] (task id → ids it waits for). A depth-first search from [blockerId] along its own
     * blockers looks for [taskId]; it tolerates cycles already in the data (e.g. a restored
     * backup) and never loops.
     */
    fun wouldCreateCycle(edges: Map<EntityId, Collection<EntityId>>, taskId: EntityId, blockerId: EntityId): Boolean {
        if (taskId == blockerId) return true
        val visited = HashSet<EntityId>()
        val stack = ArrayDeque<EntityId>()
        stack.addLast(blockerId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == taskId) return true
            if (!visited.add(current)) continue
            edges[current]?.forEach { if (it !in visited) stack.addLast(it) }
        }
        return false
    }
}
