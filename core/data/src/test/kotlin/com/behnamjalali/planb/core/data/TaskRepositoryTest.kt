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
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
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
    fun search_reflectsEditsAndDeletes() = runTest {
        val id = repo.save(Task(title = "alpha"))
        repo.save(repo.getTask(id)!!.copy(title = "beta"))
        assertThat(search.search("alpha")).isEmpty()
        assertThat(search.search("beta").map { it.id }).containsExactly(id)
        repo.delete(listOf(id))
        assertThat(search.search("beta")).isEmpty()
    }
}
