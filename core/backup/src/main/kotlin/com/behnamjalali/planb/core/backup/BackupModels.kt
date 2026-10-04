package com.behnamjalali.planb.core.backup

import kotlinx.serialization.Serializable

/**
 * Backup archive format (see docs/BACKUP_FORMAT.md). All dates are canonical:
 * epoch-day for dates, second-of-day for times, epoch-millis UTC for instants.
 * Every field that was added after format 1 must have a default so older
 * backups keep importing.
 */
@Serializable
data class BackupManifest(
    val backupFormatVersion: Int,
    val appVersion: String,
    val appVersionCode: Int = 0,
    val createdAt: Long,
    val databaseSchemaVersion: Int = 0,
    val application: String = BackupFormat.APPLICATION_ID,
)

@Serializable
data class BackupMetadata(
    val counts: Map<String, Int> = emptyMap(),
    val language: String? = null,
)

@Serializable data class TagDto(val id: Long, val name: String, val color: String = "lavender")

@Serializable
data class TaskDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val status: String = "TODO",
    val completed: Boolean = false,
    val priority: Int = 0,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val startTime: Int? = null,
    val dueTime: Int? = null,
    val reminderOffsetMinutes: Int? = null,
    val projectId: Long? = null,
    val parentTaskId: Long? = null,
    val recurrence: String? = null,
    val recurrenceAnchor: Long? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
    val notes: String = "",
    val sortOrder: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val archived: Boolean = false,
    // Format 2 (schema v3).
    val deadline: Long? = null,
    val scheduledStart: Long? = null,
    val scheduledEnd: Long? = null,
    val nag: Boolean = false,
    val deletedAt: Long? = null,
)

@Serializable data class RefDto(val a: Long, val b: Long)

@Serializable
data class ProjectDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val color: String = "lavender",
    val icon: String = "folder",
    val status: String = "ACTIVE",
    val progressMode: String = "TASKS",
    val manualProgress: Float = 0f,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val archived: Boolean = false,
)

@Serializable
data class MilestoneDto(val id: Long, val parentId: Long, val title: String, val date: Long? = null, val target: Double? = null, val completed: Boolean = false, val sortOrder: Long = 0)

@Serializable
data class NotebookDto(
    val id: Long, val title: String, val icon: String = "book", val color: String = "lavender", val sortOrder: Long = 0,
    val createdAt: Long = 0, val updatedAt: Long = 0, val archived: Boolean = false,
)

@Serializable data class SectionDto(val id: Long, val notebookId: Long, val title: String, val sortOrder: Long = 0)

@Serializable
data class NoteDto(
    val id: Long, val notebookId: Long, val sectionId: Long? = null, val title: String = "", val content: String = "",
    val contentFormat: String = "blocks-v1", val pinned: Boolean = false, val favorite: Boolean = false, val sortOrder: Long = 0,
    val createdAt: Long = 0, val updatedAt: Long = 0, val archived: Boolean = false,
    // Format 2 (schema v3). The encrypted body is Base64 (standard alphabet, padded).
    val deletedAt: Long? = null, val locked: Boolean = false, val encryptedPayload: String? = null,
)

@Serializable
data class HabitDto(
    val id: Long, val title: String, val icon: String = "star", val color: String = "mint", val schedule: String = "DAILY",
    val target: Int = 1, val unit: String = "", val reminderTime: Int? = null, val startDate: Long, val createdAt: Long = 0,
    val updatedAt: Long = 0, val archived: Boolean = false,
    // Format 2 (schema v3).
    val healthMetric: String? = null, val healthThreshold: Long? = null,
)

@Serializable data class HabitCompletionDto(val id: Long, val habitId: Long, val date: Long, val amount: Int = 1, val createdAt: Long = 0)

@Serializable
data class GoalDto(
    val id: Long, val title: String, val description: String = "", val target: Double = 100.0, val currentValue: Double = 0.0,
    val unit: String = "", val deadline: Long? = null, val projectId: Long? = null, val notes: String = "", val createdAt: Long = 0,
    val updatedAt: Long = 0, val archived: Boolean = false,
)

@Serializable
data class EventDto(
    val id: Long, val title: String, val description: String = "", val date: Long, val startTime: Int? = null, val endTime: Int? = null,
    val allDay: Boolean = true, val reminderOffsetMinutes: Int? = null, val recurrence: String? = null, val color: String = "powder_blue",
    val notes: String = "", val createdAt: Long = 0, val updatedAt: Long = 0,
)

@Serializable
data class FocusDto(
    val id: Long, val linkedTaskId: Long? = null, val startedAt: Long, val endedAt: Long? = null, val plannedDurationMillis: Long,
    val actualDurationMillis: Long = 0, val status: String = "COMPLETED", val runningSince: Long? = null, val accumulatedMillis: Long = 0,
    // Format 2 (schema v3).
    val soundId: String? = null, val strict: Boolean = false,
)

@Serializable
data class TemplateDto(val id: Long, val title: String, val type: String, val payload: String, val createdAt: Long = 0, val updatedAt: Long = 0)

// Format 2 (schema v3) tables.

@Serializable
data class TaskReminderDto(val id: Long, val taskId: Long, val kind: String = "OFFSET", val offsetMinutes: Int? = null, val at: Long? = null)

@Serializable
data class SavedFilterDto(
    val id: Long, val name: String, val icon: String = "star", val color: String = "lavender", val query: String = "{}",
    val sortOrder: Long = 0, val createdAt: Long = 0, val updatedAt: Long = 0,
)

@Serializable
data class NoteVersionDto(val id: Long, val noteId: Long, val createdAt: Long = 0, val title: String = "", val content: String = "", val size: Long = 0)

/** File bytes are in the archive at `attachments/<fileName>`. */
@Serializable
data class AttachmentDto(
    val id: Long, val ownerType: String, val ownerId: Long, val kind: String = "FILE", val fileName: String,
    val displayName: String = "", val mimeType: String = "application/octet-stream", val sizeBytes: Long = 0,
    val durationMillis: Long? = null, val width: Int? = null, val height: Int? = null, val ocrText: String? = null,
    val transcript: String? = null, val sortOrder: Long = 0, val createdAt: Long = 0,
)

@Serializable
data class JournalEntryDto(val id: Long, val date: Long, val noteId: Long, val promptId: String? = null, val createdAt: Long = 0, val updatedAt: Long = 0)

@Serializable
data class MoodEntryDto(
    val id: Long, val date: Long, val time: Int? = null, val mood: Int? = null, val energy: Int? = null, val tags: String = "",
    val noteId: Long? = null, val createdAt: Long = 0, val updatedAt: Long = 0,
)

@Serializable
data class ChallengeDto(
    val id: Long, val kind: String, val title: String = "", val targetDays: Int = 1, val startDate: Long, val habitId: Long? = null,
    val status: String = "ACTIVE", val completedAt: Long? = null, val createdAt: Long = 0, val updatedAt: Long = 0,
)

@Serializable data class BadgeDto(val id: Long, val key: String, val earnedAt: Long = 0)

@Serializable
data class ActivityDto(val id: Long, val entityType: String, val entityId: Long, val action: String, val at: Long = 0, val summary: String = "")

@Serializable
data class CalendarLinkDto(
    val id: Long, val localType: String, val localId: Long, val calendarId: Long, val externalEventId: Long, val lastSyncedAt: Long = 0,
    val localVersion: Long? = null, val remoteVersion: String? = null,
)

/** database.json: one array per table. Missing arrays decode as empty. */
@Serializable
data class BackupDatabase(
    val tags: List<TagDto> = emptyList(),
    val projects: List<ProjectDto> = emptyList(),
    val projectTags: List<RefDto> = emptyList(),
    val projectMilestones: List<MilestoneDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val taskTags: List<RefDto> = emptyList(),
    val notebooks: List<NotebookDto> = emptyList(),
    val sections: List<SectionDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val noteTags: List<RefDto> = emptyList(),
    val habits: List<HabitDto> = emptyList(),
    val habitCompletions: List<HabitCompletionDto> = emptyList(),
    val goals: List<GoalDto> = emptyList(),
    val goalMilestones: List<MilestoneDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val focusSessions: List<FocusDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    // Format 2 (schema v3).
    val taskReminders: List<TaskReminderDto> = emptyList(),
    /** `a` = task id, `b` = the task it depends on. */
    val taskDependencies: List<RefDto> = emptyList(),
    val savedFilters: List<SavedFilterDto> = emptyList(),
    val noteVersions: List<NoteVersionDto> = emptyList(),
    /** `a` = linking note id, `b` = linked note id. */
    val noteLinks: List<RefDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val journalEntries: List<JournalEntryDto> = emptyList(),
    val moodEntries: List<MoodEntryDto> = emptyList(),
    val challenges: List<ChallengeDto> = emptyList(),
    val badges: List<BadgeDto> = emptyList(),
    val activityLog: List<ActivityDto> = emptyList(),
    val calendarLinks: List<CalendarLinkDto> = emptyList(),
) {
    fun counts(): Map<String, Int> = mapOf(
        "tasks" to tasks.size, "projects" to projects.size, "notebooks" to notebooks.size, "notes" to notes.size,
        "habits" to habits.size, "goals" to goals.size, "events" to events.size, "focusSessions" to focusSessions.size,
        "templates" to templates.size, "tags" to tags.size, "attachments" to attachments.size,
    )
}

object BackupFormat {
    /**
     * Current format written by this app. Readers accept 1..CURRENT.
     * 2: schema v3 tables and columns, plus attachment files in `attachments/`.
     */
    const val CURRENT = 2
    const val APPLICATION_ID = "com.behnamjalali.planb"
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database.json"
    const val PREFERENCES = "preferences.json"
    const val METADATA = "metadata.json"

    /** Folder of attachment files: `attachments/<fileName>`, one flat level. */
    const val ATTACHMENTS_DIR = "attachments/"
    const val MIME = "application/zip"

    /**
     * Hard limits protecting against zip bombs and corrupt archives. Entries are read into
     * memory and then decoded as text (about three times their size at peak), so the caps
     * stay well below a phone's heap while leaving room for very large real backups.
     */
    const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 40L * 1024 * 1024

    /**
     * Attachment files are streamed to app-private storage, never held in memory, so they
     * have their own caps: the same per-file limit as a JSON entry, a total that bounds the
     * disk space a restore can take, and a maximum number of files.
     */
    const val MAX_ATTACHMENT_BYTES = MAX_ENTRY_BYTES
    const val MAX_ATTACHMENTS_TOTAL_BYTES = 1024L * 1024 * 1024
    const val MAX_ATTACHMENTS = 10_000
    const val MAX_ENTRIES = 16 + MAX_ATTACHMENTS
}

/** Typed failures shown to the user with a clear message. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup(detail: String) : BackupException("Not a Plan-B backup: $detail")
    class Corrupt(detail: String) : BackupException("Backup is damaged: $detail")
    class UnsupportedVersion(val version: Int) : BackupException("Backup format $version is newer than this app supports")

    /** The backup's database schema is newer than this app's: it was made by a newer version. */
    class NewerDatabase(val schemaVersion: Int) : BackupException("Backup was made by a newer version of the app (database schema $schemaVersion)")
    class Invalid(detail: String) : BackupException("Backup data is inconsistent: $detail")
    class RestoreFailed(cause: Throwable) : BackupException("Restore failed; your current data was kept (${cause.message})")
}
