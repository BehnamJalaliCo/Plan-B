package com.behnamjalali.planb.core.database

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.database.entity.HabitEntity
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
        db.taskDao().insert(task("x"))
        db.backupDao().clearAll()
        assertThat(db.backupDao().tasks()).isEmpty()
    }
}
