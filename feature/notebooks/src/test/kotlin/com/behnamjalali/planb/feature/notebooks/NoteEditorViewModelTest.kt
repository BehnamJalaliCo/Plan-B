package com.behnamjalali.planb.feature.notebooks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.ui.AssistantOutcome
import kotlinx.coroutines.delay
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

    /** Plan-B Pro #39: a selection is what the assistant reads; its changes apply with Undo. */
    @Test
    fun assistant_readsTheSelection_andItsChangesCanBeUndone() = runBlocking<Unit> {
        val notebook = graph.notes.ensureDefaultNotebook("Notes")
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "سفر"))
        val vm = viewModel(SavedStateHandle(mapOf("noteId" to id)))
        vm.start("Notes")
        val block = vm.state.awaitItem { !it.loading }.blocks.single()
        vm.onFocus(block.id)
        vm.onBlockChange(block.id, TextFieldValue("باید بلیت بخرم و هتل رزرو کنم", TextRange(5)))
        assertThat(vm.assistantText()).isEqualTo("باید بلیت بخرم و هتل رزرو کنم" to false)
        vm.onBlockChange(block.id, TextFieldValue("باید بلیت بخرم و هتل رزرو کنم", TextRange(5, 14)))
        assertThat(vm.assistantText()).isEqualTo("بلیت بخرم" to true)

        vm.applyAssistant(AssistantOutcome.ReplaceSelection("بلیت قطار بخرم"))
        assertThat(vm.state.value.blocks.single().value.text).isEqualTo("باید بلیت قطار بخرم و هتل رزرو کنم")
        vm.undoAssistant()
        assertThat(vm.state.value.blocks.single().value.text).isEqualTo("باید بلیت بخرم و هتل رزرو کنم")

        vm.applyAssistant(AssistantOutcome.AddChecklist(listOf("بلیت", "هتل")))
        assertThat(vm.state.value.blocks.map { it.type }).containsExactly(BlockType.TEXT, BlockType.CHECKLIST, BlockType.CHECKLIST).inOrder()
        vm.applyAssistant(AssistantOutcome.SetTitle("سفر شیراز"))
        assertThat(vm.state.value.title.text).isEqualTo("سفر شیراز")
        vm.undoAssistant()
        assertThat(vm.state.value.title.text).isEqualTo("سفر")
        // Saved like any other edit.
        vm.flush()
        withTimeout(20_000) { while (graph.notes.getNote(id)!!.document.blocks.size != 3) delay(20) }
    }

    /** Plan-B Pro #40: dictation goes in at the caret, with spaces around it. */
    @Test
    fun dictation_isInsertedAtTheCaret() = runBlocking<Unit> {
        val notebook = graph.notes.ensureDefaultNotebook("Notes")
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "Note"))
        val vm = viewModel(SavedStateHandle(mapOf("noteId" to id)))
        vm.start("Notes")
        val block = vm.state.awaitItem { !it.loading }.blocks.single()
        vm.onFocus(block.id)
        vm.onBlockChange(block.id, TextFieldValue("خرید نان", TextRange(4)))
        vm.insertDictation("و شیر")
        val value = vm.state.value.blocks.single().value
        assertThat(value.text).isEqualTo("خرید و شیر نان")
        assertThat(value.selection).isEqualTo(TextRange(10))
    }

    @Test
    fun assistantText_becomesBlocks() {
        var n = 0
        val blocks = AssistantBlocks.fromText("# Plan\n\n- **one**\n2. two\n- [x] done\n```\nplain") { "b${n++}" }
        assertThat(blocks.map { it.type to it.value.text }).containsExactly(
            BlockType.HEADING to "Plan",
            BlockType.BULLET to "one",
            BlockType.NUMBERED to "two",
            BlockType.CHECKLIST to "done",
            BlockType.TEXT to "plain",
        ).inOrder()
        assertThat(blocks[3].checked).isTrue()
    }
}
