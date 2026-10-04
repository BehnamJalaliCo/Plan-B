package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineEventRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.time.measureTimedValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Generates a realistic heavy-user dataset (thousands of tasks, notes and events) and
 * checks that the hot read paths stay fast. Bounds are generous because CI machines vary;
 * they exist to catch accidental full scans or N+1 queries, not to micro-benchmark.
 */
@RunWith(RobolectricTestRunner::class)
class LargeDataTest {
    private lateinit var db: PlanBDatabase
    private val time = FakeTimeProvider()
    private val today: LocalDate get() = time.today()
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var events: OfflineEventRepository
    private lateinit var search: FtsSearchRepository

    @Before
    fun setUp() {
        db = TestDatabase.create()
        val reminders = RecordingReminderScheduler()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time)
        events = OfflineEventRepository(db, db.eventDao(), db.searchDao(), time, reminders)
        search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
        runBlocking { generate() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun generate() {
        val random = Random(42)
        val words = listOf("جلسه", "گزارش", "خرید", "meeting", "report", "design", "سفر", "کتاب", "review", "پروژه")
        repeat(TASKS) { i ->
            tasks.save(
                Task(
                    title = "${words[i % words.size]} ${words[random.nextInt(words.size)]} #$i",
                    dueDate = today.plusDays(random.nextLong(-180, 180)),
                    dueTime = if (i % 3 == 0) LocalTime.of(random.nextInt(8, 20), 0) else null,
                    priority = Priority.entries[i % Priority.entries.size],
                ),
            )
        }
        val notebooks = (1..10).map { notes.saveNotebook(Notebook(title = "Notebook $it")) }
        repeat(NOTES) { i ->
            val blocks = (0 until 12).map { b -> NoteBlock("$i-$b", if (b % 4 == 0) BlockType.HEADING else BlockType.TEXT, "${words[(i + b) % words.size]} متن نمونه $b") }
            notes.saveNote(Note(notebookId = notebooks[i % notebooks.size], title = "Note $i", document = NoteDocument(blocks = blocks)))
        }
        repeat(EVENTS) { i ->
            events.save(CalendarEvent(title = "Event $i", date = today.plusDays(random.nextLong(-365, 365)), startTime = LocalTime.of(9 + i % 8, 0)))
        }
    }

    private fun <T> timed(label: String, limitMs: Long, block: suspend () -> T): T = runBlocking {
        block() // warm up statement caches
        val (value, duration) = measureTimedValue { block() }
        println("LargeDataTest $label: ${duration.inWholeMilliseconds} ms")
        assertThat(duration.inWholeMilliseconds).isLessThan(limitMs)
        value
    }

    @Test
    fun hotQueries_stayFast() {
        val todayTasks = timed("today tasks", 1_000) { tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first() }
        assertThat(todayTasks).isNotEmpty()
        val all = timed("all tasks", 3_000) { tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today)).first() }
        assertThat(all.size).isEqualTo(TASKS)
        val month = timed("month events", 1_000) { events.occurrences(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1)) }
        assertThat(month).isNotEmpty()
        timed("recent notes", 500) { notes.observeRecent(6).first() }
        val hits = timed("search persian", 1_000) { search.search("گزارش") }
        assertThat(hits).isNotEmpty()
        assertThat(timed("search prefix", 1_000) { search.search("meet") }).isNotEmpty()
    }

    private companion object {
        const val TASKS = 5_000
        const val NOTES = 1_000
        const val EVENTS = 2_000
    }
}
