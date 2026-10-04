package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { TODO, IN_PROGRESS, DONE }

data class Tag(
    val id: EntityId = NEW_ID,
    val name: String,
    val color: AccentColor = AccentColor.LAVENDER,
)

data class Task(
    val id: EntityId = NEW_ID,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Priority = Priority.NONE,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val dueTime: LocalTime? = null,
    /** Minutes before the due moment; null = no reminder. */
    val reminderOffsetMinutes: Int? = null,
    val projectId: EntityId? = null,
    val parentTaskId: EntityId? = null,
    val recurrence: RecurrenceRule? = null,
    /** First date of the recurring series; occurrence counting starts here. */
    val recurrenceAnchor: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val notes: String = "",
    val sortOrder: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val completedAt: Instant? = null,
    val archived: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val subtaskCount: Int = 0,
    val completedSubtaskCount: Int = 0,
    /** Hard deadline, distinct from [dueDate] (the planned date). */
    val deadline: LocalDate? = null,
    /** Time-blocked or auto-scheduled slot. */
    val scheduledStart: Instant? = null,
    val scheduledEnd: Instant? = null,
    /** Repeat the reminder until the task is done. */
    val nag: Boolean = false,
    /** Set while the task is in the trash. */
    val deletedAt: Instant? = null,
) {
    val isCompleted: Boolean get() = status == TaskStatus.DONE
    val isRecurring: Boolean get() = recurrence != null

    fun isOverdue(today: LocalDate): Boolean = !isCompleted && dueDate != null && dueDate < today
}

/** Smart lists and filters supported by the task screen. */
enum class TaskView { INBOX, TODAY, UPCOMING, SCHEDULED, COMPLETED, ARCHIVED, ALL }

enum class TaskSort { MANUAL, DUE_DATE, PRIORITY, CREATED, TITLE }
