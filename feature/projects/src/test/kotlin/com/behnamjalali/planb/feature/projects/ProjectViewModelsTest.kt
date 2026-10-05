package com.behnamjalali.planb.feature.projects

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Tag
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectViewModelsTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private var projectId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        graph = TestDataGraph()
        projectId = graph.projects.save(Project(title = "Garden", description = "Old notes", tags = listOf(Tag(name = "home"), Tag(name = "outdoor"))))
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun detail() = main.track(
        ProjectDetailViewModel(SavedStateHandle(mapOf("projectId" to projectId)), graph.projects, graph.tasks, graph.time, main.scope, graph.planning),
    )

    private suspend fun tagNames() = graph.projects.getProject(projectId)!!.tags.map { it.name }

    @Test
    fun getProject_includesTags() = runBlocking<Unit> {
        assertThat(tagNames()).containsExactly("home", "outdoor")
    }

    @Test
    fun notesAutosave_keepsTags() = runBlocking<Unit> {
        val vm = detail()
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first { it == ProjectEvent.NotesSaved } } }
        vm.updateNotes("New notes")
        saved.await()
        val project = graph.projects.getProject(projectId)!!
        assertThat(project.description).isEqualTo("New notes")
        assertThat(project.tags.map { it.name }).containsExactly("home", "outdoor")
    }

    @Test
    fun pendingNotes_areWrittenWhenFlushedOrCleared() = runBlocking<Unit> {
        val vm = detail()
        vm.updateNotes("Typed then left the tab")
        vm.flushNotes()
        withTimeout(20_000) { graph.projects.observeProject(projectId).first { it?.project?.description == "Typed then left the tab" } }

        vm.updateNotes("Typed then left the screen")
        main.clearViewModels()
        withTimeout(20_000) { graph.projects.observeProject(projectId).first { it?.project?.description == "Typed then left the screen" } }
    }

    @Test
    fun revertingNotesToTheOriginal_isSaved() = runBlocking<Unit> {
        val vm = detail()
        vm.updateNotes("Changed")
        vm.flushNotes()
        withTimeout(20_000) { graph.projects.observeProject(projectId).first { it?.project?.description == "Changed" } }
        vm.updateNotes("Old notes")
        vm.flushNotes()
        withTimeout(20_000) { graph.projects.observeProject(projectId).first { it?.project?.description == "Old notes" } }
    }

    @Test
    fun editingAProject_keepsItsTags_andDoubleSaveInsertsOnce() = runBlocking<Unit> {
        val editor = main.track(ProjectEditorViewModel(SavedStateHandle(mapOf("projectId" to projectId)), graph.projects))
        editor.form.awaitItem { it.id == projectId }
        assertThat(editor.form.value.tags).isEqualTo("home, outdoor")
        editor.update { it.copy(title = "Garden 2") }
        editor.form.awaitItem { it.title == "Garden 2" }
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { editor.saved.first() } }
        editor.save()
        editor.save()
        assertThat(saved.await()).isEqualTo(projectId)
        assertThat(tagNames()).containsExactly("home", "outdoor")

        val creator = main.track(ProjectEditorViewModel(SavedStateHandle(), graph.projects))
        // The blank form loads asynchronously; keep typing until the edit sticks.
        withTimeout(20_000) {
            while (!creator.isDirty) {
                creator.update { it.copy(title = "Once") }
                delay(50)
            }
        }
        val created = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { creator.saved.first() } }
        creator.save()
        creator.save()
        created.await()
        delay(300)
        val titles = graph.projects.observeProjects().first().map { it.project.title }
        assertThat(titles.count { it == "Once" }).isEqualTo(1)
    }
}
