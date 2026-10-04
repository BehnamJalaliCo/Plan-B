package com.behnamjalali.planb.core.database

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.SearchIndexEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: PlanBDatabase
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlanBDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun task(title: String, projectId: Long? = null, parent: Long? = null, due: LocalDate? = null) = TaskEntity(
        title = title, status = "TODO", completed = false, priority = 0, startDate = null, dueDate = due, startTime = null,
        dueTime = null, reminderOffsetMinutes = null, projectId = projectId, parentTaskId = parent, recurrence = null,
        recurrenceAnchor = null, estimatedMinutes = null, actualMinutes = null, sortOrder = 0, createdAt = t0, updatedAt = t0,
        completedAt = null, archived = false,
    )

    @Test
    fun foreignKeys_setNullAndCascade() = runTest {
        val projectId = db.projectDao().insert(
            ProjectEntity(
                title = "P", color = "mint", icon = "folder", status = "ACTIVE", progressMode = "TASKS", manualProgress = 0f,
                startDate = null, dueDate = null, sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false,
            ),
        )
        val parent = db.taskDao().insert(task("parent", projectId))
        db.taskDao().insert(task("child", projectId, parent))
        db.projectDao().delete(projectId)
        assertThat(db.taskDao().getEntity(parent)!!.projectId).isNull()
        db.taskDao().delete(listOf(parent))
        assertThat(db.taskDao().count()).isEqualTo(0)
    }

    @Test
    fun localDates_storedAsEpochDays_andQueryable() = runTest {
        val due = LocalDate.of(2026, 3, 21)
        db.taskDao().insert(task("Nowruz", due = due))
        val raw = db.query(SimpleSQLiteQuery("SELECT due_date FROM tasks"))
        raw.use {
            it.moveToFirst()
            assertThat(it.getLong(0)).isEqualTo(due.toEpochDay())
        }
        assertThat(db.taskDao().openDueBetween(due.toEpochDay(), due.toEpochDay() + 1).single().title).isEqualTo("Nowruz")
    }

    @Test
    fun notebookCascade_removesSectionsAndNotes() = runTest {
        val nb = db.noteDao().insertNotebook(NotebookEntity(title = "N", icon = "book", color = "rose", sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false))
        val section = db.noteDao().insertSection(NotebookSectionEntity(notebookId = nb, title = "S", sortOrder = 0))
        db.noteDao().insertNote(
            NoteEntity(
                notebookId = nb, sectionId = section, title = "n", content = "", contentFormat = "blocks-v1", pinned = false,
                favorite = false, sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false,
            ),
        )
        assertThat(db.noteDao().observeNotebooks(false).first().single().noteCount).isEqualTo(1)
        db.noteDao().deleteSection(section)
        assertThat(db.noteDao().notesInNotebook(nb).single().sectionId).isNull()
        db.noteDao().deleteNotebook(nb)
        assertThat(db.noteDao().notesInNotebook(nb)).isEmpty()
    }

    @Test
    fun habitCompletion_uniquePerDay_adjust() = runTest {
        val habit = db.habitDao().insert(
            HabitEntity(
                title = "h", icon = "star", color = "mint", schedule = "DAILY", target = 2, unit = "", reminderTime = null,
                startDate = LocalDate.of(2026, 1, 1), createdAt = t0, updatedAt = t0, archived = false,
            ),
        )
        val day = LocalDate.of(2026, 1, 2).toEpochDay()
        db.habitDao().adjust(habit, day, 1, 0)
        db.habitDao().adjust(habit, day, 1, 0)
        assertThat(db.habitDao().completion(habit, day)!!.amount).isEqualTo(2)
        db.habitDao().adjust(habit, day, -5, 0)
        assertThat(db.habitDao().completion(habit, day)).isNull()
    }

    @Test
    fun ftsIndex_matchAndReplaceByRowId() = runTest {
        val dao = db.searchDao()
        dao.upsert(SearchIndexEntity(rowId = 17, entityType = 1, entityId = 1, content = "کتاب planb"))
        assertThat(dao.search("کتا*", 10).single().entityId).isEqualTo(1)
        dao.upsert(SearchIndexEntity(rowId = 17, entityType = 1, entityId = 1, content = "دفتر"))
        assertThat(dao.search("کتا*", 10)).isEmpty()
        assertThat(dao.search("plan*", 10)).isEmpty()
        assertThat(dao.search("دفتر", 10)).hasSize(1)
    }

    @Test
    fun backupDao_clearAllEmptiesEveryTable() = runTest {
        val taskId = db.taskDao().insert(task("x"))
        db.taskReminderDao().insert(TaskReminderEntity(taskId = taskId, kind = "OFFSET", offsetMinutes = 30))
        db.activityLogDao().insert(ActivityLogEntity(entityType = "TASK", entityId = taskId, action = "CREATED", at = t0))
        db.challengeDao().award(BadgeEntity(key = "k", earnedAt = t0))
        db.backupDao().clearAll()
        assertThat(db.backupDao().tasks()).isEmpty()
        assertThat(db.backupDao().taskReminders()).isEmpty()
        assertThat(db.backupDao().activityLog()).isEmpty()
        assertThat(db.backupDao().badges()).isEmpty()
    }

    private suspend fun notebookWithNote(title: String = "n"): Pair<Long, Long> {
        val nb = db.noteDao().insertNotebook(NotebookEntity(title = "N", icon = "book", color = "rose", sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false))
        val note = db.noteDao().insertNote(
            NoteEntity(
                notebookId = nb, sectionId = null, title = title, content = "", contentFormat = "blocks-v1", pinned = true,
                favorite = true, sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false,
            ),
        )
        return nb to note
    }

    @Test
    fun trashedTasks_areLeftOutOfEveryList() = runTest {
        val due = LocalDate.of(2026, 3, 21)
        val projectId = db.projectDao().insert(
            ProjectEntity(
                title = "P", color = "mint", icon = "folder", status = "ACTIVE", progressMode = "TASKS", manualProgress = 0f,
                startDate = null, dueDate = null, sortOrder = 0, createdAt = t0, updatedAt = t0, archived = false,
            ),
        )
        val kept = db.taskDao().insert(task("kept", projectId, due = due).copy(reminderOffsetMinutes = 10))
        val trashed = db.taskDao().insert(task("trashed", projectId, due = due).copy(reminderOffsetMinutes = 10))
        val sub = db.taskDao().insert(task("sub", parent = kept))
        db.taskDao().insert(task("trashed sub", parent = kept).copy(deletedAt = t0))
        db.taskDao().setDeletedAt(listOf(trashed), t0.toEpochMilli(), t0.toEpochMilli())

        val all = db.taskDao().getTasks(SimpleSQLiteQuery("${TaskDao.SELECT_WITH_COUNTS} WHERE t.deleted_at IS NULL AND t.parent_task_id IS NULL"))
        assertThat(all.map { it.task.title }).containsExactly("kept")
        assertThat(all.single().subtaskCount).isEqualTo(1)
        assertThat(db.taskDao().observeSubtasks(kept).first().map { it.task.id }).containsExactly(sub)
        assertThat(db.taskDao().tasksWithReminders().map { it.id }).containsExactly(kept)
        assertThat(db.taskDao().openDueBetween(due.toEpochDay(), due.toEpochDay() + 1).map { it.id }).containsExactly(kept)
        assertThat(db.taskDao().priorities(due.toEpochDay(), due.toEpochDay(), 10).map { it.id }).containsExactly(kept)
        assertThat(db.projectDao().observeProject(projectId).first()!!.totalTasks).isEqualTo(1)
        assertThat(db.taskDao().observeTrash().first().map { it.task.title }).containsExactly("trashed", "trashed sub")
        assertThat(db.taskDao().trashedBefore(t0.toEpochMilli() + 1)).hasSize(2)

        db.taskDao().setDeletedAt(listOf(trashed), null, t0.toEpochMilli())
        assertThat(db.taskDao().tasksWithReminders().map { it.id }).containsExactly(kept, trashed)
    }

    @Test
    fun trashedNotes_areLeftOutOfEveryList() = runTest {
        val (nb, note) = notebookWithNote()
        val (_, other) = notebookWithNote("other")
        db.noteDao().setDeletedAt(note, t0.toEpochMilli())
        assertThat(db.noteDao().observeNotes(nb, null).first()).isEmpty()
        assertThat(db.noteDao().observeRecent(10).first().map { it.note.id }).containsExactly(other)
        assertThat(db.noteDao().observePinnedOrFavorite(10).first().map { it.note.id }).containsExactly(other)
        assertThat(db.noteDao().observeNotebooks(false).first().first { it.notebook.id == nb }.noteCount).isEqualTo(0)
        assertThat(db.noteDao().countCreatedBetween(0, t0.toEpochMilli() + 1)).isEqualTo(1)
        db.noteDao().setArchived(note, true)
        assertThat(db.noteDao().observeArchived().first()).isEmpty()
        assertThat(db.noteDao().observeTrash().first().map { it.note.id }).containsExactly(note)
        // Still reachable by id, so it can be restored.
        assertThat(db.noteDao().getNote(note)!!.deletedAt).isEqualTo(t0)
        db.noteDao().setDeletedAt(note, null)
        assertThat(db.noteDao().observeArchived().first().map { it.note.id }).containsExactly(note)
    }

    @Test
    fun lockedNote_keepsEncryptedPayload() = runTest {
        val (_, note) = notebookWithNote()
        val payload = byteArrayOf(1, 2, 3)
        db.noteDao().setLocked(note, true, payload, "", 5)
        val stored = db.noteDao().getNote(note)!!
        assertThat(stored.locked).isTrue()
        assertThat(stored.encryptedPayload).isEqualTo(payload)
        assertThat(stored).isEqualTo(stored.copy(encryptedPayload = byteArrayOf(1, 2, 3)))
    }

    @Test
    fun taskReminders_dependencies_andCascades() = runTest {
        val a = db.taskDao().insert(task("a"))
        val b = db.taskDao().insert(task("b"))
        db.taskReminderDao().replace(a, listOf(
            TaskReminderEntity(taskId = 0, kind = "OFFSET", offsetMinutes = 60),
            TaskReminderEntity(taskId = 0, kind = "ABSOLUTE", at = t0),
        ))
        assertThat(db.taskReminderDao().count(a)).isEqualTo(2)
        assertThat(db.taskReminderDao().activeReminders()).hasSize(2)
        db.taskDao().setDeletedAt(listOf(a), 1, 1)
        assertThat(db.taskReminderDao().activeReminders()).isEmpty()

        db.taskDependencyDao().insert(TaskDependencyEntity(b, a))
        db.taskDependencyDao().insert(TaskDependencyEntity(b, a)) // duplicate is ignored
        assertThat(db.taskDependencyDao().observeDependencies(b).first()).containsExactly(a)
        assertThat(db.taskDependencyDao().observeDependents(a).first()).containsExactly(b)
        assertThat(db.taskDependencyDao().openBlockers(b)).isEmpty() // a is in the trash
        db.taskDao().setDeletedAt(listOf(a), null, 1)
        assertThat(db.taskDependencyDao().openBlockers(b)).containsExactly(a)

        db.taskDao().delete(listOf(a))
        assertThat(db.taskReminderDao().forTask(a)).isEmpty()
        assertThat(db.taskDependencyDao().all()).isEmpty()
    }

    @Test
    fun noteLinksVersionsAndAttachments() = runTest {
        val (_, a) = notebookWithNote("a")
        val (nb2, b) = notebookWithNote("b")
        db.noteLinkDao().replaceOutgoing(a, listOf(b, a, b))
        assertThat(db.noteLinkDao().observeOutgoing(a).first()).containsExactly(b)
        assertThat(db.noteLinkDao().observeBacklinks(b).first()).containsExactly(a)
        assertThat(db.noteLinkDao().observeGraph().first()).hasSize(1)

        repeat(5) { db.noteVersionDao().insert(NoteVersionEntity(noteId = a, createdAt = t0.plusSeconds(it.toLong()), title = "v$it", content = "{}", size = 2)) }
        db.noteVersionDao().prune(a, keep = 3)
        assertThat(db.noteVersionDao().observeForNote(a).first().map { it.title }).containsExactly("v4", "v3", "v2").inOrder()

        val attachment = AttachmentEntity(ownerType = "NOTE", ownerId = b, kind = "IMAGE", fileName = "b.jpg", mimeType = "image/jpeg", sizeBytes = 10, createdAt = t0)
        db.attachmentDao().insert(attachment)
        db.attachmentDao().insert(attachment.copy(ownerType = "TASK", ownerId = 999, fileName = "orphan.jpg"))
        assertThat(db.attachmentDao().deleteOrphans()).isEqualTo(1)
        db.noteDao().deleteNotebook(nb2)
        assertThat(db.noteLinkDao().observeGraph().first()).isEmpty()
        assertThat(db.attachmentDao().deleteOrphans()).isEqualTo(1)
        assertThat(db.attachmentDao().allFileNames()).isEmpty()
    }

    @Test
    fun journalMoodChallengesAndLinks() = runTest {
        val (_, note) = notebookWithNote()
        val day = LocalDate.of(2026, 10, 4)
        db.journalDao().insertEntry(JournalEntryEntity(date = day, noteId = note, promptId = "gratitude", createdAt = t0, updatedAt = t0))
        assertThat(db.journalDao().entryOn(day.toEpochDay())!!.noteId).isEqualTo(note)
        db.journalDao().insertMood(MoodEntryEntity(date = day, mood = 4, energy = 2, tags = "work,sleep", noteId = note, createdAt = t0, updatedAt = t0))
        db.journalDao().insertMood(MoodEntryEntity(date = day, mood = 5, createdAt = t0, updatedAt = t0))
        assertThat(db.journalDao().observeMoods(day.toEpochDay(), day.toEpochDay()).first()).hasSize(2)

        assertThat(db.challengeDao().award(BadgeEntity(key = "streak_7", earnedAt = t0))).isGreaterThan(0)
        assertThat(db.challengeDao().award(BadgeEntity(key = "streak_7", earnedAt = t0.plusSeconds(9)))).isEqualTo(-1)
        assertThat(db.challengeDao().observeBadges().first().single().earnedAt).isEqualTo(t0)
        db.challengeDao().insert(ChallengeEntity(kind = "TASKS_PER_DAY", title = "c", targetDays = 7, startDate = day, createdAt = t0, updatedAt = t0))
        assertThat(db.challengeDao().observeByStatus("ACTIVE").first()).hasSize(1)

        db.calendarLinkDao().upsert(CalendarLinkEntity(localType = "EVENT", localId = 1, calendarId = 2, externalEventId = 3, lastSyncedAt = t0))
        assertThat(db.calendarLinkDao().forExternal(2, 3)!!.localId).isEqualTo(1)
        db.savedFilterDao().insert(SavedFilterEntity(name = "f", icon = "star", color = "mint", query = "{}", sortOrder = 1, createdAt = t0, updatedAt = t0))
        assertThat(db.savedFilterDao().observeAll().first()).hasSize(1)
    }
}
