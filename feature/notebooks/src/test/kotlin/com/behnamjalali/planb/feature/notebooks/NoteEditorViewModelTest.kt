package com.behnamjalali.planb.feature.notebooks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteEditorViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    /** The editor only saves notes as templates; nothing here needs the real repository. */
    private object NoTemplates : TemplateRepository {
        override fun observeTemplates(): Flow<List<PlannerTemplate>> = flowOf(emptyList())
        override fun builtInTemplates(): List<PlannerTemplate> = emptyList()
        override suspend fun saveCustom(title: String, type: TemplateType, payload: TemplatePayload, id: EntityId): EntityId = 0
        override suspend fun saveNoteAsTemplate(note: Note): EntityId = 0
        override suspend fun rename(id: EntityId, title: String) = Unit
        override suspend fun delete(id: EntityId) = Unit
        override suspend fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String) = TemplateResult(TemplateType.NOTE, 0)
    }

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel(handle: SavedStateHandle) = main.track(
        NoteEditorViewModel(handle, graph.notes, NoTemplates, DocumentFiles(ApplicationProvider.getApplicationContext(), Dispatchers.IO), main.scope),
    )

    @Test
    fun newNote_isNotCreatedTwiceAfterProcessDeath() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.start("Notes")
        val created = first.state.awaitItem { !it.loading }.noteId
        first.onTitleChange(TextFieldValue("Kept"))
        first.save()

        // Process death: a new ViewModel over the same saved state, route still "new note".
        val restored = viewModel(handle)
        restored.start("Notes")
        val state = restored.state.awaitItem { !it.loading }
        assertThat(state.noteId).isEqualTo(created)
        val notebook = graph.notes.getNote(created)!!.notebookId
        assertThat(graph.notes.observeNotes(notebook).first().map { it.id }).containsExactly(created)
    }

    @Test
    fun archiving_unpinsTheNoteInTheEditorToo() = runBlocking<Unit> {
        val notebook = graph.notes.ensureDefaultNotebook("Notes")
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "Pinned", pinned = true))
        val vm = viewModel(SavedStateHandle(mapOf("noteId" to id)))
        vm.start("Notes")
        assertThat(vm.state.awaitItem { !it.loading }.pinned).isTrue()

        vm.setArchived(true)
        val archived = vm.state.awaitItem { it.archived }
        assertThat(archived.pinned).isFalse()
    }

    @Test
    fun fieldEdits_keepTheirRevision_butSplitsBumpIt() = runBlocking<Unit> {
        val notebook = graph.notes.ensureDefaultNotebook("Notes")
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "Note"))
        val vm = viewModel(SavedStateHandle(mapOf("noteId" to id)))
        vm.start("Notes")
        val block = vm.state.awaitItem { !it.loading }.blocks.single()

        // A plain edit is the field's own value: the field must not be reset.
        vm.onBlockChange(block.id, TextFieldValue("hello", TextRange(5)))
        assertThat(vm.state.value.blocks.single().revision).isEqualTo(block.revision)

        // Enter splits: this block's text was changed by the ViewModel, so its field resyncs.
        vm.onBlockChange(block.id, TextFieldValue("hel\nlo", TextRange(4)))
        val blocks = vm.state.value.blocks
        assertThat(blocks.map { it.value.text }).containsExactly("hel", "lo").inOrder()
        assertThat(blocks[0].revision).isGreaterThan(block.revision)
        withTimeout(20_000) { assertThat(vm.state.first { it.focusId == blocks[1].id }.focusId).isEqualTo(blocks[1].id) }
    }
}
