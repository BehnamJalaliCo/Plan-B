package com.behnamjalali.planb.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

@RunWith(RobolectricTestRunner::class)
class TaskEditorViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    /** A handle that already holds a form skips the async initial load. */
    private fun viewModel(form: TaskForm = TaskForm()): TaskEditorViewModel {
        val handle = SavedStateHandle(mapOf("task_form" to Json.encodeToString(TaskForm.serializer(), form)))
        return main.track(TaskEditorViewModel(handle, graph.tasks, graph.projects, graph.settings, graph.time, graph.planning))
    }

    private suspend fun allTasks() =
        graph.tasks.observeTasks(TaskFilter(today = graph.time.today())).first()

    @Test
    fun doubleTapOnSave_insertsTheTaskOnce() = runBlocking<Unit> {
        val vm = viewModel(TaskForm(title = "Once"))
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save()
        vm.save()
        assertThat(saved.await()).isInstanceOf(EditorEvent.Saved::class.java)
        delay(300)
        assertThat(allTasks().map { it.title }).containsExactly("Once")
    }

    @Test
    fun save_keepsTagTextThatWasNotConfirmedWithDone() = runBlocking<Unit> {
        val vm = viewModel(TaskForm(title = "Tagged"))
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save(pendingTag = "#work ")
        val id = (saved.await() as EditorEvent.Saved).id
        assertThat(graph.tasks.getTask(id)!!.tags.map { it.name }).containsExactly("work")
    }

    @Test
    fun startAfterDue_blocksSave() = runBlocking<Unit> {
        val today = graph.time.today()
        val vm = viewModel(TaskForm(title = "Backwards", startDate = today.plusDays(2).toEpochDay(), dueDate = today.toEpochDay()))
        assertThat(vm.form.awaitItem { it.title == "Backwards" }.datesValid).isFalse()
        vm.save()
        delay(300)
        assertThat(allTasks()).isEmpty()

        vm.update { it.copy(startDate = today.toEpochDay()) }
        assertThat(vm.form.awaitItem { it.startDate == today.toEpochDay() }.canSave).isTrue()
    }
}
