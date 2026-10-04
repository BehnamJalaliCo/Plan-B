package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TasksViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    private var overdue = 0L
    private var dueToday = 0L
    private var upcoming = 0L
    private var inbox = 0L
    private var done = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        graph = TestDataGraph()
        overdue = graph.tasks.save(Task(title = "Overdue", dueDate = today.minusDays(1)))
        dueToday = graph.tasks.save(Task(title = "Due today", dueDate = today))
        upcoming = graph.tasks.save(Task(title = "Next week", dueDate = today.plusDays(3)))
        inbox = graph.tasks.save(Task(title = "Someday"))
        done = graph.tasks.save(Task(title = "Already done", dueDate = today))
        graph.tasks.setCompleted(done, true)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    /** A handle with a saved view skips the async default-view lookup from settings. */
    private fun viewModel(view: TaskView? = TaskView.TODAY): TasksViewModel {
        val handle = if (view == null) SavedStateHandle() else SavedStateHandle(mapOf("tasks_view" to view.name))
        return main.track(TasksViewModel(handle, graph.tasks, graph.projects, graph.settings, graph.time, main.scope)).also {
            main.keepCollecting(it.uiState)
        }
    }

    private suspend fun TasksViewModel.awaitTitles(view: TaskView, vararg titles: String): TasksUiState =
        uiState.awaitItem { !it.loading && it.filter.view == view && it.tasks.map { t -> t.title } == titles.toList() }

    @Test
    fun defaultView_comesFromSettings() = runBlocking<Unit> {
        graph.settings.update { it.copy(defaultTaskView = TaskView.UPCOMING) }
        val vm = viewModel(view = null)
        val state = vm.awaitTitles(TaskView.UPCOMING, "Next week")
        assertThat(state.filter.view).isEqualTo(TaskView.UPCOMING)
    }

    @Test
    fun switchingViews_filtersTasks() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitTitles(TaskView.TODAY, "Overdue", "Due today")

        vm.setView(TaskView.UPCOMING)
        vm.awaitTitles(TaskView.UPCOMING, "Next week")

        vm.setView(TaskView.COMPLETED)
        val completed = vm.awaitTitles(TaskView.COMPLETED, "Already done")
        assertThat(completed.tasks.single().isCompleted).isTrue()

        vm.setView(TaskView.INBOX)
        vm.awaitTitles(TaskView.INBOX, "Someday")
    }

    @Test
    fun completingTask_movesItToCompleted() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitTitles(TaskView.TODAY, "Overdue", "Due today")

        val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.messages.first() } }
        vm.setCompleted(dueToday, true)
        assertThat(message.await()).isEqualTo(TasksMessage.Completed(dueToday, null))
        vm.awaitTitles(TaskView.TODAY, "Overdue")

        vm.setView(TaskView.COMPLETED)
        val completed = vm.uiState.awaitItem { it.filter.view == TaskView.COMPLETED && it.tasks.size == 2 }
        assertThat(completed.tasks.map { it.id }).containsExactly(dueToday, done)

        // Reopening brings it back to Today.
        vm.setCompleted(dueToday, false)
        vm.awaitTitles(TaskView.COMPLETED, "Already done")
        vm.setView(TaskView.TODAY)
        vm.awaitTitles(TaskView.TODAY, "Overdue", "Due today")
    }

    @Test
    fun multiSelect_bulkCompleteCompletesAllSelected() = runBlocking<Unit> {
        val vm = viewModel(TaskView.ALL)
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week", "Someday")

        vm.toggleSelection(overdue)
        vm.toggleSelection(upcoming)
        vm.toggleSelection(inbox)
        vm.toggleSelection(inbox) // deselect again
        val selecting = vm.uiState.awaitItem { it.selection == setOf(overdue, upcoming) }
        assertThat(selecting.selecting).isTrue()

        vm.completeSelected(true)
        val after = vm.awaitTitles(TaskView.ALL, "Due today", "Someday")
        assertThat(after.selection).isEmpty()
        assertThat(graph.tasks.getTask(overdue)!!.isCompleted).isTrue()
        assertThat(graph.tasks.getTask(upcoming)!!.isCompleted).isTrue()
        assertThat(graph.tasks.getTask(inbox)!!.isCompleted).isFalse()

        vm.setView(TaskView.COMPLETED)
        val completed = vm.uiState.awaitItem { it.filter.view == TaskView.COMPLETED && it.tasks.size == 3 }
        assertThat(completed.tasks.map { it.id }).containsExactly(overdue, upcoming, done)
    }

    @Test
    fun selectAll_thenSwitchingView_clearsSelection() = runBlocking<Unit> {
        val vm = viewModel()
        vm.awaitTitles(TaskView.TODAY, "Overdue", "Due today")
        vm.selectAll()
        vm.uiState.awaitItem { it.selection == setOf(overdue, dueToday) }

        vm.setView(TaskView.UPCOMING)
        val state = vm.awaitTitles(TaskView.UPCOMING, "Next week")
        assertThat(state.selection).isEmpty()
    }

    @Test
    fun query_filtersVisibleTasks_andDeleteCanBeUndone() = runBlocking<Unit> {
        val vm = viewModel(TaskView.ALL)
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week", "Someday")

        vm.setQuery("next")
        vm.awaitTitles(TaskView.ALL, "Next week")
        vm.setQuery("")
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week", "Someday")

        vm.requestDelete(listOf(inbox))
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week")
        vm.undoDelete()
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week", "Someday")

        vm.requestDelete(listOf(inbox))
        vm.commitDelete()
        withTimeout(20_000) { graph.tasks.observeTask(inbox).first { it == null } }
        vm.awaitTitles(TaskView.ALL, "Overdue", "Due today", "Next week")
    }
}
