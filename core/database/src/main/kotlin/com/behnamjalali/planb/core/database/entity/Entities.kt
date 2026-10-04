package com.behnamjalali.planb.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("project_id"),
        Index("parent_task_id"),
        Index("completed", "archived", "due_date"),
        Index("completed_at"),
        Index("deadline"),
        Index("scheduled_start"),
        Index("deleted_at"),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: String,
    val completed: Boolean,
    val priority: Int,
    @ColumnInfo(name = "start_date") val startDate: LocalDate?,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    @ColumnInfo(name = "due_time") val dueTime: LocalTime?,
    @ColumnInfo(name = "reminder_offset_minutes") val reminderOffsetMinutes: Int?,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    @ColumnInfo(name = "parent_task_id") val parentTaskId: Long?,
    val recurrence: String?,
    @ColumnInfo(name = "recurrence_anchor") val recurrenceAnchor: LocalDate?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int?,
    val notes: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    val archived: Boolean,
    /** Hard deadline (epoch day), distinct from [dueDate], which is the planned date (v3). */
    val deadline: LocalDate? = null,
    /** Time-blocked or auto-scheduled slot (epoch ms, v3). */
    @ColumnInfo(name = "scheduled_start") val scheduledStart: Instant? = null,
    @ColumnInfo(name = "scheduled_end") val scheduledEnd: Instant? = null,
    /** Repeat the reminder until the task is done (v3). */
    @ColumnInfo(defaultValue = "0") val nag: Boolean = false,
    /** Set when the task is in the trash; every regular list excludes such rows (v3). */
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: String,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["task_id", "tag_id"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class TaskTagCrossRef(
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(tableName = "projects", indices = [Index("status", "archived")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val color: String,
    val icon: String,
    val status: String,
    @ColumnInfo(name = "progress_mode") val progressMode: String,
    @ColumnInfo(name = "manual_progress") val manualProgress: Float,
    @ColumnInfo(name = "start_date") val startDate: LocalDate?,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val archived: Boolean,
)

@Entity(
    tableName = "project_tags",
    primaryKeys = ["project_id", "tag_id"],
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class ProjectTagCrossRef(
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "project_milestones",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("project_id")],
)
data class ProjectMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    val title: String,
    val date: LocalDate?,
    val completed: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
)

@Entity(tableName = "notebooks", indices = [Index("archived", "sort_order")])
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val color: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val archived: Boolean,
)

@Entity(
    tableName = "notebook_sections",
    foreignKeys = [ForeignKey(NotebookEntity::class, ["id"], ["notebook_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("notebook_id")],
)
data class NotebookSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "notebook_id") val notebookId: Long,
    val title: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(NotebookEntity::class, ["id"], ["notebook_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NotebookSectionEntity::class, ["id"], ["section_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("notebook_id", "archived"), Index("section_id"), Index("updated_at"), Index("deleted_at")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "notebook_id") val notebookId: Long,
    @ColumnInfo(name = "section_id") val sectionId: Long?,
    val title: String,
    val content: String,
    @ColumnInfo(name = "content_format") val contentFormat: String,
    val pinned: Boolean,
    val favorite: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val archived: Boolean,
    /** Set when the note is in the trash; every regular list excludes such rows (v3). */
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
    /** Requires unlocking (biometrics) before it is shown (v3). */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    /**
     * Encrypted note body (v3). While it is set, [content] holds no readable text and
     * [title] is the only indexed text. See DATABASE.md for the envelope format.
     */
    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB) val encryptedPayload: ByteArray? = null,
) {
    // Value semantics for the payload (a plain data class compares arrays by identity).
    override fun equals(other: Any?): Boolean = other is NoteEntity && id == other.id && notebookId == other.notebookId &&
        sectionId == other.sectionId && title == other.title && content == other.content && contentFormat == other.contentFormat &&
        pinned == other.pinned && favorite == other.favorite && sortOrder == other.sortOrder && createdAt == other.createdAt &&
        updatedAt == other.updatedAt && archived == other.archived && deletedAt == other.deletedAt && locked == other.locked &&
        encryptedPayload.contentEquals(other.encryptedPayload)

    override fun hashCode(): Int = 31 * id.hashCode() + title.hashCode() + content.hashCode() + encryptedPayload.contentHashCode()
}

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(tableName = "habits", indices = [Index("archived")])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val color: String,
    val schedule: String,
    val target: Int,
    val unit: String,
    @ColumnInfo(name = "reminder_time") val reminderTime: LocalTime?,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val archived: Boolean,
    /** Health Connect metric that checks the habit off automatically, e.g. STEPS (v3). */
    @ColumnInfo(name = "health_metric") val healthMetric: String? = null,
    /** Daily amount of [healthMetric] that counts as done (v3). */
    @ColumnInfo(name = "health_threshold") val healthThreshold: Long? = null,
)

/** One row per habit per day; [amount] accumulates check-ins for that day. */
@Entity(
    tableName = "habit_completions",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habit_id", "date"], unique = true), Index("date")],
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val date: LocalDate,
    val amount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "goals",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["project_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("project_id"), Index("archived")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val target: Double,
    @ColumnInfo(name = "current_value") val currentValue: Double,
    val unit: String,
    val deadline: LocalDate?,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val archived: Boolean,
)

@Entity(
    tableName = "goal_milestones",
    foreignKeys = [ForeignKey(GoalEntity::class, ["id"], ["goal_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goal_id")],
)
data class GoalMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val title: String,
    val target: Double?,
    val completed: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
)

@Entity(tableName = "calendar_events", indices = [Index("date"), Index("recurrence")])
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    @ColumnInfo(name = "end_time") val endTime: LocalTime?,
    @ColumnInfo(name = "all_day") val allDay: Boolean,
    @ColumnInfo(name = "reminder_offset_minutes") val reminderOffsetMinutes: Int?,
    val recurrence: String?,
    val color: String,
    val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["linked_task_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("linked_task_id"), Index("started_at"), Index("status")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "linked_task_id") val linkedTaskId: Long?,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
    @ColumnInfo(name = "planned_duration_ms") val plannedDurationMillis: Long,
    @ColumnInfo(name = "actual_duration_ms") val actualDurationMillis: Long,
    val status: String,
    @ColumnInfo(name = "running_since") val runningSince: Instant?,
    @ColumnInfo(name = "accumulated_ms") val accumulatedMillis: Long,
    /** Ambient sound played during the session (v3). */
    @ColumnInfo(name = "sound_id") val soundId: String? = null,
    /** Strict mode: Do Not Disturb while running (v3). */
    @ColumnInfo(defaultValue = "0") val strict: Boolean = false,
)

@Entity(tableName = "planner_templates")
data class PlannerTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val payload: String,
    @ColumnInfo(name = "built_in") val builtIn: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * Full-text index over normalized text (see SearchNormalizer). The rowid encodes
 * the entity type and id (`id * 16 + type`) so updates and deletes are O(1).
 */
@Fts4(notIndexed = ["entity_type", "entity_id"])
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    @ColumnInfo(name = "entity_type") val entityType: Int,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    val content: String,
)

/**
 * Unsaved editor state for crash/draft recovery (added in schema v2).
 * A row exists only while the editor holds changes not yet committed to [NoteEntity].
 */
@Entity(
    tableName = "note_drafts",
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["note_id"], onDelete = ForeignKey.CASCADE)],
)
data class NoteDraftEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: Long,
    val title: String,
    val content: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
