package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskValidationException
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {
    private lateinit var db: PlanBDatabase
    private lateinit var repo: OfflineTaskRepository
    private lateinit var projects: OfflineProjectRepository
    private lateinit var search: FtsSearchRepository
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private val today: LocalDate get() = time.today()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        repo = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        projects = OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time)
        search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
    }

    @After
    fun tearDown() = db.close()

    private fun filter(view: TaskView, sort: TaskSort = TaskSort.MANUAL) = TaskFilter(view = view, today = today, sort = sort)

    @Test
    fun save_persistsAndSchedulesReminder() = runTest {
        val id = repo.save(Task(title = "  Write report ", dueDate = today, reminderOffsetMinutes = 10))
        val stored = repo.getTask(id)!!
        assertThat(stored.title).isEqualTo("Write report")
        assertThat(stored.createdAt).isEqualTo(time.now())
        assertThat(reminders.synced).contains("task:$id")
    }

    @Test
    fun save_blankTitle_isRejected() = runTest {
        assertThrows(TaskValidationException::class.java) {
            kotlinx.coroutines.runBlocking { repo.save(Task(title = "   ")) }
        }
    }

    @Test
    fun views_filterCorrectly() = runTest {
        val inbox = repo.save(Task(title = "Inbox item"))
        val overdue = repo.save(Task(title = "Overdue", dueDate = today.minusDays(2)))
        val dueToday = repo.save(Task(title = "Today", dueDate = today))
        val later = repo.save(Task(title = "Later", dueDate = today.plusDays(3)))
        val done = repo.save(Task(title = "Done", dueDate = today))
        repo.setCompleted(done, true)
        val archived = repo.save(Task(title = "Archived"))
        repo.setArchived(listOf(archived), true)

        assertThat(repo.observeTasks(filter(TaskView.INBOX)).first().map { it.id }).containsExactly(inbox)
        assertThat(repo.observeTasks(filter(TaskView.TODAY)).first().map { it.id }).containsExactly(overdue, dueToday).inOrder()
        assertThat(repo.observeTasks(filter(TaskView.UPCOMING)).first().map { it.id }).containsExactly(later)
        assertThat(repo.observeTasks(filter(TaskView.COMPLETED)).first().map { it.id }).containsExactly(done)
        assertThat(repo.observeTasks(filter(TaskView.ARCHIVED)).first().map { it.id }).containsExactly(archived)
        assertThat(repo.observeTasks(filter(TaskView.SCHEDULED)).first().map { it.id }).containsExactly(overdue, dueToday, later)
    }

    @Test
    fun sortByPriority_highestFirst() = runTest {
        repo.save(Task(title = "low", priority = Priority.LOW))
        repo.save(Task(title = "high", priority = Priority.HIGH))
        repo.save(Task(title = "medium", priority = Priority.MEDIUM))
        val titles = repo.observeTasks(filter(TaskView.ALL, TaskSort.PRIORITY)).first().map { it.title }
        assertThat(titles).containsExactly("high", "medium", "low").inOrder()
    }

    @Test
    fun subtasks_countedOnParent_andCascadeOnDelete() = runTest {
        val parent = repo.save(Task(title = "Parent"))
        val a = repo.save(Task(title = "A", parentTaskId = parent))
        repo.save(Task(title = "B", parentTaskId = parent))
        repo.setCompleted(a, true)
        val loaded = repo.getTask(parent)!!
        assertThat(loaded.subtaskCount).isEqualTo(2)
        assertThat(loaded.completedSubtaskCount).isEqualTo(1)
        // top-level lists exclude subtasks
        assertThat(repo.observeTasks(filter(TaskView.ALL)).first().map { it.id }).containsExactly(parent)

        repo.delete(listOf(parent))
        assertThat(repo.observeSubtasks(parent).first()).isEmpty()
        assertThat(search.search("B")).isEmpty()
        assertThat(reminders.cancelled).contains("task:$a")
    }

    @Test
    fun completingRecurringTask_createsNextOccurrence() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY)
        val id = repo.save(Task(title = "Weekly sync", dueDate = today, recurrence = rule, tags = listOf(Tag(name = "work"))))
        val nextId = repo.setCompleted(id, true)!!
        val next = repo.getTask(nextId)!!
        assertThat(next.dueDate).isEqualTo(today.plusWeeks(1))
        assertThat(next.isCompleted).isFalse()
        assertThat(next.recurrence).isEqualTo(rule)
        assertThat(next.recurrenceAnchor).isEqualTo(today)
        assertThat(next.tags.map { it.name }).containsExactly("work")
        val completed = repo.getTask(id)!!
        assertThat(completed.isCompleted).isTrue()
        assertThat(completed.recurrence).isNull()
    }

    @Test
    fun recurringTaskWithCount_stopsAfterLastOccurrence() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, count = 2)
        val first = repo.save(Task(title = "Twice", dueDate = today, recurrence = rule))
        val second = repo.setCompleted(first, true)!!
        assertThat(repo.setCompleted(second, true)).isNull()
    }

    @Test
    fun monthlyJalaliRecurrence_movesToNextJalaliMonth() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, calendarSystem = CalendarSystem.JALALI)
        // 31 Shahrivar 1405 = 22 Sep 2026 → next is 30 Mehr 1405 = 22 Oct 2026
        val id = repo.save(Task(title = "Rent", dueDate = LocalDate.of(2026, 9, 22), recurrence = rule))
        val next = repo.getTask(repo.setCompleted(id, true)!!)!!
        assertThat(next.dueDate).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun uncomplete_clearsCompletedAt() = runTest {
        val id = repo.save(Task(title = "Toggle"))
        repo.setCompleted(id, true)
        assertThat(repo.getTask(id)!!.completedAt).isNotNull()
        repo.setCompleted(id, false)
        assertThat(repo.getTask(id)!!.completedAt).isNull()
    }

    @Test
    fun duplicate_copiesTagsAndSubtasks() = runTest {
        val id = repo.save(Task(title = "Original", tags = listOf(Tag(name = "home"))))
        repo.save(Task(title = "Child", parentTaskId = id))
        val copy = repo.getTask(repo.duplicate(id))!!
        assertThat(copy.title).isEqualTo("Original")
        assertThat(copy.tags.map { it.name }).containsExactly("home")
        assertThat(copy.subtaskCount).isEqualTo(1)
    }

    @Test
    fun reorder_updatesManualOrder() = runTest {
        val a = repo.save(Task(title = "a"))
        val b = repo.save(Task(title = "b"))
        val c = repo.save(Task(title = "c"))
        repo.reorder(listOf(c, a, b))
        assertThat(repo.observeTasks(filter(TaskView.ALL)).first().map { it.id }).containsExactly(c, a, b).inOrder()
    }

    @Test
    fun tags_areDeduplicatedCaseInsensitively_andFilterable() = runTest {
        val a = repo.save(Task(title = "a", tags = listOf(Tag(name = "Work"))))
        repo.save(Task(title = "b", tags = listOf(Tag(name = "work"))))
        repo.save(Task(title = "c"))
        val tags = repo.observeTags().first()
        assertThat(tags).hasSize(1)
        val byTag = repo.observeTasks(TaskFilter(view = TaskView.ALL, today = today, tagId = tags.single().id)).first()
        assertThat(byTag.map { it.title }).containsExactly("a", "b")
        assertThat(byTag.first { it.id == a }.tags.single().name).isEqualTo("Work")
    }

    @Test
    fun projectProgress_countsTopLevelTasks() = runTest {
        val projectId = projects.save(Project(title = "Launch"))
        val t1 = repo.save(Task(title = "t1", projectId = projectId))
        repo.save(Task(title = "t2", projectId = projectId))
        repo.save(Task(title = "sub", projectId = projectId, parentTaskId = t1))
        repo.setCompleted(t1, true)
        projects.saveMilestone(ProjectMilestone(projectId = projectId, title = "M1"))
        val summary = projects.observeProject(projectId).first()!!
        assertThat(summary.totalTasks).isEqualTo(2)
        assertThat(summary.completedTasks).isEqualTo(1)
        assertThat(summary.progress).isEqualTo(0.5f)
        assertThat(summary.totalMilestones).isEqualTo(1)
        // Deleting a project keeps its tasks (moved to no project)
        projects.delete(projectId)
        assertThat(repo.getTask(t1)!!.projectId).isNull()
    }

    @Test
    fun search_findsPersianWithArabicVariantsAndDigits() = runTest {
        val id = repo.save(Task(title = "خرید کتاب برای کلاس ۱۴"))
        val english = repo.save(Task(title = "Plan-B release notes"))
        assertThat(search.search("كتاب").map { it.id }).containsExactly(id)
        assertThat(search.search("کلاس 14").map { it.id }).containsExactly(id)
        assertThat(search.search("خری").map { it.id }).containsExactly(id)
        assertThat(search.search("plan").map { it.id }).containsExactly(english)
        assertThat(search.search("RELEASE").single().type).isEqualTo(SearchEntityType.TASK)
        assertThat(search.search("\"*)(")).isEmpty()
    }

    @Test
    fun save_doneOnRecurringTask_continuesTheSeries() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY)
        val id = repo.save(Task(title = "Weekly sync", dueDate = today, recurrence = rule, reminderOffsetMinutes = 5))
        // The editor's "Done" status chip saves the whole task with status DONE.
        repo.save(repo.getTask(id)!!.copy(title = "Weekly sync (edited)", status = TaskStatus.DONE))

        val completed = repo.getTask(id)!!
        assertThat(completed.isCompleted).isTrue()
        assertThat(completed.completedAt).isEqualTo(time.now())
        assertThat(completed.recurrence).isNull()
        val next = repo.observeTasks(filter(TaskView.ALL)).first().single()
        assertThat(next.id).isNotEqualTo(id)
        assertThat(next.title).isEqualTo("Weekly sync (edited)")
        assertThat(next.dueDate).isEqualTo(today.plusWeeks(1))
        assertThat(next.recurrence).isEqualTo(rule)
        assertThat(reminders.synced).contains("task:${next.id}")
    }

    @Test
    fun save_movingRecurringTask_reanchorsTheSeries() = runTest {
        val monday = LocalDate.of(2026, 10, 5)
        val wednesday = monday.plusDays(2)
        val id = repo.save(Task(title = "Gym", dueDate = monday, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY)))
        repo.save(repo.getTask(id)!!.copy(dueDate = wednesday))
        assertThat(repo.getTask(id)!!.recurrenceAnchor).isEqualTo(wednesday)
        val next = repo.getTask(repo.setCompleted(id, true)!!)!!
        assertThat(next.dueDate).isEqualTo(wednesday.plusWeeks(1))

        // Editing anything else (or only the end of the series) keeps the anchor.
        repo.save(next.copy(title = "Gym!", recurrence = next.recurrence!!.copy(count = 10)))
        assertThat(repo.getTask(next.id)!!.recurrenceAnchor).isEqualTo(wednesday)
    }

    @Test
    fun nextOccurrence_shiftsSubtaskDates_andSchedulesTheirReminders() = runTest {
        val parent = repo.save(Task(title = "Report", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY)))
        repo.save(Task(title = "Draft", parentTaskId = parent, dueDate = today.minusDays(1), startDate = today.minusDays(2), reminderOffsetMinutes = 0))
        val next = repo.setCompleted(parent, true)!!
        val sub = repo.observeSubtasks(next).first().single()
        assertThat(sub.dueDate).isEqualTo(today.minusDays(1).plusWeeks(1))
        assertThat(sub.startDate).isEqualTo(today.minusDays(2).plusWeeks(1))
        assertThat(sub.isCompleted).isFalse()
        assertThat(reminders.synced).contains("task:${sub.id}")
    }

    @Test
    fun duplicate_schedulesSubtaskReminders() = runTest {
        val id = repo.save(Task(title = "Original"))
        repo.save(Task(title = "Child", parentTaskId = id, dueDate = today.plusDays(1), reminderOffsetMinutes = 10))
        val copy = repo.duplicate(id)
        val childCopy = repo.observeSubtasks(copy).first().single()
        assertThat(reminders.synced).contains("task:${childCopy.id}")
    }

    @Test
    fun uncheckingRecurringOccurrence_takesTheSeriesBack() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY)
        val id = repo.save(Task(title = "Water plants", dueDate = today, recurrence = rule))
        repo.save(Task(title = "Fill can", parentTaskId = id))
        val next = repo.setCompleted(id, true)!!
        val nextSubtask = repo.observeSubtasks(next).first().single().id
        time.advance(Duration.ofMinutes(1))

        repo.setCompleted(id, false)

        assertThat(repo.getTask(next)).isNull()
        assertThat(repo.getTask(nextSubtask)).isNull()
        assertThat(reminders.cancelled).containsAtLeast("task:$next", "task:$nextSubtask")
        val reopened = repo.getTask(id)!!
        assertThat(reopened.isCompleted).isFalse()
        assertThat(reopened.recurrence).isEqualTo(rule)
        assertThat(reopened.recurrenceAnchor).isEqualTo(today)
        assertThat(repo.observeTasks(filter(TaskView.ALL)).first().map { it.id }).containsExactly(id)
        assertThat(search.search("Water").map { it.id }).containsExactly(id)
        // Completing again continues the series as before.
        assertThat(repo.getTask(repo.setCompleted(id, true)!!)!!.dueDate).isEqualTo(today.plusDays(1))
    }

    @Test
    fun uncheckingRecurringOccurrence_keepsANextOccurrenceTheUserChanged() = runTest {
        val id = repo.save(Task(title = "Stretch", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)))
        val next = repo.setCompleted(id, true)!!
        time.advance(Duration.ofMinutes(1))
        repo.save(repo.getTask(next)!!.copy(notes = "moved to the evening"))

        repo.setStatus(id, TaskStatus.TODO)

        assertThat(repo.getTask(next)).isNotNull()
        assertThat(repo.getTask(id)!!.isCompleted).isFalse()
        assertThat(repo.getTask(id)!!.recurrence).isNull()
    }

    @Test
    fun reorder_subset_keepsOtherTasksInPlace() = runTest {
        val ids = (1..5).map { repo.save(Task(title = "t$it")) }
        // A filtered view (e.g. one project) shows only t2, t4 and t5 and moves t5 first.
        repo.reorder(listOf(ids[4], ids[1], ids[3]))
        val all = repo.observeTasks(filter(TaskView.ALL)).first()
        assertThat(all.map { it.title }).containsExactly("t1", "t5", "t3", "t2", "t4").inOrder()
        assertThat(all.map { it.sortOrder }.toSet()).hasSize(5)
    }

    @Test
    fun archivingParent_archivesSubtasks_andStopsTheirReminders() = runTest {
        val parent = repo.save(Task(title = "Trip"))
        val sub = repo.save(Task(title = "Book hotel", parentTaskId = parent, dueDate = today.plusDays(3), reminderOffsetMinutes = 60))
        reminders.synced.clear()
        repo.setArchived(listOf(parent), true)
        assertThat(repo.getTask(sub)!!.archived).isTrue()
        assertThat(reminders.synced).containsAtLeast("task:$parent", "task:$sub")
        assertThat(repo.tasksWithReminders().map { it.id }).doesNotContain(sub)

        repo.setArchived(listOf(parent), false)
        assertThat(repo.getTask(sub)!!.archived).isFalse()
        assertThat(repo.tasksWithReminders().map { it.id }).contains(sub)
    }

    @Test
    fun completedCount_matchesTheTodayList() = runTest {
        val zone = time.zone()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val dueToday = repo.save(Task(title = "due today", dueDate = today))
        val overdue = repo.save(Task(title = "overdue", dueDate = today.minusDays(3)))
        val tomorrow = repo.save(Task(title = "tomorrow", dueDate = today.plusDays(1)))
        val noDate = repo.save(Task(title = "inbox"))
        val sub = repo.save(Task(title = "sub", parentTaskId = dueToday, dueDate = today))
        val archived = repo.save(Task(title = "archived", dueDate = today))
        listOf(dueToday, overdue, tomorrow, noDate, sub, archived).forEach { repo.setCompleted(it, true) }
        repo.setArchived(listOf(archived), true)

        assertThat(repo.observeCompletedCount(dayStart, dayEnd).first()).isEqualTo(2)
    }

    @Test
    fun addActualMinutes_onlyTouchesTrackedTime() = runTest {
        val id = repo.save(Task(title = "Focus target", actualMinutes = 5))
        repo.addActualMinutes(id, 25)
        assertThat(repo.getTask(id)!!.actualMinutes).isEqualTo(30)
        val other = repo.save(Task(title = "Untracked"))
        repo.addActualMinutes(other, 10)
        assertThat(repo.getTask(other)!!.actualMinutes).isEqualTo(10)
    }

    @Test
    fun search_reflectsEditsAndDeletes() = runTest {
        val id = repo.save(Task(title = "alpha"))
        repo.save(repo.getTask(id)!!.copy(title = "beta"))
        assertThat(search.search("alpha")).isEmpty()
        assertThat(search.search("beta").map { it.id }).containsExactly(id)
        repo.delete(listOf(id))
        assertThat(search.search("beta")).isEmpty()
    }
}
