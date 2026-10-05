package com.behnamjalali.planb.feature.notebooks

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.TemplateRepository
import com.behnamjalali.planb.core.data.repository.TemplateResult
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.SearchMatchSource
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.model.rich.TableData
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.behnamjalali.planb.feature.notebooks.media.HandwritingRecognition
import com.behnamjalali.planb.feature.notebooks.media.PickedContent
import com.behnamjalali.planb.feature.notebooks.media.RecognizedText
import com.behnamjalali.planb.feature.notebooks.media.SpeechTranscription
import com.behnamjalali.planb.feature.notebooks.media.TextRecognition
import com.behnamjalali.planb.feature.notebooks.rich.HandwritingConsent
import com.behnamjalali.planb.feature.notebooks.rich.RichMessage
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** Plan-B Pro rich blocks in the editor, with fake recognition engines (no ML Kit on the JVM). */
@RunWith(RobolectricTestRunner::class)
class RichEditorTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private var noteId = 0L

    private object NoTemplates : TemplateRepository {
        override fun observeTemplates(): Flow<List<PlannerTemplate>> = flowOf(emptyList())
        override fun builtInTemplates(): List<PlannerTemplate> = emptyList()
        override suspend fun saveCustom(title: String, type: TemplateType, payload: TemplatePayload, id: EntityId): EntityId = 0
        override suspend fun saveNoteAsTemplate(note: Note): EntityId = 0
        override suspend fun rename(id: EntityId, title: String) = Unit
        override suspend fun delete(id: EntityId) = Unit
        override suspend fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String) = TemplateResult(TemplateType.NOTE, 0)
    }

    private class FakeOcr(var text: String?) : TextRecognition {
        val seen = mutableListOf<File>()
        override suspend fun recognize(image: File): String? = text.also { seen += image }
    }

    private class FakeInk(var ready: Boolean, val downloadWorks: Boolean = true) : HandwritingRecognition {
        var downloads = mutableListOf<Pair<String, Boolean>>()
        override suspend fun isModelReady(language: String) = ready
        override suspend fun downloadModel(language: String, wifiOnly: Boolean): Boolean {
            downloads += language to wifiOnly
            ready = downloadWorks
            return downloadWorks
        }
        override suspend fun recognize(drawing: Drawing, language: String) = if (drawing.strokes.isEmpty()) null else "سلام $language"
    }

    private class FakeSpeech(val files: Boolean) : SpeechTranscription {
        override fun canTranscribeFiles() = files
        override fun canDictate() = true
        override suspend fun transcribeFile(file: File, languageTag: String) = "file transcript $languageTag"
        override suspend fun dictate(languageTag: String) = "dictated $languageTag"
    }

    private object FakePicked : PickedContent {
        override fun open(uri: Uri): InputStream = ByteArrayInputStream(ByteArray(64) { it.toByte() })
        override fun displayName(uri: Uri) = uri.lastPathSegment.orEmpty()
        override fun mimeType(uri: Uri) = "application/pdf"
    }

    private val ocr = FakeOcr("INVOICE 42")
    private val ink = FakeInk(ready = false)
    private var speech = FakeSpeech(files = true)

    @Before
    fun setUp() = runBlocking<Unit> {
        graph = TestDataGraph()
        val notebook = graph.notes.ensureDefaultNotebook("Notes")
        noteId = graph.notes.saveNote(Note(notebookId = notebook, title = "Rich"))
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel() = main.track(
        NoteEditorViewModel(
            SavedStateHandle(mapOf("noteId" to noteId)), graph.notes, NoTemplates,
            DocumentFiles(ApplicationProvider.getApplicationContext(), Dispatchers.IO), main.scope,
            attachments = graph.attachments, textRecognition = ocr, handwriting = ink, speech = speech, picked = FakePicked,
        ),
    )

    private suspend fun opened(): NoteEditorViewModel = viewModel().also {
        it.start("Notes")
        it.state.awaitItem { s -> !s.loading }
    }

    private suspend fun saved(vm: NoteEditorViewModel): NoteDocument {
        vm.save()
        return graph.notes.getNote(noteId)!!.document
    }

    @Test
    fun insertedBlocks_followTheFocus_andAreSaved() = runBlocking<Unit> {
        val vm = opened()
        val first = vm.state.value.blocks.single()
        vm.onFocus(first.id)
        // The empty focused paragraph is replaced; a text block follows so writing can go on.
        val table = vm.rich.insert(BlockType.TABLE, RichBlocks.encode(TableData()))
        assertThat(vm.state.value.blocks.map { it.type }).containsExactly(BlockType.TABLE, BlockType.TEXT).inOrder()
        assertThat(vm.state.value.focusId).isEqualTo(table)
        vm.rich.insert(BlockType.MATH, text = "x^2")
        vm.rich.update(table, RichBlocks.encode(TableData().setCell(0, 0, "قیمت")))
        val doc = saved(vm)
        assertThat(doc.blocks.map { it.type }).containsExactly(BlockType.TABLE, BlockType.MATH, BlockType.TEXT).inOrder()
        assertThat(RichBlocks.table(doc.blocks[0]).cell(0, 0)).isEqualTo("قیمت")
        assertThat(doc.blocks[1].text).isEqualTo("x^2")
        // A note that holds only rich blocks is not "empty" and is never discarded.
        assertThat(doc.isBlank()).isFalse()
    }

    @Test
    fun ownRichEdits_keepTheBlockRevision_butRestoredDraftsBumpIt() = runBlocking<Unit> {
        val vm = opened()
        val table = vm.rich.insert(BlockType.TABLE, RichBlocks.encode(TableData()))
        val revision = vm.state.value.blocks.first { it.id == table }.revision
        vm.rich.update(table, RichBlocks.encode(TableData().addRow()))
        assertThat(vm.state.value.blocks.first { it.id == table }.revision).isEqualTo(revision)
    }

    @Test
    fun textNeverMergesIntoRichBlocks_andTheirKindIsKept() = runBlocking<Unit> {
        val vm = opened()
        val text = vm.state.value.blocks.single()
        vm.onFocus(text.id)
        val table = vm.rich.insert(BlockType.TABLE, RichBlocks.encode(TableData()))
        val after = vm.state.value.blocks.last()
        vm.onBackspaceAtStart(after.id)
        vm.mergeWithPrevious(after.id)
        assertThat(vm.state.value.blocks.map { it.type }).containsExactly(BlockType.TABLE, BlockType.TEXT).inOrder()
        vm.setType(table, BlockType.HEADING)
        assertThat(vm.state.value.blocks.first().type).isEqualTo(BlockType.TABLE)
    }

    @Test
    fun pickedImages_becomeBlocks_andTheirTextIsSearchable() = runBlocking<Unit> {
        val vm = opened()
        vm.rich.addImages(listOf(Uri.parse("content://picked/receipt.jpg")), AttachmentKind.SCAN)
        val state = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.SCAN } && s.richWork.isEmpty() && s.attachments.values.any { it.ocrText != null } }
        val scan = state.blocks.first { it.type == BlockType.SCAN }
        assertThat(state.attachments.getValue(scan.attachmentId!!).ocrText).isEqualTo("INVOICE 42")
        assertThat(ocr.seen).hasSize(1)
        saved(vm)
        val hit = graph.search.search("invoice").single()
        assertThat(hit.id).isEqualTo(noteId)
        assertThat(hit.foundIn).isEqualTo(SearchMatchSource.IMAGE)
    }

    @Test
    fun nothingRecognized_saysSo_onlyWhenAsked() = runBlocking<Unit> {
        ocr.text = null
        val vm = opened()
        vm.rich.addImages(listOf(Uri.parse("content://picked/photo.jpg")), AttachmentKind.IMAGE)
        val image = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.IMAGE } && s.richWork.isEmpty() && ocr.seen.isNotEmpty() }
            .blocks.first { it.type == BlockType.IMAGE }
        val events = java.util.Collections.synchronizedList(mutableListOf<NoteEditorEvent>())
        val collector = CoroutineScope(Dispatchers.Default).launch { vm.events.collect { events += it } }
        delay(200)
        vm.rich.recognizeText(image.id)
        withTimeout(20_000) { while (events.none { it == NoteEditorEvent.Rich(RichMessage.NOTHING_RECOGNIZED) }) delay(20) }
        collector.cancel()
    }

    @Test
    fun files_areCopiedIn_andRemovedWhenTheirBlockIsDeleted() = runBlocking<Unit> {
        val vm = opened()
        vm.rich.addFile(Uri.parse("content://picked/report.pdf"))
        val state = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.FILE } && s.attachments.isNotEmpty() }
        val block = state.blocks.first { it.type == BlockType.FILE }
        val attachment = state.attachments.getValue(block.attachmentId!!)
        assertThat(attachment.displayName).isEqualTo("report.pdf")
        assertThat(attachment.mimeType).isEqualTo("application/pdf")
        vm.deleteBlock(block.id)
        saved(vm)
        graph.time.advance(java.time.Duration.ofMinutes(15))
        assertThat(graph.attachments.deleteUnused(noteId)).isEqualTo(1)
        assertThat(graph.attachmentFiles.directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun recordings_getAWaveform_andATranscript() = runBlocking<Unit> {
        val vm = opened()
        val recording = File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(100)) }
        vm.rich.addRecording(recording, 2_500, List(96) { (it % 10) / 10f }, "Voice note")
        val block = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.AUDIO } && s.attachments.isNotEmpty() }.blocks.first { it.type == BlockType.AUDIO }
        assertThat(RichBlocks.audio(block.toNoteBlockForTest()).waveform).hasSize(48)
        vm.rich.transcribe(block.id, "fa-IR")
        val state = vm.state.awaitItem { s -> s.attachments.values.any { it.transcript != null } }
        assertThat(state.attachments.values.single().transcript).isEqualTo("file transcript fa-IR")
        assertThat(graph.search.search("transcript").single().foundIn).isEqualTo(SearchMatchSource.RECORDING)
    }

    @Test
    fun withoutFileTranscription_theTranscriptIsDictated() = runBlocking<Unit> {
        speech = FakeSpeech(files = false)
        val vm = opened()
        vm.rich.addRecording(File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(10)) }, 1_000, emptyList(), "Voice")
        val block = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.AUDIO } && s.attachments.isNotEmpty() }.blocks.first { it.type == BlockType.AUDIO }
        assertThat(vm.rich.canTranscribeFiles()).isFalse()
        vm.rich.transcribe(block.id, "en-US")
        assertThat(vm.state.awaitItem { s -> s.attachments.values.any { it.transcript != null } }.attachments.values.single().transcript).isEqualTo("dictated en-US")
    }

    @Test
    fun handwriting_asksBeforeDownloading_thenInsertsTheTextBelow() = runBlocking<Unit> {
        val vm = opened()
        val drawing = Drawing(strokes = listOf(Stroke(0xFF000000, 4f, listOf(10, 10, 100, 50, 60, 100))))
        vm.rich.saveDrawing(null, drawing, byteArrayOf(1, 2, 3), drawing.width, drawing.height)
        val block = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.DRAWING } }.blocks.first { it.type == BlockType.DRAWING }
        assertThat(vm.rich.readDrawing(block.id)).isEqualTo(drawing)

        vm.rich.convertHandwriting(block.id, "fa")
        assertThat(vm.state.awaitItem { it.handwritingConsent != null }.handwritingConsent).isEqualTo(HandwritingConsent(block.id, "fa"))
        assertThat(ink.downloads).isEmpty()

        vm.rich.convertHandwriting(block.id, "fa", consented = true, wifiOnly = true)
        val state = vm.state.awaitItem { s -> s.blocks.any { it.value.text == "سلام fa" } }
        assertThat(ink.downloads).containsExactly("fa" to true)
        assertThat(state.handwritingConsent).isNull()
        val index = state.blocks.indexOfFirst { it.id == block.id }
        assertThat(state.blocks[index + 1].type to state.blocks[index + 1].value.text).isEqualTo(BlockType.TEXT to "سلام fa")
    }

    @Test
    fun editingADrawing_replacesItsFilesInPlace() = runBlocking<Unit> {
        val vm = opened()
        val first = Drawing(strokes = listOf(Stroke(1, 2f, listOf(0, 0, 100))))
        vm.rich.saveDrawing(null, first, byteArrayOf(1), first.width, first.height)
        val block = vm.state.awaitItem { s -> s.blocks.any { it.type == BlockType.DRAWING } }.blocks.first { it.type == BlockType.DRAWING }
        val second = first.plus(Stroke(2, 2f, listOf(5, 5, 100)))
        vm.rich.saveDrawing(block.id, second, byteArrayOf(2), second.width, second.height)
        withTimeout(20_000) { while (vm.rich.readDrawing(block.id) != second) delay(20) }
        assertThat(vm.state.value.blocks.count { it.type == BlockType.DRAWING }).isEqualTo(1)
        assertThat(graph.attachments.observeForNote(noteId).first()).hasSize(2)
    }

    @Test
    fun recognizedText_mergesEnginesLineByLine() {
        assertThat(RecognizedText.merge("سلام\nTotal 12", "Total 12\nINVOICE", null)).isEqualTo("سلام\nTotal 12\nINVOICE")
        assertThat(RecognizedText.merge(" ", null)).isNull()
        assertThat(RecognizedText.persianShare("سلام world")).isWithin(0.01).of(4.0 / 9)
        assertThat(RecognizedText.persianShare("123")).isEqualTo(0.0)
    }

    @Test
    fun legacyNotes_openUnchanged() = runBlocking<Unit> {
        graph.notes.updateContent(noteId, "Rich", NoteDocument(blocks = listOf(NoteBlock("a", BlockType.HEADING, "H"), NoteBlock("b", BlockType.TEXT, "t"))))
        val vm = opened()
        assertThat(vm.state.value.blocks.map { it.type to it.value.text }).containsExactly(BlockType.HEADING to "H", BlockType.TEXT to "t").inOrder()
        assertThat(saved(vm).encode()).isEqualTo(graph.notes.getNote(noteId)!!.document.encode())
    }
}

private fun EditorBlock.toNoteBlockForTest() = NoteBlock(id, type, value.text, checked, data, attachmentId)
