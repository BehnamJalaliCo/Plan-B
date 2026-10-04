package com.behnamjalali.planb.core.backup

import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.database.entity.FocusSessionEntity
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.GoalMilestoneEntity
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectMilestoneEntity
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private fun Long?.date() = this?.let(LocalDate::ofEpochDay)
private fun Int?.time() = this?.let { LocalTime.ofSecondOfDay(it.toLong().coerceIn(0, 86_399)) }
private fun Long.instant() = Instant.ofEpochMilli(this)
private fun Long?.instantOrNull() = this?.let(Instant::ofEpochMilli)

internal fun TagEntity.dto() = TagDto(id, name, color)
internal fun TagDto.entity() = TagEntity(id, name, color)

internal fun TaskEntity.dto() = TaskDto(
    id, title, description, status, completed, priority, startDate?.toEpochDay(), dueDate?.toEpochDay(), startTime?.toSecondOfDay(),
    dueTime?.toSecondOfDay(), reminderOffsetMinutes, projectId, parentTaskId, recurrence, recurrenceAnchor?.toEpochDay(),
    estimatedMinutes, actualMinutes, notes, sortOrder, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), completedAt?.toEpochMilli(), archived,
)
internal fun TaskDto.entity() = TaskEntity(
    id, title, description, status, completed, priority, startDate.date(), dueDate.date(), startTime.time(), dueTime.time(),
    reminderOffsetMinutes, projectId, parentTaskId, recurrence, recurrenceAnchor.date(), estimatedMinutes, actualMinutes, notes, sortOrder,
    createdAt.instant(), updatedAt.instant(), completedAt.instantOrNull(), archived,
)

internal fun ProjectEntity.dto() = ProjectDto(
    id, title, description, color, icon, status, progressMode, manualProgress, startDate?.toEpochDay(), dueDate?.toEpochDay(), sortOrder,
    createdAt.toEpochMilli(), updatedAt.toEpochMilli(), archived,
)
internal fun ProjectDto.entity() = ProjectEntity(
    id, title, description, color, icon, status, progressMode, manualProgress, startDate.date(), dueDate.date(), sortOrder,
    createdAt.instant(), updatedAt.instant(), archived,
)

internal fun ProjectMilestoneEntity.dto() = MilestoneDto(id, projectId, title, date?.toEpochDay(), null, completed, sortOrder)
internal fun MilestoneDto.projectMilestone() = ProjectMilestoneEntity(id, parentId, title, date.date(), completed, sortOrder)
internal fun GoalMilestoneEntity.dto() = MilestoneDto(id, goalId, title, null, target, completed, sortOrder)
internal fun MilestoneDto.goalMilestone() = GoalMilestoneEntity(id, parentId, title, target, completed, sortOrder)

internal fun NotebookEntity.dto() = NotebookDto(id, title, icon, color, sortOrder, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), archived)
internal fun NotebookDto.entity() = NotebookEntity(id, title, icon, color, sortOrder, createdAt.instant(), updatedAt.instant(), archived)
internal fun NotebookSectionEntity.dto() = SectionDto(id, notebookId, title, sortOrder)
internal fun SectionDto.entity() = NotebookSectionEntity(id, notebookId, title, sortOrder)
internal fun NoteEntity.dto() = NoteDto(
    id, notebookId, sectionId, title, content, contentFormat, pinned, favorite, sortOrder, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), archived,
)
internal fun NoteDto.entity() = NoteEntity(
    id, notebookId, sectionId, title, content, contentFormat, pinned, favorite, sortOrder, createdAt.instant(), updatedAt.instant(), archived,
)

internal fun HabitEntity.dto() = HabitDto(
    id, title, icon, color, schedule, target, unit, reminderTime?.toSecondOfDay(), startDate.toEpochDay(), createdAt.toEpochMilli(),
    updatedAt.toEpochMilli(), archived,
)
internal fun HabitDto.entity() = HabitEntity(
    id, title, icon, color, schedule, target, unit, reminderTime.time(), LocalDate.ofEpochDay(startDate), createdAt.instant(), updatedAt.instant(), archived,
)
internal fun HabitCompletionEntity.dto() = HabitCompletionDto(id, habitId, date.toEpochDay(), amount, createdAt.toEpochMilli())
internal fun HabitCompletionDto.entity() = HabitCompletionEntity(id, habitId, LocalDate.ofEpochDay(date), amount, createdAt.instant())

internal fun GoalEntity.dto() = GoalDto(
    id, title, description, target, currentValue, unit, deadline?.toEpochDay(), projectId, notes, createdAt.toEpochMilli(), updatedAt.toEpochMilli(), archived,
)
internal fun GoalDto.entity() = GoalEntity(
    id, title, description, target, currentValue, unit, deadline.date(), projectId, notes, createdAt.instant(), updatedAt.instant(), archived,
)

internal fun CalendarEventEntity.dto() = EventDto(
    id, title, description, date.toEpochDay(), startTime?.toSecondOfDay(), endTime?.toSecondOfDay(), allDay, reminderOffsetMinutes, recurrence,
    color, notes, createdAt.toEpochMilli(), updatedAt.toEpochMilli(),
)
internal fun EventDto.entity() = CalendarEventEntity(
    id, title, description, LocalDate.ofEpochDay(date), startTime.time(), endTime.time(), allDay, reminderOffsetMinutes, recurrence, color, notes,
    createdAt.instant(), updatedAt.instant(),
)

internal fun FocusSessionEntity.dto() = FocusDto(
    id, linkedTaskId, startedAt.toEpochMilli(), endedAt?.toEpochMilli(), plannedDurationMillis, actualDurationMillis, status,
    runningSince?.toEpochMilli(), accumulatedMillis,
)
internal fun FocusDto.entity() = FocusSessionEntity(
    id, linkedTaskId, startedAt.instant(), endedAt.instantOrNull(), plannedDurationMillis, actualDurationMillis, status,
    runningSince.instantOrNull(), accumulatedMillis,
)

internal fun PlannerTemplateEntity.dto() = TemplateDto(id, title, type, payload, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
internal fun TemplateDto.entity() = PlannerTemplateEntity(id, title, type, payload, false, createdAt.instant(), updatedAt.instant())

internal fun TaskTagCrossRef.dto() = RefDto(taskId, tagId)
internal fun ProjectTagCrossRef.dto() = RefDto(projectId, tagId)
internal fun NoteTagCrossRef.dto() = RefDto(noteId, tagId)
