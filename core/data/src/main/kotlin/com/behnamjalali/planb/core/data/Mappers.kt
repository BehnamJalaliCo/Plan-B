package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.database.entity.FocusSessionEntity
import com.behnamjalali.planb.core.database.entity.GoalEntity
import com.behnamjalali.planb.core.database.entity.GoalMilestoneEntity
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectMilestoneEntity
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.model.NoteWithTags
import com.behnamjalali.planb.core.database.model.NotebookWithCount
import com.behnamjalali.planb.core.database.model.ProjectWithCounts
import com.behnamjalali.planb.core.database.model.TaskWithDetails
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.GoalMilestone
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitCompletion
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteFormat
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.NotebookSection
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.ProgressMode
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType

internal inline fun <reified E : Enum<E>> enumOf(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

internal fun TagEntity.toModel() = Tag(id, name, AccentColor.fromKey(color))
internal fun Tag.toEntity() = TagEntity(id, name.trim(), color.key)

internal fun TaskEntity.toModel(
    tags: List<Tag> = emptyList(),
    subtaskCount: Int = 0,
    completedSubtaskCount: Int = 0,
) = Task(
    id = id,
    title = title,
    description = description,
    status = enumOf(status, if (completed) TaskStatus.DONE else TaskStatus.TODO),
    priority = Priority.fromWeight(priority),
    startDate = startDate,
    dueDate = dueDate,
    startTime = startTime,
    dueTime = dueTime,
    reminderOffsetMinutes = reminderOffsetMinutes,
    projectId = projectId,
    parentTaskId = parentTaskId,
    recurrence = RecurrenceRule.decode(recurrence),
    recurrenceAnchor = recurrenceAnchor,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
    notes = notes,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    archived = archived,
    tags = tags,
    subtaskCount = subtaskCount,
    completedSubtaskCount = completedSubtaskCount,
    deadline = deadline,
    scheduledStart = scheduledStart,
    scheduledEnd = scheduledEnd,
    nag = nag,
    deletedAt = deletedAt,
)

internal fun TaskWithDetails.toModel() =
    task.toModel(tags.map { it.toModel() }, subtaskCount, completedSubtaskCount)

internal fun Task.toEntity() = TaskEntity(
    id = id,
    title = title.trim(),
    description = description,
    status = status.name,
    completed = status == TaskStatus.DONE,
    priority = priority.weight,
    startDate = startDate,
    dueDate = dueDate,
    startTime = startTime,
    dueTime = dueTime,
    reminderOffsetMinutes = reminderOffsetMinutes,
    projectId = projectId,
    parentTaskId = parentTaskId,
    recurrence = recurrence?.encode(),
    recurrenceAnchor = recurrenceAnchor,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
    notes = notes,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    archived = archived,
    deadline = deadline,
    scheduledStart = scheduledStart,
    scheduledEnd = scheduledEnd,
    nag = nag,
    deletedAt = deletedAt,
)

internal fun ProjectEntity.toModel(tags: List<Tag> = emptyList()) = Project(
    id = id,
    title = title,
    description = description,
    color = AccentColor.fromKey(color),
    icon = PlannerIcon.fromKey(icon),
    status = enumOf(status, ProjectStatus.ACTIVE),
    progressMode = enumOf(progressMode, ProgressMode.TASKS),
    manualProgress = manualProgress,
    startDate = startDate,
    dueDate = dueDate,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    tags = tags,
)

internal fun Project.toEntity() = ProjectEntity(
    id = id,
    title = title.trim(),
    description = description,
    color = color.key,
    icon = icon.key,
    status = status.name,
    progressMode = progressMode.name,
    manualProgress = manualProgress.coerceIn(0f, 1f),
    startDate = startDate,
    dueDate = dueDate,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
)

internal fun ProjectWithCounts.toModel() = ProjectSummary(
    project = project.toModel(tags.map { it.toModel() }),
    totalTasks = totalTasks,
    completedTasks = completedTasks,
    totalMilestones = totalMilestones,
    completedMilestones = completedMilestones,
)

internal fun ProjectMilestoneEntity.toModel() = ProjectMilestone(id, projectId, title, date, completed, sortOrder)
internal fun ProjectMilestone.toEntity() = ProjectMilestoneEntity(id, projectId, title.trim(), date, completed, sortOrder)

internal fun NotebookEntity.toModel(noteCount: Int = 0) = Notebook(
    id, title, PlannerIcon.fromKey(icon), AccentColor.fromKey(color), sortOrder, createdAt, updatedAt, archived, noteCount,
)
internal fun NotebookWithCount.toModel() = notebook.toModel(noteCount)
internal fun Notebook.toEntity() = NotebookEntity(id, title.trim(), icon.key, color.key, sortOrder, createdAt, updatedAt, archived)

internal fun NotebookSectionEntity.toModel() = NotebookSection(id, notebookId, title, sortOrder)
internal fun NotebookSection.toEntity() = NotebookSectionEntity(id, notebookId, title.trim(), sortOrder)

internal fun NoteEntity.toModel(tags: List<Tag> = emptyList()) = Note(
    id = id,
    notebookId = notebookId,
    sectionId = sectionId,
    title = title,
    document = NoteDocument.decode(content),
    pinned = pinned,
    favorite = favorite,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    tags = tags,
    deletedAt = deletedAt,
    locked = locked,
)
internal fun NoteWithTags.toModel() = note.toModel(tags.map { it.toModel() })
/** The encrypted body is not part of the model: repositories keep the stored one ([encryptedPayload]). */
internal fun Note.toEntity(encryptedPayload: ByteArray? = null) = NoteEntity(
    id = id,
    notebookId = notebookId,
    sectionId = sectionId,
    title = title,
    content = document.encode(),
    contentFormat = NoteFormat.BLOCKS_V1.key,
    pinned = pinned,
    favorite = favorite,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    deletedAt = deletedAt,
    locked = locked,
    encryptedPayload = encryptedPayload,
)

internal fun HabitEntity.toModel() = Habit(
    id = id,
    title = title,
    icon = PlannerIcon.fromKey(icon),
    color = AccentColor.fromKey(color),
    schedule = HabitSchedule.decode(schedule),
    target = target,
    unit = unit,
    reminderTime = reminderTime,
    startDate = startDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    healthMetric = HealthMetric.fromKey(healthMetric),
    healthThreshold = healthThreshold,
)
internal fun Habit.toEntity() = HabitEntity(
    id, title.trim(), icon.key, color.key, schedule.encode(), target.coerceAtLeast(1), unit.trim(),
    reminderTime, startDate, createdAt, updatedAt, archived, healthMetric?.name, healthThreshold,
)
internal fun HabitCompletionEntity.toModel() = HabitCompletion(id, habitId, date, amount, createdAt)

internal fun GoalEntity.toModel() = Goal(
    id, title, description, target, currentValue, unit, deadline, projectId, notes, createdAt, updatedAt, archived,
)
internal fun Goal.toEntity() = GoalEntity(
    id, title.trim(), description, target, currentValue, unit.trim(), deadline, projectId, notes, createdAt, updatedAt, archived,
)
internal fun GoalMilestoneEntity.toModel() = GoalMilestone(id, goalId, title, target, completed, sortOrder)
internal fun GoalMilestone.toEntity() = GoalMilestoneEntity(id, goalId, title.trim(), target, completed, sortOrder)

internal fun CalendarEventEntity.toModel() = CalendarEvent(
    id = id,
    title = title,
    description = description,
    date = date,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    reminderOffsetMinutes = reminderOffsetMinutes,
    recurrence = RecurrenceRule.decode(recurrence),
    color = AccentColor.fromKey(color),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
internal fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    title = title.trim(),
    description = description,
    date = date,
    startTime = if (allDay) null else startTime,
    endTime = if (allDay) null else endTime,
    allDay = allDay || startTime == null,
    reminderOffsetMinutes = reminderOffsetMinutes,
    recurrence = recurrence?.encode(),
    color = color.key,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun FocusSessionEntity.toModel() = FocusSession(
    id = id,
    linkedTaskId = linkedTaskId,
    startedAt = startedAt,
    endedAt = endedAt,
    plannedDurationMillis = plannedDurationMillis,
    actualDurationMillis = actualDurationMillis,
    status = enumOf(status, FocusStatus.CANCELLED),
    runningSince = runningSince,
    accumulatedMillis = accumulatedMillis,
    soundId = soundId,
    strict = strict,
)
internal fun FocusSession.toEntity() = FocusSessionEntity(
    id, linkedTaskId, startedAt, endedAt, plannedDurationMillis, actualDurationMillis, status.name, runningSince, accumulatedMillis,
    soundId, strict,
)

internal fun PlannerTemplateEntity.toModel(): PlannerTemplate? = runCatching {
    PlannerTemplate(
        id = id,
        builtInKey = null,
        title = title,
        type = enumOf(type, TemplateType.NOTE),
        payload = TemplatePayload.decode(payload),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}.getOrNull()
