package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.OfflineFocusRepository
import com.behnamjalali.planb.core.data.repository.OfflineHabitRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineStatisticsRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatisticsRepositoryTest {
    private lateinit var db: PlanBDatabase
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var projects: OfflineProjectRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var habits: OfflineHabitRepository
    private lateinit var focus: OfflineFocusRepository
    private lateinit var stats: OfflineStatisticsRepository

    private val weekStart = LocalDate.of(2026, 10, 3) // Saturday
    private val week = DateSpan(weekStart, weekStart.plusDays(6))
    private val days = week.dates().map { DateSpan(it, it) }.toList()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        projects = OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time)
        habits = OfflineHabitRepository(db, db.habitDao(), db.searchDao(), time, reminders)
        focus = OfflineFocusRepository(db, db.focusDao(), time)
        stats = OfflineStatisticsRepository(db.statisticsDao(), db.habitDao(), projects, time)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aggregatesTasksFocusHabitsNotesAndTags() = runTest {
        val project = projects.save(Project(title = "Launch"))
        val tag = tasks.upsertTag(Tag(name = "work"))
        // Monday 09:00 local: complete an on-time task with a tag and a project.
        time.setLocal(weekStart.plusDays(2), LocalTime.of(9, 0))
        val onTime = tasks.save(Task(title = "On time", dueDate = weekStart.plusDays(2), projectId = project, tags = listOf(Tag(id = tag, name = "work"))))
        tasks.setCompleted(onTime, true)
        val late = tasks.save(Task(title = "Late", dueDate = weekStart))
        tasks.setCompleted(late, true)
        tasks.save(Task(title = "Open", dueDate = weekStart.plusDays(1)))
        // A completed task in the trash never counts.
        val trashed = tasks.save(Task(title = "Trashed"))
        tasks.setCompleted(trashed, true)
        db.taskDao().setDeletedAt(listOf(trashed), time.now().toEpochMilli(), time.now().toEpochMilli())

        focus.start(Duration.ofMinutes(25).toMillis(), null)
        time.advance(Duration.ofMinutes(25))
        focus.finish()

        val habit = habits.save(Habit(title = "Walk", startDate = weekStart))
        habits.checkIn(habit, weekStart)
        habits.checkIn(habit, weekStart.plusDays(1))

        val notebook = notes.saveNotebook(Notebook(title = "Journal"))
        notes.saveNote(Note(notebookId = notebook, title = "Idea"))

        val result = stats.statistics(week, days, DayOfWeek.SATURDAY)
        assertThat(result.completedTotal).isEqualTo(2)
        assertThat(result.completedPerBucket[2]).isEqualTo(2)
        assertThat(result.onTime).isEqualTo(1)
        assertThat(result.late).isEqualTo(1)
        assertThat(result.busiestWeekday).isEqualTo(DayOfWeek.MONDAY)
        assertThat(result.busiestHour).isEqualTo(9)
        // Planned Saturday..Monday: 3 tasks, 2 done.
        assertThat(result.plannedTotal).isEqualTo(3)
        assertThat(result.completionRate).isWithin(0.001f).of(2f / 3f)
        assertThat(result.focusMinutes).isEqualTo(25)
        assertThat(result.notesWritten).isEqualTo(1)
        assertThat(result.topTags.single().tag.name).isEqualTo("work")
        assertThat(result.topProjects.single().project.title).isEqualTo("Launch")
        assertThat(result.habits.single().doneDays).isEqualTo(2)
    }
}
