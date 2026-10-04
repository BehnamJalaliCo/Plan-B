package com.behnamjalali.planb.core.data

import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.repository.EventValidationException
import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineEventRepository
import com.behnamjalali.planb.core.data.repository.OfflineFocusRepository
import com.behnamjalali.planb.core.data.repository.OfflineGoalRepository
import com.behnamjalali.planb.core.data.repository.OfflineHabitRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineReviewRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.OfflineTemplateRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.NotebookSection
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtherRepositoriesTest {
    private lateinit var db: PlanBDatabase
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var projects: OfflineProjectRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var habits: OfflineHabitRepository
    private lateinit var goals: OfflineGoalRepository
    private lateinit var events: OfflineEventRepository
    private lateinit var focus: OfflineFocusRepository
    private lateinit var search: FtsSearchRepository

    @Before
    fun setUp() {
        db = TestDatabase.create()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        projects = OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time)
        habits = OfflineHabitRepository(db, db.habitDao(), db.searchDao(), time, reminders)
        goals = OfflineGoalRepository(db, db.goalDao(), db.searchDao(), time)
        events = OfflineEventRepository(db, db.eventDao(), db.searchDao(), time, reminders)
        focus = OfflineFocusRepository(db, db.focusDao(), time)
        search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun notes_hierarchy_moveDuplicateArchive() = runTest {
        val nb1 = notes.saveNotebook(Notebook(title = "Work"))
        val nb2 = notes.saveNotebook(Notebook(title = "Personal"))
        val section = notes.saveSection(NotebookSection(notebookId = nb1, title = "Meetings"))
        val doc = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.HEADING, "سلام دنیا"), NoteBlock("2", BlockType.CHECKLIST, "Buy milk")))
        val noteId = notes.saveNote(Note(notebookId = nb1, sectionId = section, title = "Mixed یادداشت", document = doc))

        assertThat(notes.observeNotes(nb1, section).first().map { it.id }).containsExactly(noteId)
        assertThat(notes.observeNotebooks().first().first { it.id == nb1 }.noteCount).isEqualTo(1)
        assertThat(notes.getNote(noteId)!!.document).isEqualTo(doc)

        val copy = notes.duplicateNote(noteId, "(copy)")
        assertThat(notes.getNote(copy)!!.title).isEqualTo("Mixed یادداشت (copy)")

        notes.moveSection(section, nb2)
        assertThat(notes.getNote(noteId)!!.notebookId).isEqualTo(nb2)

        notes.setArchived(noteId, true)
        assertThat(notes.observeNotes(nb2).first().map { it.id }).doesNotContain(noteId)
        assertThat(notes.observeArchivedNotes().first().map { it.id }).contains(noteId)

        assertThat(search.search("دنیا").map { it.id }).contains(noteId)
        notes.deleteNotebook(nb2)
        assertThat(notes.getNote(noteId)).isNull()
        assertThat(search.search("دنیا")).isEmpty()
    }

    @Test
    fun notes_updateContent_isAtomicAndIndexed() = runTest {
        val nb = notes.ensureDefaultNotebook("Default")
        assertThat(notes.ensureDefaultNotebook("Other")).isEqualTo(nb)
        val id = notes.saveNote(Note(notebookId = nb, title = "Draft"))
        time.advance(Duration.ofMinutes(1))
        notes.updateContent(id, "Final", NoteDocument(blocks = listOf(NoteBlock("a", text = "autosaved text"))))
        val note = notes.getNote(id)!!
        assertThat(note.title).isEqualTo("Final")
        assertThat(note.updatedAt).isEqualTo(time.now())
        assertThat(search.search("autosaved").map { it.id }).containsExactly(id)
    }

    @Test
    fun drafts_recoveredUntilCommitted() = runTest {
        val nb = notes.ensureDefaultNotebook("Default")
        val id = notes.saveNote(Note(notebookId = nb, title = "Saved"))
        val draftDoc = NoteDocument(blocks = listOf(NoteBlock("d", text = "unsaved")))
        notes.saveDraft(id, "Unsaved title", draftDoc)
        assertThat(notes.getDraft(id)!!.document).isEqualTo(draftDoc)
        time.advance(Duration.ofSeconds(1))
        notes.updateContent(id, "Unsaved title", draftDoc)
        assertThat(notes.getDraft(id)).isNull()
        // A draft newer than the commit is kept
        time.advance(Duration.ofSeconds(1))
        notes.saveDraft(id, "newer", draftDoc)
        notes.deleteNote(id)
        assertThat(notes.getDraft(id)).isNull()
    }

    @Test
    fun habits_checkIn_accumulatesAndUndo() = runTest {
        val today = time.today()
        val id = habits.save(Habit(title = "Water", target = 3, startDate = today.minusDays(5)))
        habits.checkIn(id, today)
        habits.checkIn(id, today, 2)
        var history = habits.observeHabits(today.minusDays(7), today).first().single()
        assertThat(history.amounts[today]).isEqualTo(3)
        habits.checkIn(id, today, -3)
        history = habits.observeHabits(today.minusDays(7), today).first().single()
        assertThat(history.amounts).isEmpty()
        assertThat(reminders.synced).contains("habit:$id")
    }

    @Test
    fun goals_progressAndValidation() = runTest {
        val id = goals.save(Goal(title = "Read books", target = 24.0, unit = "books"))
        goals.updateProgress(id, 6.0)
        assertThat(goals.getGoal(id)!!.progress).isEqualTo(0.25f)
        goals.updateProgress(id, -5.0)
        assertThat(goals.getGoal(id)!!.currentValue).isEqualTo(0.0)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { goals.save(Goal(title = "x", target = 0.0)) } }
    }

    @Test
    fun events_recurringExpansionAndValidation() = runTest {
        val start = LocalDate.of(2026, 10, 1)
        val weekly = events.save(
            CalendarEvent(
                title = "Yoga", date = start, startTime = LocalTime.of(18, 0), endTime = LocalTime.of(19, 0), allDay = false,
                recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY),
            ),
        )
        val single = events.save(CalendarEvent(title = "Dentist", date = LocalDate.of(2026, 10, 10)))
        val occurrences = events.occurrences(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertThat(occurrences.filter { it.event.id == weekly }.map { it.date })
            .containsExactly(start, start.plusWeeks(1), start.plusWeeks(2), start.plusWeeks(3), start.plusWeeks(4))
        assertThat(occurrences.single { it.event.id == single }.event.allDay).isTrue()
        assertThrows(EventValidationException::class.java) {
            runBlocking {
                events.save(CalendarEvent(title = "Bad", date = start, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(9, 0), allDay = false))
            }
        }
        events.delete(weekly)
        assertThat(reminders.cancelled).contains("event:$weekly")
    }

    @Test
    fun focus_timerUsesTimestamps_pauseResumeFinish() = runTest {
        val planned = Duration.ofMinutes(25).toMillis()
        val session = focus.start(planned, null)
        assertThat(session.status).isEqualTo(FocusStatus.RUNNING)
        time.advance(Duration.ofMinutes(10))
        val paused = focus.pause()!!
        assertThat(paused.accumulatedMillis).isEqualTo(Duration.ofMinutes(10).toMillis())
        time.advance(Duration.ofMinutes(30)) // paused time does not count
        val resumed = focus.resume()!!
        assertThat(resumed.elapsedMillis(time.now())).isEqualTo(Duration.ofMinutes(10).toMillis())
        time.advance(Duration.ofMinutes(5))
        assertThat(focus.completeIfElapsed()!!.status).isEqualTo(FocusStatus.RUNNING)
        time.advance(Duration.ofMinutes(10))
        val done = focus.completeIfElapsed()!!
        assertThat(done.status).isEqualTo(FocusStatus.COMPLETED)
        assertThat(done.actualDurationMillis).isEqualTo(planned)
        assertThat(focus.observeActive().first()).isNull()
        assertThat(focus.observeHistory().first()).hasSize(1)
    }

    @Test
    fun focus_startingNewSessionCancelsPrevious() = runTest {
        focus.start(60_000, null)
        time.advance(Duration.ofSeconds(20))
        focus.start(60_000, null)
        val history = focus.observeHistory().first()
        assertThat(history.single().status).isEqualTo(FocusStatus.CANCELLED)
        assertThat(history.single().actualDurationMillis).isEqualTo(20_000)
    }

    @Test
    fun templates_builtInsAreStructured_andApplyCreatesRecords() = runTest {
        val templates = OfflineTemplateRepository(
            ApplicationProvider.getApplicationContext(), db.templateDao(), tasks, projects, notes, habits, time,
        )
        val builtIns = templates.builtInTemplates()
        assertThat(builtIns.map { it.builtInKey }).containsExactly(
            "daily_planner", "weekly_planner", "monthly_planner", "meeting_note",
            "project_plan", "study_plan", "personal_journal", "habit_plan",
        ).inOrder()

        val daily = builtIns.first { it.builtInKey == "daily_planner" }
        val noteResult = templates.apply(daily, "12 Mehr", "Planner")
        val note = notes.getNote(noteResult.id)!!
        assertThat(note.title).contains("12 Mehr")
        assertThat(note.document.blocks.map { it.type }).contains(BlockType.CHECKLIST)
        assertThat(note.document.blocks.map { it.id }.toSet()).hasSize(note.document.blocks.size)

        val projectResult = templates.apply(builtIns.first { it.builtInKey == "project_plan" }, "today", "Planner")
        assertThat(projectResult.type).isEqualTo(TemplateType.PROJECT)
        val summary = projects.observeProject(projectResult.id).first()!!
        assertThat(summary.totalTasks).isEqualTo(4)
        assertThat(summary.totalMilestones).isEqualTo(3)

        val habitResult = templates.apply(builtIns.first { it.builtInKey == "habit_plan" }, "", "Planner")
        assertThat(habitResult.createdCount).isEqualTo(4)

        val customId = templates.saveNoteAsTemplate(note)
        val all = templates.observeTemplates().first()
        assertThat(all.last().id).isEqualTo(customId)
        assertThat(all.last().builtIn).isFalse()
    }

    @Test
    fun weeklyReview_aggregatesWeek() = runTest {
        val weekStart = LocalDate.of(2026, 10, 3) // Saturday
        time.setLocal(weekStart.plusDays(2))
        val done = tasks.save(Task(title = "Finished", dueDate = weekStart))
        tasks.setCompleted(done, true)
        tasks.save(Task(title = "Missed", dueDate = weekStart.plusDays(1)))
        tasks.save(Task(title = "Next week", dueDate = weekStart.plusDays(8)))
        val habitId = habits.save(Habit(title = "Walk", startDate = weekStart))
        habits.checkIn(habitId, weekStart)
        val review = OfflineReviewRepository(db.taskDao(), db.habitDao(), db.focusDao(), db.noteDao(), db.goalDao(), projects, time)
            .weeklyReview(weekStart)
        assertThat(review.completedTasks).isEqualTo(1)
        assertThat(review.completedPerDay[2]).isEqualTo(1)
        assertThat(review.missedTasks.map { it.title }).containsExactly("Missed")
        assertThat(review.nextWeekPriorities.map { it.title }).containsExactly("Next week")
        // 1 of the 2 finished days done; today is still open, so it is not counted as missed yet.
        assertThat(review.habits.single().rate).isWithin(0.01f).of(1f / 2f)
        // Once today is done it counts: 2 of 3 days.
        habits.checkIn(habitId, weekStart.plusDays(2))
        val updated = OfflineReviewRepository(db.taskDao(), db.habitDao(), db.focusDao(), db.noteDao(), db.goalDao(), projects, time)
            .weeklyReview(weekStart)
        assertThat(updated.habits.single().rate).isWithin(0.01f).of(2f / 3f)
    }
}
