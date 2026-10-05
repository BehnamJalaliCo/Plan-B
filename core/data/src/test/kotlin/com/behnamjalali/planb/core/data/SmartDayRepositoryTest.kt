package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.OfflineDayPlanRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineRitualJournalRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.PlannedBlock
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro smart day in the data layer: time blocks (#5) and ritual reflections in the journal (#8). */
@RunWith(RobolectricTestRunner::class)
class SmartDayRepositoryTest {
    private lateinit var db: PlanBDatabase
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var plans: OfflineDayPlanRepository
    private lateinit var journal: OfflineRitualJournalRepository
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private val nine: Instant get() = time.today().atTime(9, 0).atZone(time.zone()).toInstant()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time)
        plans = OfflineDayPlanRepository(db, db.taskDao(), time)
        journal = OfflineRitualJournalRepository(db, notes, time)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun applyBlocks_writesOnlyTheBlock_andObserveFindsOverlaps() = runTest {
        val id = tasks.save(Task(title = "Write", dueDate = time.today(), notes = "keep me", estimatedMinutes = 45))
        val written = plans.applyBlocks(listOf(PlannedBlock(id, nine, nine.plus(Duration.ofMinutes(45)))))
        assertThat(written).isEqualTo(1)
        val task = tasks.getTask(id)!!
        assertThat(task.scheduledStart).isEqualTo(nine)
        assertThat(task.scheduledEnd).isEqualTo(nine.plus(Duration.ofMinutes(45)))
        assertThat(task.notes).isEqualTo("keep me")
        assertThat(task.dueDate).isEqualTo(time.today())
        // Overlap is half-open: a range ending at the block's start does not see it.
        assertThat(plans.observeBlocks(nine.minusSeconds(3600), nine).first()).isEmpty()
        assertThat(plans.observeBlocks(nine.plusSeconds(60), nine.plusSeconds(120)).first().map { it.id }).containsExactly(id)
    }

    @Test
    fun applyBlocks_skipsCompletedAndMissingTasks_inOneTransaction() = runTest {
        val open = tasks.save(Task(title = "Open"))
        val done = tasks.save(Task(title = "Done", status = TaskStatus.DONE))
        val written = plans.applyBlocks(
            listOf(
                PlannedBlock(open, nine, nine.plusSeconds(1800)),
                PlannedBlock(done, nine, nine.plusSeconds(1800)),
                PlannedBlock(999, nine, nine.plusSeconds(1800)),
            ),
        )
        assertThat(written).isEqualTo(1)
        assertThat(tasks.getTask(done)!!.scheduledStart).isNull()
        // An invalid block rolls the whole batch back.
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { plans.applyBlocks(listOf(PlannedBlock(open, nine.plusSeconds(7200), nine.plusSeconds(9000)), PlannedBlock(open, nine, nine))) }
        }
        assertThat(tasks.getTask(open)!!.scheduledStart).isEqualTo(nine)
    }

    @Test
    fun completedTasks_leaveTheBlockList_andClearRemovesBlocks() = runTest {
        val a = tasks.save(Task(title = "A"))
        val b = tasks.save(Task(title = "B"))
        plans.applyBlocks(listOf(PlannedBlock(a, nine, nine.plusSeconds(1800)), PlannedBlock(b, nine.plusSeconds(3600), nine.plusSeconds(5400))))
        val day = nine.minusSeconds(9 * 3600)
        assertThat(plans.observeBlocks(day, day.plus(Duration.ofDays(1))).first().map { it.id }).containsExactly(a, b).inOrder()
        tasks.setCompleted(a, true)
        assertThat(plans.observeBlocks(day, day.plus(Duration.ofDays(1))).first().map { it.id }).containsExactly(b)
        plans.clearBlocks(listOf(b))
        assertThat(tasks.getTask(b)!!.scheduledStart).isNull()
        assertThat(plans.observeBlocks(day, day.plus(Duration.ofDays(1))).first()).isEmpty()
    }

    @Test
    fun reflections_createTheJournalPageOnce_andAppendToIt() = runTest {
        val today = time.today()
        val first = journal.append(today, "Intention", "Finish the report calmly", "Journal", "Journal 4 Oct", "ritual_morning")
        val second = journal.append(today, "Reflection", "Good focus in the morning", "Journal", "ignored title", "ritual_evening")
        assertThat(second).isEqualTo(first)
        assertThat(journal.pageOf(today)).isEqualTo(first)
        val note = notes.getNote(first)!!
        assertThat(note.title).isEqualTo("Journal 4 Oct")
        assertThat(note.document.blocks.map { it.type to it.text }).containsExactly(
            BlockType.HEADING to "Intention",
            BlockType.TEXT to "Finish the report calmly",
            BlockType.HEADING to "Reflection",
            BlockType.TEXT to "Good focus in the morning",
        ).inOrder()
        val entry = db.journalDao().entryOn(today.toEpochDay())!!
        assertThat(entry.promptId).isEqualTo("ritual_morning")
        // Another day gets its own page in the same notebook.
        val tomorrow = journal.append(today.plusDays(1), "Intention", "Rest", "Journal", "Journal 5 Oct", "ritual_morning")
        assertThat(tomorrow).isNotEqualTo(first)
        assertThat(notes.observeNotebooks().first().count { it.title == "Journal" }).isEqualTo(1)
    }

    @Test
    fun reflections_useAnExistingNotebookWithThatTitle_andRecreateADeletedPage() = runTest {
        val existing = notes.saveNotebook(Notebook(title = "Journal"))
        val today = time.today()
        val page = journal.append(today, "Reflection", "One", "Journal", "Page", "ritual_evening")
        assertThat(notes.getNote(page)!!.notebookId).isEqualTo(existing)
        notes.deleteNote(page)
        val again = journal.append(today, "Reflection", "Two", "Journal", "Page", "ritual_evening")
        assertThat(again).isNotEqualTo(page)
        assertThat(notes.getNote(again)!!.document.plainText()).contains("Two")
        assertThat(journal.pageOf(today)).isEqualTo(again)
    }

    @Test
    fun blankReflections_areRejected() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { journal.append(time.today(), "Reflection", "  ", "Journal", "Page", "ritual_evening") }
        }
        assertThat(journal.pageOf(time.today())).isNull()
    }
}
