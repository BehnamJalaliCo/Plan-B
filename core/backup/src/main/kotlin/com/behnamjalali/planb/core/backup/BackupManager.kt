package com.behnamjalali.planb.core.backup

import android.net.Uri
import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.BackupDao
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

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
            )
        }
        val prefs = preferences.export()
        return BackupArchive(
            manifest = BackupManifest(
                backupFormatVersion = BackupFormat.CURRENT,
                appVersion = version.name,
                appVersionCode = version.code,
                createdAt = time.now().toEpochMilli(),
                databaseSchemaVersion = PlanBDatabase.VERSION,
            ),
            database = database,
            preferences = prefs,
            metadata = BackupMetadata(counts = database.counts(), language = prefs["language"]),
        )
    }

    suspend fun exportTo(uri: Uri) {
        val archive = snapshot()
        files.output(uri) { BackupCodec.write(archive, it) }
    }

    /** Opens, decodes and validates a backup without touching current data. */
    suspend fun inspect(uri: Uri): BackupArchive {
        val archive = files.input(uri) { BackupCodec.read(it) }
        BackupValidator.validate(archive.database)
        return archive
    }

    fun preview(archive: BackupArchive) = BackupPreview(archive.manifest, archive.database.counts())

    /**
     * Replaces all data with [archive] in a single transaction. The archive must
     * come from [inspect]; it is validated again defensively. On any failure the
     * transaction rolls back and the current data stays untouched.
     */
    suspend fun restore(archive: BackupArchive) {
        BackupValidator.validate(archive.database)
        val d = archive.database
        // Remember which alarms exist now so they can be cancelled once the data is replaced.
        val oldReminders = Triple(
            dao.tasks().filter { it.reminderOffsetMinutes != null }.map { it.id },
            dao.events().filter { it.reminderOffsetMinutes != null }.map { it.id },
            dao.habits().filter { it.reminderTime != null }.map { it.id },
        )
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
                searchIndex.rebuild()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.RestoreFailed(e)
        }
        oldReminders.first.forEach { runCatching { reminders.cancelTask(it) } }
        oldReminders.second.forEach { runCatching { reminders.cancelEvent(it) } }
        oldReminders.third.forEach { runCatching { reminders.cancelHabit(it) } }
        // Preferences are restored only after the data committed successfully.
        if (archive.preferences.isNotEmpty()) runCatching { preferences.import(archive.preferences) }
        runCatching { reminders.rescheduleAll() }
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

    /** Permanently deletes all user data (Settings › Data management). */
    suspend fun deleteAllData() {
        db.withTransaction { dao.clearAll() }
        runCatching { reminders.rescheduleAll() }
    }
}
