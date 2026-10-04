package com.behnamjalali.planb.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/*
 * Tables added in schema v3 for the Plan-B Pro work packages. They are created empty by
 * MIGRATION_2_3 and documented in DATABASE.md. Enum-like TEXT columns store the names of the
 * constants listed in the KDoc; readers must tolerate unknown values.
 */

/**
 * Additional reminders of a task (up to four besides `tasks.reminder_offset_minutes`, which
 * stays the primary reminder, so a task has at most five).
 *
 * [kind]: `OFFSET` (minutes before the due date/time in [offsetMinutes]) or `ABSOLUTE`
 * (a fixed instant in [at], epoch ms).
 */
@Entity(
    tableName = "task_reminders",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("task_id")],
)
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val kind: String,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int? = null,
    val at: Instant? = null,
)

/** [taskId] cannot start before [dependsOnTaskId] is done. A task never depends on itself (checked in code). */
@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["task_id", "depends_on_task_id"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["depends_on_task_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("depends_on_task_id")],
)
data class TaskDependencyEntity(
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "depends_on_task_id") val dependsOnTaskId: Long,
)

/** A custom smart list. [query] is a versioned JSON filter document written by the smart-lists feature. */
@Entity(tableName = "saved_filters", indices = [Index("sort_order")])
data class SavedFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val query: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** A snapshot of a note taken before it changed (note version history). */
@Entity(
    tableName = "note_versions",
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("note_id", "created_at")],
)
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val title: String,
    /** The JSON NoteDocument as it was. */
    val content: String,
    /** UTF-8 size of [content] in bytes, for pruning. */
    val size: Long,
)

/** A link written in note [fromNoteId] that points at note [toNoteId] (backlinks, graph view). */
@Entity(
    tableName = "note_links",
    primaryKeys = ["from_note_id", "to_note_id"],
    foreignKeys = [
        ForeignKey(NoteEntity::class, ["id"], ["from_note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NoteEntity::class, ["id"], ["to_note_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("to_note_id")],
)
data class NoteLinkEntity(
    @ColumnInfo(name = "from_note_id") val fromNoteId: Long,
    @ColumnInfo(name = "to_note_id") val toNoteId: Long,
)

/**
 * A file that belongs to a task, note, event or mood entry.
 *
 * [ownerType]: `TASK`, `NOTE`, `EVENT` or `MOOD`; [ownerId] is that row's id. The owner is
 * polymorphic, so there is no foreign key: `AttachmentDao.deleteOrphans()` runs inside every
 * transaction that deletes owners. [kind]: `IMAGE`, `FILE`, `AUDIO`, `DRAWING` or `SCAN`.
 * [fileName] is a flat, unique name inside the app-private `files/attachments/` folder,
 * never a path.
 */
@Entity(
    tableName = "attachments",
    indices = [Index("owner_type", "owner_id"), Index(value = ["file_name"], unique = true)],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "owner_type") val ownerType: String,
    @ColumnInfo(name = "owner_id") val ownerId: Long,
    val kind: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    /** Name shown to the user (for example the original file name); may be empty. */
    @ColumnInfo(name = "display_name", defaultValue = "") val displayName: String = "",
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Text recognized in an image or scan (searchable). */
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    /** Transcript of an audio recording. */
    val transcript: String? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/** The journal page of a day: one per date, its text lives in note [noteId]. */
@Entity(
    tableName = "journal_entries",
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["date"], unique = true), Index("note_id")],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    @ColumnInfo(name = "note_id") val noteId: Long,
    /** Key of the writing prompt shown that day (resource-backed), if any. */
    @ColumnInfo(name = "prompt_id") val promptId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * A mood and energy check-in (several per day are allowed). [mood] and [energy] are 1..5;
 * at least one is set (checked in code). [tags] is a comma-separated list of tag keys.
 */
@Entity(
    tableName = "mood_entries",
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["note_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("date"), Index("note_id")],
)
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val time: LocalTime? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    @ColumnInfo(defaultValue = "") val tags: String = "",
    @ColumnInfo(name = "note_id") val noteId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/**
 * A challenge (for example "meditate 21 days in a row"). [kind] names the rule, e.g.
 * `HABIT_STREAK`, `TASKS_PER_DAY`, `FOCUS_MINUTES`. [status]: `ACTIVE`, `COMPLETED`,
 * `FAILED` or `ABANDONED`.
 */
@Entity(
    tableName = "challenges",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("habit_id"), Index("status")],
)
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    @ColumnInfo(name = "target_days") val targetDays: Int,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "habit_id") val habitId: Long? = null,
    @ColumnInfo(defaultValue = "ACTIVE") val status: String = "ACTIVE",
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** An earned badge; [key] identifies the badge definition (resource-backed). */
@Entity(tableName = "badges", indices = [Index(value = ["key"], unique = true)])
data class BadgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    @ColumnInfo(name = "earned_at") val earnedAt: Instant,
)

/**
 * Activity history. [entityType]: `TASK`, `PROJECT`, `NOTE`, `NOTEBOOK`, `HABIT`, `GOAL`,
 * `EVENT`. [action]: `CREATED`, `UPDATED`, `COMPLETED`, `REOPENED`, `DELETED`, `RESTORED`,
 * `ARCHIVED`. [summary] is a short label such as the title; it never contains note bodies.
 */
@Entity(tableName = "activity_log", indices = [Index("entity_type", "entity_id"), Index("at")])
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    val action: String,
    val at: Instant,
    @ColumnInfo(defaultValue = "") val summary: String = "",
)

/**
 * Links a local event or task ([localType] `EVENT` or `TASK`) to an event in a device
 * calendar (CalendarContract). [localVersion] is the local `updated_at` at the last sync;
 * [remoteVersion] is a fingerprint of the remote event at the last sync.
 */
@Entity(
    tableName = "calendar_links",
    indices = [
        Index(value = ["local_type", "local_id"], unique = true),
        Index(value = ["calendar_id", "external_event_id"], unique = true),
    ],
)
data class CalendarLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_type") val localType: String,
    @ColumnInfo(name = "local_id") val localId: Long,
    @ColumnInfo(name = "calendar_id") val calendarId: Long,
    @ColumnInfo(name = "external_event_id") val externalEventId: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Instant,
    @ColumnInfo(name = "local_version") val localVersion: Long? = null,
    @ColumnInfo(name = "remote_version") val remoteVersion: String? = null,
)
