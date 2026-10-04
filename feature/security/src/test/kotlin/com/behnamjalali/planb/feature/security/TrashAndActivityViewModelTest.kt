package com.behnamjalali.planb.feature.security

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TrashItemType
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
class TrashAndActivityViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()
    private lateinit var graph: TestDataGraph

    @Before
    fun setUp() {
        graph = TestDataGraph().apply { pro = true }
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    @Test
    fun trash_listsFiltersRestoresAndEmpties() = runBlocking<Unit> {
        val task = graph.tasks.save(Task(title = "Old task"))
        val notebook = graph.notes.saveNotebook(Notebook(title = "NB"))
        val note = graph.notes.saveNote(Note(notebookId = notebook, title = "Old note"))
        graph.tasks.delete(listOf(task))
        graph.notes.deleteNote(note)

        val vm = main.track(TrashViewModel(graph.trash))
        main.keepCollecting(vm.state)
        val loaded = vm.state.awaitItem { it.items.size == 2 }
        assertThat(loaded.visible.map { it.title }).containsExactly("Old task", "Old note")

        vm.setFilter(TrashFilter.NOTES)
        assertThat(vm.state.awaitItem { it.filter == TrashFilter.NOTES }.visible.map { it.type }).containsExactly(TrashItemType.NOTE)

        val restored = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first { it is TrashEvent.Restored } } }
        vm.restore(loaded.items.first { it.type == TrashItemType.TASK })
        restored.await()
        assertThat(graph.tasks.getTask(task)!!.deletedAt).isNull()

        val emptied = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first { it is TrashEvent.Emptied } } }
        vm.empty()
        assertThat((emptied.await() as TrashEvent.Emptied).count).isEqualTo(1)
        assertThat(graph.notes.getNote(note)).isNull()
        assertThat(vm.state.awaitItem { it.items.isEmpty() }.items).isEmpty()
    }

    @Test
    fun activity_showsEverything_filtersByType_andShowsOneItem() = runBlocking<Unit> {
        val task = graph.tasks.save(Task(title = "Write report"))
        graph.tasks.setCompleted(task, true)
        val notebook = graph.notes.saveNotebook(Notebook(title = "Journal"))

        val all = main.track(ActivityViewModel(SavedStateHandle(), graph.activity))
        main.keepCollecting(all.state)
        assertThat(all.state.awaitItem { it.entries.size == 3 }.entries.map { it.summary })
            .containsExactly("Journal", "Write report", "Write report")
        all.setFilter(ActivityEntityType.NOTEBOOK)
        assertThat(all.state.awaitItem { it.filter == ActivityEntityType.NOTEBOOK && it.entries.size == 1 }.entries.single().entityId).isEqualTo(notebook)

        val single = main.track(ActivityViewModel(SavedStateHandle(mapOf("entityType" to "TASK", "entityId" to task)), graph.activity))
        main.keepCollecting(single.state)
        val state = single.state.awaitItem { it.entries.size == 2 }
        assertThat(state.single).isTrue()
        assertThat(state.entries.map { it.action }).containsExactly(ActivityAction.COMPLETED, ActivityAction.CREATED).inOrder()
    }
}
