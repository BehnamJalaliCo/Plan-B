package com.behnamjalali.planb.core.backup

import android.net.Uri
import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.BackupDao
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class AppVersion(val name: String, val code: Int)

/** Summary shown to the user before they confirm a restore. */
data class BackupPreview(val manifest: BackupManifest, val counts: Map<String, Int>)

@Singleton
class BackupManager @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: BackupDao,
    private val preferences: UserPreferencesDataSource,
    private val files: DocumentFiles,
    private val searchIndex: SearchIndexMaintenance,
    private val reminders: ReminderScheduler,
    private val time: TimeProvider,
    private val version: AppVersion,
    private val attachments: AttachmentFiles,
) {
    /** Reads every table inside one transaction so the snapshot is consistent. */
    suspend fun snapshot(): BackupArchive {
        val database = db.withTransaction {
            BackupDatabase(
                tags = dao.tags().map { it.dto() },
                projects = dao.projects().map { it.dto() },
                projectTags = dao.projectTags().map { it.dto() },
                projectMilestones = dao.projectMilestones().map { it.dto() },
                tasks = dao.tasks().map { it.dto() },
                taskTags = dao.taskTags().map { it.dto() },
                notebooks = dao.notebooks().map { it.dto() },
                sections = dao.sections().map { it.dto() },
                notes = dao.notes().map { it.dto() },
                noteTags = dao.noteTags().map { it.dto() },
                habits = dao.habits().map { it.dto() },
                habitCompletions = dao.habitCompletions().map { it.dto() },
                goals = dao.goals().map { it.dto() },
                goalMilestones = dao.goalMilestones().map { it.dto() },
                events = dao.events().map { it.dto() },
                focusSessions = dao.focusSessions().map { it.dto() },
                templates = dao.templates().map { it.dto() },
                taskReminders = dao.taskReminders().map { it.dto() },
                taskDependencies = dao.taskDependencies().map { it.dto() },
                savedFilters = dao.savedFilters().map { it.dto() },
                noteVersions = dao.noteVersions().map { it.dto() },
                noteLinks = dao.noteLinks().map { it.dto() },
                // Only attachments whose file exists: a restore never creates rows without bytes.
                attachments = dao.attachments().filter { attachmentFile(it.fileName)?.isFile == true }.map { it.dto() },
                journalEntries = dao.journalEntries().map { it.dto() },
                moodEntries = dao.moodEntries().map { it.dto() },
                challenges = dao.challenges().map { it.dto() },
                badges = dao.badges().map { it.dto() },
                activityLog = dao.activityLog().map { it.dto() },
                calendarLinks = dao.calendarLinks().map { it.dto() },
            )
        }
        // Rows of deleted owners are not exported (they are removed on the next sweep anyway).
        val owners = mapOf(
            "TASK" to database.tasks.map { it.id }.toSet(),
            "NOTE" to database.notes.map { it.id }.toSet(),
            "EVENT" to database.events.map { it.id }.toSet(),
            "MOOD" to database.moodEntries.map { it.id }.toSet(),
        )
        val withOwners = database.copy(attachments = database.attachments.filter { owners[it.ownerType]?.contains(it.ownerId) == true })
        val prefs = preferences.export()
        return BackupArchive(
            manifest = BackupManifest(
                backupFormatVersion = BackupFormat.CURRENT,
                appVersion = version.name,
                appVersionCode = version.code,
                createdAt = time.now().toEpochMilli(),
                databaseSchemaVersion = PlanBDatabase.VERSION,
            ),
            database = withOwners,
            preferences = prefs,
            metadata = BackupMetadata(counts = withOwners.counts(), language = prefs["language"]),
            attachmentFiles = withOwners.attachments.mapNotNull { a -> attachmentFile(a.fileName)?.let { a.fileName to it } }.toMap(),
        )
    }

    private fun attachmentFile(name: String) = if (AttachmentFiles.isSafeName(name)) attachments.file(name) else null

    suspend fun exportTo(uri: Uri) {
        val archive = snapshot()
        files.output(uri) { BackupCodec.write(archive, it) }
    }

    /**
     * Opens, decodes and validates a backup without touching current data. Attachment files
     * are unpacked into a private staging folder; call [discard] if the restore is cancelled.
     */
    suspend fun inspect(uri: Uri): BackupArchive {
        val staging = attachments.newStagingDirectory()
        try {
            val archive = files.input(uri) { BackupCodec.read(it, stagingDirectory = staging) }
            validate(archive)
            return archive
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Removes the files unpacked by [inspect] for a restore that will not happen. */
    fun discard(archive: BackupArchive) {
        archive.stagingDirectory?.deleteRecursively()
    }

    private fun validate(archive: BackupArchive) = BackupValidator.validate(
        archive.database,
        attachmentFiles = archive.attachmentFiles.filterValues { it.isFile }.keys,
        requireAttachmentFiles = true,
    )

    fun preview(archive: BackupArchive) = BackupPreview(archive.manifest, archive.database.counts())

    /**
     * Replaces all data with [archive] in a single transaction. The archive must
     * come from [inspect]; it is validated again defensively. On any failure the
     * transaction rolls back and the current data stays untouched.
     */
    suspend fun restore(archive: BackupArchive) {
        validate(archive)
        val d = archive.database
        // Remember which alarms exist now so they can be cancelled once the data is replaced.
        val oldReminders = currentReminders()
        // The restored files replace the current ones; the current folder is kept aside until
        // the database transaction has committed, and put back if it fails.
        val replacement = try {
            attachments.replaceWith(archive.stagingDirectory?.also { dir -> keepOnly(dir, d.attachments.map { it.fileName }.toSet()) })
        } catch (e: IllegalStateException) {
            throw BackupException.RestoreFailed(e)
        }
        try {
            db.withTransaction {
                dao.clearAll()
                dao.insertTags(d.tags.map { it.entity() })
                dao.insertProjects(d.projects.map { it.entity() })
                dao.insertProjectTags(d.projectTags.map { com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef(it.a, it.b) })
                dao.insertProjectMilestones(d.projectMilestones.map { it.projectMilestone() })
                dao.insertTasks(orderParentsFirst(d.tasks).map { it.entity() })
                dao.insertTaskTags(d.taskTags.map { com.behnamjalali.planb.core.database.entity.TaskTagCrossRef(it.a, it.b) })
                dao.insertNotebooks(d.notebooks.map { it.entity() })
                dao.insertSections(d.sections.map { it.entity() })
                dao.insertNotes(d.notes.map { it.entity() })
                dao.insertNoteTags(d.noteTags.map { com.behnamjalali.planb.core.database.entity.NoteTagCrossRef(it.a, it.b) })
                dao.insertHabits(d.habits.map { it.entity() })
                dao.insertHabitCompletions(d.habitCompletions.map { it.entity() })
                dao.insertGoals(d.goals.map { it.entity() })
                dao.insertGoalMilestones(d.goalMilestones.map { it.goalMilestone() })
                dao.insertEvents(d.events.map { it.entity() })
                dao.insertFocusSessions(d.focusSessions.map { it.entity() })
                dao.insertTemplates(d.templates.map { it.entity() })
                dao.insertTaskReminders(d.taskReminders.map { it.entity() })
                dao.insertTaskDependencies(d.taskDependencies.map { it.taskDependency() })
                dao.insertSavedFilters(d.savedFilters.map { it.entity() })
                dao.insertNoteVersions(d.noteVersions.map { it.entity() })
                dao.insertNoteLinks(d.noteLinks.map { it.noteLink() })
                dao.insertJournalEntries(d.journalEntries.map { it.entity() })
                dao.insertMoodEntries(d.moodEntries.map { it.entity() })
                dao.insertAttachments(d.attachments.map { it.entity() })
                dao.insertChallenges(d.challenges.map { it.entity() })
                dao.insertBadges(d.badges.map { it.entity() })
                dao.insertActivityLog(d.activityLog.map { it.entity() })
                dao.insertCalendarLinks(d.calendarLinks.map { it.entity() })
                searchIndex.rebuild()
            }
        } catch (e: CancellationException) {
            replacement.rollback()
            throw e
        } catch (e: Exception) {
            replacement.rollback()
            throw BackupException.RestoreFailed(e)
        } catch (e: OutOfMemoryError) {
            // The transaction rolled back; report it like any other failed restore.
            replacement.rollback()
            throw BackupException.RestoreFailed(e)
        }
        runCatching { replacement.commit() }
        // The data is committed: the follow-up steps must run to the end even if the caller
        // is cancelled meanwhile, or preferences and alarms would not match the data.
        withContext(NonCancellable) {
            cancel(oldReminders)
            // Preferences are restored only after the data committed successfully.
            if (archive.preferences.isNotEmpty()) runCatchingSafely { preferences.import(archive.preferences) }
            runCatchingSafely { reminders.rescheduleAll() }
        }
    }

    /** Deletes unpacked files that no attachment row refers to. */
    private fun keepOnly(dir: java.io.File, names: Set<String>) {
        dir.listFiles().orEmpty().filter { it.name !in names }.forEach { it.deleteRecursively() }
    }

    /** Topological order so a subtask is never inserted before its parent. */
    private fun orderParentsFirst(tasks: List<TaskDto>): List<TaskDto> {
        val byId = tasks.associateBy { it.id }
        val result = LinkedHashMap<Long, TaskDto>()
        fun visit(t: TaskDto) {
            if (t.id in result) return
            t.parentTaskId?.let { p -> byId[p]?.let(::visit) }
            result[t.id] = t
        }
        tasks.forEach(::visit)
        return result.values.toList()
    }

    /**
     * Permanently deletes all user data (Settings › Data management). Alarms and posted
     * notifications of the deleted items are cancelled too, so none of them can fire later
     * for a restored item that reuses the same id.
     */
    suspend fun deleteAllData() {
        val oldReminders = currentReminders()
        db.withTransaction { dao.clearAll() }
        withContext(NonCancellable) {
            runCatchingSafely { attachments.deleteAll() }
            cancel(oldReminders)
            runCatchingSafely { reminders.cancelFocusEnd() }
            runCatchingSafely { reminders.rescheduleAll() }
        }
    }

    private class Reminders(val tasks: List<Long>, val events: List<Long>, val habits: List<Long>)

    private suspend fun currentReminders() = Reminders(
        // The primary reminder, or extra reminders and nagging (Plan-B Pro #12).
        (dao.tasks().filter { it.reminderOffsetMinutes != null || it.nag }.map { it.id } + dao.taskReminders().map { it.taskId }).distinct(),
        dao.events().filter { it.reminderOffsetMinutes != null }.map { it.id },
        dao.habits().filter { it.reminderTime != null }.map { it.id },
    )

    /** Cancels alarms and posted notifications; one failure never stops the rest. */
    private suspend fun cancel(old: Reminders) {
        old.tasks.forEach { runCatchingSafely { reminders.cancelTask(it) } }
        old.events.forEach { runCatchingSafely { reminders.cancelEvent(it) } }
        old.habits.forEach { runCatchingSafely { reminders.cancelHabit(it) } }
    }
}
