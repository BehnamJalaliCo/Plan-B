package com.behnamjalali.planb.feature.notebooks

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.OfflineNoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteLinkRepository
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteGraph
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.NoteRef
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.behnamjalali.planb.feature.notebooks.clipper.ClipInput
import com.behnamjalali.planb.feature.notebooks.clipper.ClipIntentInput
import com.behnamjalali.planb.feature.notebooks.clipper.ClipParser
import com.behnamjalali.planb.feature.notebooks.graph.GraphBuilder
import com.behnamjalali.planb.feature.notebooks.graph.GraphFilter
import com.behnamjalali.planb.feature.notebooks.knowledge.NoteLinkTransformation
import com.google.common.truth.Truth.assertThat
import java.time.Duration
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

/** Plan-B Pro notes knowledge in feature:notebooks: links in the editor (#16), graph (#21), clipper (#22). */
@RunWith(RobolectricTestRunner::class)
class NotesKnowledgeFeatureTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private var counter = 0
    private val ids: () -> String = { "b${counter++}" }

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

    // region editor

    private fun editor(noteId: Long) = main.track(
        NoteEditorViewModel(
            SavedStateHandle(mapOf("noteId" to noteId)), graph.notes, NoTemplates,
            DocumentFiles(ApplicationProvider.getApplicationContext(), Dispatchers.IO), main.scope, null, null,
            history = OfflineNoteHistoryRepository(graph.db, graph.notes, graph.time) { graph.pro },
            links = OfflineNoteLinkRepository(graph.db),
        ),
    )

    @Test
    fun insertingALink_savesItAndKeepsItWholeWhileDeleting() = runBlocking<Unit> {
        val notebook = graph.notes.saveNotebook(Notebook(title = "Notes"))
        val target = graph.notes.saveNote(Note(notebookId = notebook, title = "Trip"))
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "Plan", document = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.TEXT, "")))))
        val vm = editor(id)
        vm.start("Notes")
        vm.state.awaitItem { !it.loading }
        vm.onBlockChange("a", TextFieldValue("See [[tr", TextRange(8)))
        val query = NoteLinks.pendingQuery("See [[tr", 8)!!
        vm.insertLink("a", query, 8, target, "Trip")
        val linked = vm.state.value.blocks.single().value
        assertThat(linked.text).isEqualTo("See [[note:$target|Trip]]")
        assertThat(linked.selection).isEqualTo(TextRange(linked.text.length))
        vm.save()
        withTimeout(10_000) {
            while (graph.db.noteLinkDao().observeOutgoing(id).first() != listOf(target)) kotlinx.coroutines.delay(20)
        }
        // Backspace right after the link removes all of it.
        vm.onBlockChange("a", TextFieldValue(linked.text.dropLast(1), TextRange(linked.text.length - 1)))
        assertThat(vm.state.value.blocks.single().value.text).isEqualTo("See ")
    }

    @Test
    fun saving_takesAVersionOfTheSessionStartForPro() = runBlocking<Unit> {
        graph.pro = true
        val notebook = graph.notes.saveNotebook(Notebook(title = "Notes"))
        val id = graph.notes.saveNote(Note(notebookId = notebook, title = "Essay", document = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.TEXT, "first")))))
        val vm = editor(id)
        vm.start("Notes")
        vm.state.awaitItem { !it.loading }
        vm.onBlockChange("a", TextFieldValue("second", TextRange(6)))
        vm.save()
        val versions = graph.db.noteVersionDao().observeForNote(id).first()
        assertThat(versions.map { NoteDocument.decode(it.content).blocks.single().text }).containsExactly("first")
        // Another save within 10 minutes keeps no new version.
        graph.time.advance(Duration.ofMinutes(2))
        vm.onBlockChange("a", TextFieldValue("third", TextRange(5)))
        vm.save()
        assertThat(graph.db.noteVersionDao().observeForNote(id).first()).hasSize(1)
    }

    @Test
    fun linkTransformation_showsCurrentTitlesAndSnapsTheCaret() {
        val raw = "a [[note:7|Old]] b"
        val refs = mapOf(7L to NoteRef(7, "New title", 1))
        val t = NoteLinkTransformation(refs, Color.Blue, Color.Gray, "Untitled").filter(AnnotatedString(raw))
        assertThat(t.text.text).isEqualTo("a New title b")
        val map = t.offsetMapping
        assertThat(map.originalToTransformed(0)).isEqualTo(0)
        assertThat(map.originalToTransformed(2)).isEqualTo(2)
        // Inside the token: the caret goes to the end of the title.
        assertThat(map.originalToTransformed(5)).isEqualTo(11)
        assertThat(map.originalToTransformed(raw.length)).isEqualTo(t.text.length)
        assertThat(map.transformedToOriginal(2)).isEqualTo(2)
        assertThat(map.transformedToOriginal(6)).isEqualTo(16)
        assertThat(map.transformedToOriginal(t.text.length)).isEqualTo(raw.length)
        // A deleted note keeps its stored title.
        val gone = NoteLinkTransformation(emptyMap(), Color.Blue, Color.Gray, "Untitled").filter(AnnotatedString(raw))
        assertThat(gone.text.text).isEqualTo("a Old b")
    }

    // endregion

    // region graph

    private fun ref(id: Long, notebook: Long = 1) = NoteRef(id, "Note $id", notebook)

    @Test
    fun graphBuilder_filtersByNotebookTagAndOrphans() {
        val g = NoteGraph(
            notes = listOf(ref(1), ref(2), ref(3), ref(4, notebook = 2)),
            edges = listOf(1L to 2L, 2L to 4L, 1L to 1L),
            tags = mapOf(1L to setOf(9L), 2L to setOf(9L)),
        )
        val all = GraphBuilder.build(g, GraphFilter())
        assertThat(all.nodes.map { it.id }).containsExactly(1L, 2L, 3L, 4L).inOrder()
        assertThat(all.edges).hasSize(2)
        assertThat(all.nodes.first { it.id == 2L }.degree).isEqualTo(2)
        assertThat(GraphBuilder.build(g, GraphFilter(orphans = false)).nodes.map { it.id }).containsExactly(1L, 2L, 4L)
        assertThat(GraphBuilder.build(g, GraphFilter(notebookId = 1, orphans = false)).nodes.map { it.id }).containsExactly(1L, 2L)
        assertThat(GraphBuilder.build(g, GraphFilter(tagId = 9)).edges).containsExactly(0 to 1)
        // Same input, same picture.
        assertThat(GraphBuilder.build(g, GraphFilter())).isEqualTo(all)
    }

    @Test
    fun graphBuilder_keepsTheBestConnectedNotesWhenCapped() {
        val notes = (1L..50L).map { ref(it) }
        val edges = (2L..10L).map { 1L to it } + (11L to 12L)
        val built = GraphBuilder.build(NoteGraph(notes, edges), GraphFilter(), maxNodes = 12)
        assertThat(built.capped).isTrue()
        assertThat(built.nodes.map { it.id }).containsAtLeast(1L, 2L, 10L, 11L, 12L)
        assertThat(built.nodes).hasSize(12)
    }

    // endregion

    // region clipper

    @Test
    fun clip_sharedLinkWithTitleFromTheApp() {
        val clip = ClipParser.parse(ClipInput(text = "https://example.com/article?id=3", subject = "A good read"), ids, "Clip")!!
        assertThat(clip.title).isEqualTo("A good read")
        assertThat(clip.url).isEqualTo("https://example.com/article?id=3")
        assertThat(clip.blocks.map { it.text }).containsExactly("https://example.com/article?id=3")
        // Without a title the host names the note; nothing is fetched.
        assertThat(ClipParser.parse(ClipInput(text = "https://www.example.com/x"), ids, "Clip")!!.title).isEqualTo("example.com")
    }

    @Test
    fun clip_textBecomesBlocksAndHtmlIsSanitized() {
        val text = ClipParser.parse(ClipInput(text = "Shopping\n\n- bread\n- milk\n\n> quote\n\nSource https://shop.example/list."), ids, "Clip")!!
        assertThat(text.title).isEqualTo("Shopping")
        assertThat(text.blocks.map { it.type }).containsAtLeast(BlockType.BULLET, BlockType.QUOTE)
        assertThat(text.url).isEqualTo("https://shop.example/list")

        val html = ClipParser.parse(
            ClipInput(html = "<h2>News</h2><p onclick=x>Body<script>steal()</script></p><a href='javascript:x'>bad</a>", text = "fallback", title = "Page"),
            ids,
            "Clip",
        )!!
        assertThat(html.title).isEqualTo("Page")
        assertThat(html.blocks.joinToString { it.text }).doesNotContain("steal")
        assertThat(html.blocks.joinToString { it.text }).doesNotContain("javascript")
        assertThat(html.blocks.first().type).isEqualTo(BlockType.HEADING)
    }

    @Test
    fun clip_limitsAndEmptyShares() {
        assertThat(ClipParser.parse(ClipInput(text = "   "), ids, "Clip")).isNull()
        assertThat(ClipParser.parse(ClipInput(), ids, "Clip")).isNull()
        val huge = ClipParser.parse(ClipInput(text = "word ".repeat(100_000)), ids, "Clip")!!
        assertThat(huge.truncated).isTrue()
        assertThat(huge.blocks.sumOf { it.text.length }).isAtMost(ClipParser.MAX_TEXT)
        val hostile = ClipParser.parse(ClipInput(text = "Hi", subject = "Evil" + Char(0x202E) + "title" + Char(0) + "x".repeat(1_000)), ids, "Clip")!!
        assertThat(hostile.title.length).isAtMost(ClipParser.MAX_TITLE)
        assertThat(hostile.title.none { it.code == 0x202E || it.code == 0 }).isTrue()
        // Only http(s) addresses become the source.
        assertThat(ClipParser.parse(ClipInput(text = "javascript:alert(1)"), ids, "Clip")!!.url).isNull()
    }

    @Test
    fun shareIntent_onlyTextSendsAreAccepted() {
        val send = ClipIntentInput.ACTION_SEND
        assertThat(ClipIntentInput.from(send, "text/plain", "hello", null, null, null)).isEqualTo(ClipInput(text = "hello"))
        assertThat(ClipIntentInput.from(send, "text/html; charset=utf-8", null, "<p>x</p>", "s", null)!!.html).isEqualTo("<p>x</p>")
        assertThat(ClipIntentInput.from("android.intent.action.VIEW", "text/plain", "hello", null, null, null)).isNull()
        assertThat(ClipIntentInput.from(send, "image/png", "hello", null, null, null)).isNull()
        assertThat(ClipIntentInput.from(send, null, "hello", null, null, null)).isNull()
        assertThat(ClipIntentInput.from(send, "text/plain", null, null, "only a subject", null)).isNull()
        val big = ClipIntentInput.from(send, "text/plain", "a".repeat(2_000_000), null, null, null)!!
        assertThat(big.text!!.length).isAtMost(ClipParser.MAX_HTML + 1)
    }

    // endregion
}
