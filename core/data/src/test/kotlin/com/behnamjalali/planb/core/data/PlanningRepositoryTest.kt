package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.DependencyCycleException
import com.behnamjalali.planb.core.data.repository.OfflineSmartListRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskPlanningRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskValidationException
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro planning in the data layer: #4 spawning, #10 smart lists, #11 deadlines, #12 reminders, #14 dependencies. */
@RunWith(RobolectricTestRunner::class)
class PlanningRepositoryTest {
    private lateinit var db: PlanBDatabase
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var planning: OfflineTaskPlanningRepository
    private lateinit var smartLists: OfflineSmartListRepository
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private val today get() = time.today()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders)
        planning = OfflineTaskPlanningRepository(db, reminders)
        smartLists = OfflineSmartListRepository(db, db.taskDao(), time)
    }

    @After
    fun tearDown() = db.close()

    // region #4 recurrence

    @Test
    fun afterCompletion_nextOccurrenceCountsFromCompletionDay_andCountRunsOut() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3, basis = RecurrenceBasis.COMPLETION, count = 2)
        val id = tasks.save(Task(title = "Water plants", dueDate = today.minusDays(5), recurrence = rule))
        // Done today, five days late: the next one is three days after today, not after the plan.
        val next = tasks.setCompleted(id, true)!!
        val nextTask = tasks.getTask(next)!!
        assertThat(nextTask.dueDate).isEqualTo(today.plusDays(3))
        assertThat(nextTask.recurrence!!.count).isEqualTo(1)
        // Undo gives the count back to the reopened occurrence.
        tasks.setCompleted(id, false)
        assertThat(tasks.getTask(next)).isNull()
        assertThat(tasks.getTask(id)!!.recurrence!!.count).isEqualTo(2)

        val again = tasks.setCompleted(id, true)!!
        time.advance(Duration.ofDays(1))
        // The last occurrence ends the series.
        assertThat(tasks.setCompleted(again, true)).isNull()
    }

    @Test
    fun secondMonday_spawnsInTheNextMonth() = runTest {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2)
        val id = tasks.save(Task(title = "Team review", dueDate = java.time.LocalDate.of(2026, 10, 12), recurrence = rule))
        val next = tasks.setCompleted(id, true)!!
        assertThat(tasks.getTask(next)!!.dueDate).isEqualTo(java.time.LocalDate.of(2026, 11, 9))
    }

    // endregion

    // region #11 deadlines

    @Test
    fun today_showsNearDeadlines_andSortsByDeadline() = runTest {
        tasks.save(Task(title = "planned", dueDate = today))
        tasks.save(Task(title = "deadline soon", dueDate = today.plusDays(10), deadline = today.plusDays(2)))
        tasks.save(Task(title = "deadline only", deadline = today.plusDays(3)))
        tasks.save(Task(title = "deadline far", dueDate = today.plusDays(10), deadline = today.plusDays(4)))
        val todayList = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first().map { it.title }
        assertThat(todayList).containsExactly("planned", "deadline soon", "deadline only")
        val byDeadline = tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today, sort = TaskSort.DEADLINE)).first().map { it.title }
        assertThat(byDeadline).containsExactly("deadline soon", "deadline only", "deadline far", "planned").inOrder()
        // Completing a task that is only on Today for its deadline counts as done today.
        val id = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first().first { it.title == "deadline only" }.id
        tasks.setCompleted(id, true)
        val zone = time.zone()
        assertThat(tasks.observeCompletedCount(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()).first())
            .isEqualTo(1)
    }

    // endregion

    // region #12 reminders

    @Test
    fun extraReminders_limitNagRowAndRescheduling() = runTest {
        val id = tasks.save(Task(title = "Pay rent", dueDate = today.plusDays(1), deadline = today.plusDays(2), nag = true))
        reminders.synced.clear()
        val extras = listOf(
            TaskReminder(kind = TaskReminderKind.OFFSET, offsetMinutes = 60),
            TaskReminder(kind = TaskReminderKind.DEADLINE, offsetMinutes = 1440),
            TaskReminder(kind = TaskReminderKind.ABSOLUTE, at = time.now().plus(Duration.ofHours(3))),
        )
        planning.setReminders(id, extras, nagIntervalMinutes = 15)
        assertThat(reminders.synced).containsExactly("task:$id")
        val stored = planning.planning(id)
        assertThat(stored.reminders.map { it.kind }).containsExactly(TaskReminderKind.OFFSET, TaskReminderKind.DEADLINE, TaskReminderKind.ABSOLUTE).inOrder()
        assertThat(stored.nagIntervalMinutes).isEqualTo(15)
        assertThat(db.taskReminderDao().forTask(id).map { it.kind }).contains("NAG")
        // The default interval needs no row.
        planning.setReminders(id, extras, nagIntervalMinutes = 10)
        assertThat(db.taskReminderDao().forTask(id).map { it.kind }).doesNotContain("NAG")
        // Five extras (six reminders in all) are refused and nothing changes.
        assertThrows(TaskValidationException::class.java) {
            kotlinx.coroutines.runBlocking { planning.setReminders(id, extras + extras, 10) }
        }
        assertThat(planning.planning(id).reminders).hasSize(3)
        // A task with only extra reminders is rescheduled after a reboot.
        val extrasOnly = tasks.save(Task(title = "No primary"))
        planning.setReminders(extrasOnly, listOf(TaskReminder(kind = TaskReminderKind.ABSOLUTE, at = time.now().plusSeconds(60))), 10)
        assertThat(tasks.tasksWithReminders().map { it.id }).containsExactly(id, extrasOnly)
    }

    @Test
    fun recurringTask_carriesRelativeRemindersAndNagInterval() = runTest {
        val id = tasks.save(Task(title = "Report", dueDate = today, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY), nag = true))
        planning.setReminders(
            id,
            listOf(
                TaskReminder(kind = TaskReminderKind.DEADLINE, offsetMinutes = 120),
                TaskReminder(kind = TaskReminderKind.ABSOLUTE, at = time.now().plus(Duration.ofHours(1))),
            ),
            nagIntervalMinutes = 30,
        )
        val next = tasks.setCompleted(id, true)!!
        val copied = planning.planning(next)
        assertThat(copied.reminders.map { it.kind }).containsExactly(TaskReminderKind.DEADLINE)
        assertThat(copied.nagIntervalMinutes).isEqualTo(30)
        assertThat(tasks.getTask(next)!!.nag).isTrue()
    }

    // endregion

    // region #14 dependencies

    @Test
    fun dependencies_blockUntilBlockersAreDone_andRejectCycles() = runTest {
        val design = tasks.save(Task(title = "Design"))
        val build = tasks.save(Task(title = "Build"))
        val ship = tasks.save(Task(title = "Ship"))
        planning.setDependencies(build, listOf(design))
        planning.setDependencies(ship, listOf(build))

        fun blockers() = kotlinx.coroutines.runBlocking {
            tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today)).first().associate { it.title to it.openBlockerCount }
        }
        assertThat(blockers()).containsExactly("Design", 0, "Build", 1, "Ship", 1)
        assertThat(tasks.getTask(ship)!!.isBlocked).isTrue()

        // Design waiting for Ship would close the circle Design → Build → Ship → Design.
        assertThat(planning.wouldCreateCycle(design, ship)).isTrue()
        val error = assertThrows(DependencyCycleException::class.java) {
            kotlinx.coroutines.runBlocking { planning.setDependencies(design, listOf(ship)) }
        }
        assertThat(error.blockerId).isEqualTo(ship)
        assertThat(planning.planning(design).blockedBy).isEmpty()
        assertThrows(DependencyCycleException::class.java) {
            kotlinx.coroutines.runBlocking { planning.setDependencies(design, listOf(design)) }
        }

        // Completing the blocker unblocks the next task.
        tasks.setCompleted(design, true)
        assertThat(blockers()).containsExactly("Build", 0, "Ship", 1)
        assertThat(planning.observeDependencies().first()).containsExactly(build, listOf(design), ship, listOf(build))
        // A deleted blocker no longer blocks.
        tasks.deletePermanently(listOf(build))
        assertThat(tasks.getTask(ship)!!.openBlockerCount).isEqualTo(0)
        assertThat(planning.planning(ship).blockedBy).isEmpty()
    }

    // endregion

    // region #10 smart lists

    @Test
    fun smartLists_crudAndOrder() = runTest {
        val a = smartLists.save(SavedFilter(name = " Urgent ", icon = PlannerIcon.STAR, color = AccentColor.ROSE, filter = SmartFilter(priorities = setOf(Priority.HIGH))))
        val b = smartLists.save(SavedFilter(name = "Home", filter = SmartFilter(noProject = true)))
        assertThat(smartLists.observeLists().first().map { it.name }).containsExactly("Urgent", "Home").inOrder()
        smartLists.reorder(listOf(b, a))
        assertThat(smartLists.observeLists().first().map { it.id }).containsExactly(b, a).inOrder()
        smartLists.save(smartLists.getList(a)!!.copy(name = "Very urgent"))
        assertThat(smartLists.getList(a)!!.name).isEqualTo("Very urgent")
        assertThat(smartLists.getList(a)!!.filter.priorities).containsExactly(Priority.HIGH)
        assertThrows(TaskValidationException::class.java) { kotlinx.coroutines.runBlocking { smartLists.save(SavedFilter(name = " ")) } }
        smartLists.delete(b)
        assertThat(smartLists.observeLists().first().map { it.id }).containsExactly(a)
    }

    @Test
    fun smartList_tasksUpdateWithData() = runTest {
        tasks.save(Task(title = "High", priority = Priority.HIGH, dueDate = today.plusDays(1)))
        tasks.save(Task(title = "Low", priority = Priority.LOW))
        val done = tasks.save(Task(title = "Done high", priority = Priority.HIGH))
        tasks.setCompleted(done, true)
        val filter = SmartFilter(priorities = setOf(Priority.HIGH))
        assertThat(smartLists.observeTasks(filter, today).first().map { it.title }).containsExactly("High")
        assertThat(smartLists.observeTasks(filter.copy(statuses = setOf(com.behnamjalali.planb.core.model.TaskStatus.DONE)), today).first().map { it.title })
            .containsExactly("Done high")
    }

    // endregion
}
