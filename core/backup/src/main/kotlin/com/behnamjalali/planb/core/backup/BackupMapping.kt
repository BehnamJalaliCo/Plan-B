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
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.database.entity.NoteLinkEntity
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import java.time.Instant
import java.util.Base64
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
    deadline?.toEpochDay(), scheduledStart?.toEpochMilli(), scheduledEnd?.toEpochMilli(), nag, deletedAt?.toEpochMilli(),
)
internal fun TaskDto.entity() = TaskEntity(
    id, title, description, status, completed, priority, startDate.date(), dueDate.date(), startTime.time(), dueTime.time(),
    reminderOffsetMinutes, projectId, parentTaskId, recurrence, recurrenceAnchor.date(), estimatedMinutes, actualMinutes, notes, sortOrder,
    createdAt.instant(), updatedAt.instant(), completedAt.instantOrNull(), archived,
    deadline.date(), scheduledStart.instantOrNull(), scheduledEnd.instantOrNull(), nag, deletedAt.instantOrNull(),
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
    deletedAt?.toEpochMilli(), locked, encryptedPayload?.let { Base64.getEncoder().encodeToString(it) },
)
internal fun NoteDto.entity() = NoteEntity(
    id, notebookId, sectionId, title, content, contentFormat, pinned, favorite, sortOrder, createdAt.instant(), updatedAt.instant(), archived,
    deletedAt.instantOrNull(), locked, encryptedPayload?.let { Base64.getDecoder().decode(it) },
)

internal fun HabitEntity.dto() = HabitDto(
    id, title, icon, color, schedule, target, unit, reminderTime?.toSecondOfDay(), startDate.toEpochDay(), createdAt.toEpochMilli(),
    updatedAt.toEpochMilli(), archived, healthMetric, healthThreshold,
)
internal fun HabitDto.entity() = HabitEntity(
    id, title, icon, color, schedule, target, unit, reminderTime.time(), LocalDate.ofEpochDay(startDate), createdAt.instant(), updatedAt.instant(), archived,
    healthMetric, healthThreshold,
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
    runningSince?.toEpochMilli(), accumulatedMillis, soundId, strict,
)
internal fun FocusDto.entity() = FocusSessionEntity(
    id, linkedTaskId, startedAt.instant(), endedAt.instantOrNull(), plannedDurationMillis, actualDurationMillis, status,
    runningSince.instantOrNull(), accumulatedMillis, soundId, strict,
)

internal fun PlannerTemplateEntity.dto() = TemplateDto(id, title, type, payload, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
internal fun TemplateDto.entity() = PlannerTemplateEntity(id, title, type, payload, false, createdAt.instant(), updatedAt.instant())

internal fun TaskTagCrossRef.dto() = RefDto(taskId, tagId)
internal fun ProjectTagCrossRef.dto() = RefDto(projectId, tagId)
internal fun NoteTagCrossRef.dto() = RefDto(noteId, tagId)

internal fun TaskDependencyEntity.dto() = RefDto(taskId, dependsOnTaskId)
internal fun RefDto.taskDependency() = TaskDependencyEntity(a, b)
internal fun NoteLinkEntity.dto() = RefDto(fromNoteId, toNoteId)
internal fun RefDto.noteLink() = NoteLinkEntity(a, b)

internal fun TaskReminderEntity.dto() = TaskReminderDto(id, taskId, kind, offsetMinutes, at?.toEpochMilli())
internal fun TaskReminderDto.entity() = TaskReminderEntity(id, taskId, kind, offsetMinutes, at.instantOrNull())

internal fun SavedFilterEntity.dto() = SavedFilterDto(id, name, icon, color, query, sortOrder, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
internal fun SavedFilterDto.entity() = SavedFilterEntity(id, name, icon, color, query, sortOrder, createdAt.instant(), updatedAt.instant())

internal fun NoteVersionEntity.dto() = NoteVersionDto(id, noteId, createdAt.toEpochMilli(), title, content, size)
internal fun NoteVersionDto.entity() = NoteVersionEntity(id, noteId, createdAt.instant(), title, content, size)

internal fun AttachmentEntity.dto() = AttachmentDto(
    id, ownerType, ownerId, kind, fileName, displayName, mimeType, sizeBytes, durationMillis, width, height, ocrText, transcript, sortOrder,
    createdAt.toEpochMilli(),
)
internal fun AttachmentDto.entity() = AttachmentEntity(
    id, ownerType, ownerId, kind, fileName, displayName, mimeType, sizeBytes, durationMillis, width, height, ocrText, transcript, sortOrder,
    createdAt.instant(),
)

internal fun JournalEntryEntity.dto() = JournalEntryDto(id, date.toEpochDay(), noteId, promptId, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
internal fun JournalEntryDto.entity() = JournalEntryEntity(id, LocalDate.ofEpochDay(date), noteId, promptId, createdAt.instant(), updatedAt.instant())

internal fun MoodEntryEntity.dto() = MoodEntryDto(
    id, date.toEpochDay(), time?.toSecondOfDay(), mood, energy, tags, noteId, createdAt.toEpochMilli(), updatedAt.toEpochMilli(),
)
internal fun MoodEntryDto.entity() = MoodEntryEntity(
    id, LocalDate.ofEpochDay(date), time.time(), mood, energy, tags, noteId, createdAt.instant(), updatedAt.instant(),
)

internal fun ChallengeEntity.dto() = ChallengeDto(
    id, kind, title, targetDays, startDate.toEpochDay(), habitId, status, completedAt?.toEpochMilli(), createdAt.toEpochMilli(), updatedAt.toEpochMilli(),
)
internal fun ChallengeDto.entity() = ChallengeEntity(
    id, kind, title, targetDays, LocalDate.ofEpochDay(startDate), habitId, status, completedAt.instantOrNull(), createdAt.instant(), updatedAt.instant(),
)

internal fun BadgeEntity.dto() = BadgeDto(id, key, earnedAt.toEpochMilli())
internal fun BadgeDto.entity() = BadgeEntity(id, key, earnedAt.instant())

internal fun ActivityLogEntity.dto() = ActivityDto(id, entityType, entityId, action, at.toEpochMilli(), summary)
internal fun ActivityDto.entity() = ActivityLogEntity(id, entityType, entityId, action, at.instant(), summary)

internal fun CalendarLinkEntity.dto() = CalendarLinkDto(
    id, localType, localId, calendarId, externalEventId, lastSyncedAt.toEpochMilli(), localVersion, remoteVersion,
)
internal fun CalendarLinkDto.entity() = CalendarLinkEntity(
    id, localType, localId, calendarId, externalEventId, lastSyncedAt.instant(), localVersion, remoteVersion,
)
