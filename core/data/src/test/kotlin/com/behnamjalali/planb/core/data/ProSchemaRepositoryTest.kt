package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.AttachmentEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Existing features keep working on top of the v3 (Pro) columns and tables. */
@RunWith(RobolectricTestRunner::class)
class ProSchemaRepositoryTest {
    private lateinit var db: PlanBDatabase
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var search: FtsSearchRepository
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time)
        search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newTaskFields_roundTripThroughSave() = runTest {
        val start = time.now().plus(Duration.ofHours(2))
        val id = tasks.save(
            Task(title = "Plan", dueDate = time.today(), deadline = time.today().plusDays(3), scheduledStart = start,
                scheduledEnd = start.plus(Duration.ofMinutes(45)), nag = true, estimatedMinutes = 45),
        )
        val stored = tasks.getTask(id)!!
        assertThat(stored.deadline).isEqualTo(time.today().plusDays(3))
        assertThat(stored.scheduledStart).isEqualTo(start)
        assertThat(stored.scheduledEnd).isEqualTo(start.plus(Duration.ofMinutes(45)))
        assertThat(stored.nag).isTrue()
        // Saving from an editor that does not know the new fields keeps them through the model.
        tasks.save(stored.copy(title = "Plan the week"))
        assertThat(tasks.getTask(id)!!.deadline).isEqualTo(time.today().plusDays(3))
    }

    @Test
    fun trashedTasks_leaveListsAndSearch_butKeepTheirData() = runTest {
        val kept = tasks.save(Task(title = "گزارش ماهانه", dueDate = time.today()))
        val trashed = tasks.save(Task(title = "گزارش قدیمی", dueDate = time.today()))
        db.taskDao().setDeletedAt(listOf(trashed), time.now().toEpochMilli(), time.now().toEpochMilli())
        for (view in TaskView.entries) {
            val ids = tasks.observeTasks(TaskFilter(view = view, today = time.today())).first().map { it.id }
            assertThat(ids).doesNotContain(trashed)
        }
        assertThat(tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = time.today())).first().map { it.id }).containsExactly(kept)
        assertThat(search.search("گزارش").map { it.id }).containsExactly(kept)
        assertThat(tasks.tasksWithReminders()).isEmpty()
        assertThat(tasks.getTask(trashed)!!.deletedAt).isEqualTo(time.now())
    }

    @Test
    fun completingRecurringTask_carriesRelativeRemindersAndDeadline() = runTest {
        val due = time.today()
        val id = tasks.save(
            Task(title = "Weekly", dueDate = due, deadline = due.plusDays(1), recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
                scheduledStart = time.now()),
        )
        db.taskReminderDao().insertAll(
            listOf(
                TaskReminderEntity(taskId = id, kind = "OFFSET", offsetMinutes = 60),
                TaskReminderEntity(taskId = id, kind = "ABSOLUTE", at = time.now()),
            ),
        )
        val next = tasks.setCompleted(id, true)!!
        val nextTask = tasks.getTask(next)!!
        assertThat(nextTask.dueDate).isEqualTo(due.plusWeeks(1))
        assertThat(nextTask.deadline).isEqualTo(due.plusDays(1).plusWeeks(1))
        assertThat(nextTask.scheduledStart).isNull()
        assertThat(db.taskReminderDao().forTask(next).map { it.kind to it.offsetMinutes }).containsExactly("OFFSET" to 60)
        // Undo takes the series back and the spawned copy's reminders go with it.
        tasks.setCompleted(id, false)
        assertThat(db.taskReminderDao().forTask(next)).isEmpty()
        assertThat(db.taskReminderDao().forTask(id)).hasSize(2)
    }

    @Test
    fun deletingOwners_removesTheirAttachmentRows() = runTest {
        val taskId = tasks.save(Task(title = "With file"))
        db.attachmentDao().insert(
            AttachmentEntity(ownerType = "TASK", ownerId = taskId, kind = "FILE", fileName = "f.pdf", mimeType = "application/pdf",
                sizeBytes = 3, createdAt = time.now()),
        )
        tasks.delete(listOf(taskId))
        assertThat(db.attachmentDao().allFileNames()).isEmpty()
    }

    @Test
    fun lockedNote_isFoundByTitleOnly_andKeepsItsPayload() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "N"))
        val doc = NoteDocument(blocks = listOf(NoteBlock(id = "b1", text = "رمز مخفی")))
        val id = notes.saveNote(Note(notebookId = notebook, title = "حساب بانکی", document = doc))
        assertThat(search.search("مخفی")).hasSize(1)
        db.noteDao().setLocked(id, true, byteArrayOf(9, 9), NoteDocument.EMPTY.encode(), time.now().toEpochMilli())
        // An ordinary save (for example a rename) re-indexes without the body and keeps the cipher text.
        notes.saveNote(notes.getNote(id)!!.copy(title = "حساب"))
        assertThat(search.search("مخفی")).isEmpty()
        val hit = search.search("حساب").single()
        assertThat(hit.snippet).isEmpty()
        val stored = db.noteDao().getNote(id)!!
        assertThat(stored.locked).isTrue()
        assertThat(stored.encryptedPayload).isEqualTo(byteArrayOf(9, 9))
    }

    @Test
    fun trashedNotes_leaveListsAndSearch() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "N"))
        val id = notes.saveNote(Note(notebookId = notebook, title = "یادداشت سفر"))
        db.noteDao().setDeletedAt(id, time.now().toEpochMilli())
        assertThat(notes.observeNotes(notebook).first()).isEmpty()
        assertThat(notes.observeRecent().first()).isEmpty()
        assertThat(search.search("سفر")).isEmpty()
    }
}
