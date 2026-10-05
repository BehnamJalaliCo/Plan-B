package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.EisenhowerQuadrant
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ViewModels of Plan-B Pro planning in Tasks: editor planning fields, smart lists, Eisenhower. */
@RunWith(RobolectricTestRunner::class)
class PlanningViewModelsTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun editor(form: TaskForm, taskId: Long = 0): TaskEditorViewModel {
        val handle = SavedStateHandle(
            mapOf(
                "task_form" to Json.encodeToString(TaskForm.serializer(), form),
                "task_form_original" to Json.encodeToString(TaskForm.serializer(), form.copy(extraReminders = emptyList(), blockedBy = emptyList())),
                "taskId" to taskId,
            ),
        )
        return main.track(TaskEditorViewModel(handle, graph.tasks, graph.projects, graph.settings, graph.time, graph.planning))
    }

    private suspend fun TaskEditorViewModel.saveAndAwait(): EditorEvent = coroutineScope {
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { events.first() } }
        save()
        event.await()
    }

    @Test
    fun editor_savesDeadlineRemindersNagAndBlockers() = runBlocking<Unit> {
        val blocker = graph.tasks.save(Task(title = "Get the keys"))
        val vm = editor(TaskForm(title = "Move in", dueDate = today.plusDays(1).toEpochDay()))
        vm.setDeadline(today.plusDays(3))
        vm.addReminder(ReminderForm(TaskReminderKind.OFFSET.name, offset = 30))
        vm.addReminder(ReminderForm(TaskReminderKind.DEADLINE.name, offset = 1440))
        vm.setNag(true, 15)
        vm.addBlocker(blocker)
        vm.form.awaitItem { it.blockedBy == listOf(blocker) }

        val saved = vm.saveAndAwait()

        val id = (saved as EditorEvent.Saved).id
        val task = graph.tasks.getTask(id)!!
        assertThat(task.deadline).isEqualTo(today.plusDays(3))
        assertThat(task.nag).isTrue()
        assertThat(task.openBlockerCount).isEqualTo(1)
        val planning = graph.planning.planning(id)
        assertThat(planning.reminders.map { it.kind }).containsExactly(TaskReminderKind.OFFSET, TaskReminderKind.DEADLINE).inOrder()
        assertThat(planning.nagIntervalMinutes).isEqualTo(15)
        assertThat(planning.blockedBy).containsExactly(blocker)
    }

    @Test
    fun editor_atMostFourExtraReminders() = runBlocking<Unit> {
        val vm = editor(TaskForm(title = "Many", dueDate = today.toEpochDay()))
        listOf(0, 5, 10, 15, 30).forEach { vm.addReminder(ReminderForm(TaskReminderKind.OFFSET.name, offset = it)) }
        val form = vm.form.awaitItem { it.extraReminders.size == 4 }
        assertThat(form.canAddReminder).isFalse()
    }

    @Test
    fun editor_rejectsADependencyCycle() = runBlocking<Unit> {
        val first = graph.tasks.save(Task(title = "First"))
        val second = graph.tasks.save(Task(title = "Second"))
        graph.planning.setDependencies(second, listOf(first))
        // Editing "First": waiting for "Second" would make them wait for each other.
        val vm = editor(TaskForm.from(graph.tasks.getTask(first)!!), taskId = first)
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.addBlocker(second)
        assertThat(event.await()).isEqualTo(EditorEvent.DependencyCycle)
        assertThat(vm.form.value.blockedBy).isEmpty()
    }

    @Test
    fun tasksScreen_showsASmartList_andGoesBackToViews() = runBlocking<Unit> {
        graph.tasks.save(Task(title = "Urgent", priority = Priority.HIGH))
        graph.tasks.save(Task(title = "Calm", priority = Priority.LOW, dueDate = today))
        val listId = graph.smartLists.save(SavedFilter(name = "High", filter = SmartFilter(priorities = setOf(Priority.HIGH))))
        val vm = main.track(
            TasksViewModel(SavedStateHandle(mapOf("tasks_view" to TaskView.TODAY.name)), graph.tasks, graph.projects, graph.settings, graph.time, main.scope, graph.smartLists),
        )
        main.keepCollecting(vm.uiState)
        vm.uiState.awaitItem { !it.loading && it.smartLists.size == 1 && it.tasks.map { t -> t.title } == listOf("Calm") }
        vm.setSmartList(listId)
        val state = vm.uiState.awaitItem { it.activeSmartList?.id == listId && it.tasks.map { t -> t.title } == listOf("Urgent") }
        assertThat(state.canReorder).isFalse()
        vm.setView(TaskView.TODAY)
        vm.uiState.awaitItem { it.activeSmartList == null && it.tasks.map { t -> t.title } == listOf("Calm") }
    }

    @Test
    fun smartListEditor_savesTheFilter() = runBlocking<Unit> {
        val handle = SavedStateHandle(mapOf("filterId" to 0L))
        val vm = main.track(SmartListEditorViewModel(handle, graph.smartLists, graph.projects, graph.tasks, graph.time))
        vm.form.awaitItem { it.name.isEmpty() }
        vm.update { it.copy(name = "This week", dateRange = "NEXT_7_DAYS", priorities = listOf("HIGH"), hasDeadline = true, sort = "DEADLINE") }
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save()
        val id = (event.await() as SmartListEvent.Saved).id
        val saved = graph.smartLists.getList(id)!!
        assertThat(saved.name).isEqualTo("This week")
        assertThat(saved.filter).isEqualTo(
            SmartFilter(
                priorities = setOf(Priority.HIGH),
                dateRange = com.behnamjalali.planb.core.model.SmartDateRange.NEXT_7_DAYS,
                hasDeadline = true,
                sort = com.behnamjalali.planb.core.model.TaskSort.DEADLINE,
            ),
        )
    }

    @Test
    fun eisenhower_groupsTasks_andMovesThem() = runBlocking<Unit> {
        val doNow = graph.tasks.save(Task(title = "Do", priority = Priority.HIGH, dueDate = today))
        val later = graph.tasks.save(Task(title = "Later", priority = Priority.NONE, dueDate = today.plusDays(20)))
        val vm = main.track(EisenhowerViewModel(SavedStateHandle(), graph.tasks, graph.time))
        main.keepCollecting(vm.uiState)
        val state = vm.uiState.awaitItem { !it.loading && it.quadrants.values.sumOf { q -> q.size } == 2 }
        assertThat(state.quadrants.getValue(EisenhowerQuadrant.DO).map { it.id }).containsExactly(doNow)
        assertThat(state.quadrants.getValue(EisenhowerQuadrant.ELIMINATE).map { it.id }).containsExactly(later)

        vm.move(later, EisenhowerQuadrant.DO)
        vm.uiState.awaitItem { it.quadrants[EisenhowerQuadrant.DO].orEmpty().size == 2 }
        val moved = graph.tasks.getTask(later)!!
        assertThat(moved.priority).isEqualTo(Priority.HIGH)
        assertThat(moved.dueDate).isEqualTo(today)

        // A wider urgency window moves nothing but the boundary.
        vm.setThreshold(7)
        vm.uiState.awaitItem { it.thresholdDays == 7 }
    }
}
