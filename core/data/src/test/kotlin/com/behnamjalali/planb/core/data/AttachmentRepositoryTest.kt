package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.AttachmentLimitException
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.SearchMatchSource
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingRef
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttachmentRepositoryTest {
    private lateinit var graph: TestDataGraph
    private val attachments get() = graph.attachments
    private var noteId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        graph = TestDataGraph()
        val notebook = graph.notes.saveNotebook(Notebook(title = "Work"))
        noteId = graph.notes.saveNote(Note(notebookId = notebook, title = "Receipts"))
    }

    @After
    fun tearDown() = graph.close()

    private fun bytes(n: Int) = { ByteArrayInputStream(ByteArray(n) { it.toByte() }) as InputStream }

    /** A stream of [size] zero bytes that never holds them in memory. */
    private fun zeros(size: Long) = {
        object : InputStream() {
            var left = size
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                left -= n
                java.util.Arrays.fill(b, off, off + n, 0)
                return n
            }
        } as InputStream
    }

    private suspend fun write(vararg blocks: NoteBlock) =
        graph.notes.updateContent(noteId, "Receipts", NoteDocument(blocks = blocks.toList()))

    private fun files(): List<String> = graph.attachmentFiles.directory.listFiles().orEmpty().map { it.name }

    @Test
    fun images_filesAndRecordings_areStoredPrivately() = runBlocking<Unit> {
        val image = attachments.addImage(noteId, AttachmentKind.IMAGE, "photo.heic", bytes(1000))
        assertThat(image.kind).isEqualTo(AttachmentKind.IMAGE)
        assertThat(image.mimeType).isEqualTo("image/jpeg")
        assertThat(image.width to image.height).isEqualTo(640 to 480)
        assertThat(image.fileName).endsWith(".jpg")
        assertThat(attachments.file(image).length()).isEqualTo(1000)

        val file = attachments.addFile(noteId, "report.final.pdf", "application/pdf", bytes(10))
        assertThat(file.fileName).endsWith(".pdf")
        assertThat(file.displayName).isEqualTo("report.final.pdf")

        val recording = File.createTempFile("rec", ".m4a").apply { writeBytes(ByteArray(42)) }
        val audio = attachments.addRecording(noteId, recording, 3_500, "Voice note")
        assertThat(audio.durationMillis).isEqualTo(3_500)
        assertThat(recording.exists()).isFalse()
        assertThat(attachments.observeForNote(noteId).first().map { it.kind })
            .containsExactly(AttachmentKind.IMAGE, AttachmentKind.FILE, AttachmentKind.AUDIO).inOrder()
        // Nothing is left in the import folder.
        assertThat(File(graph.attachmentFiles.directory.parentFile, "attachment-import").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun tooLargeOrBrokenFiles_areRejected_withoutLeftovers() = runBlocking<Unit> {
        val error = assertThrows(AttachmentLimitException::class.java) {
            runBlocking { attachments.addFile(noteId, "big.zip", "application/zip", zeros(AttachmentLimits.MAX_FILE_BYTES + 1)) }
        }
        assertThat(error.reason).isEqualTo(AttachmentLimitException.Reason.FILE_TOO_LARGE)
        assertThrows(UnsupportedImageException::class.java) {
            runBlocking { attachments.addImage(noteId, AttachmentKind.IMAGE, "empty.jpg", bytes(0)) }
        }
        assertThat(files()).isEmpty()
        assertThat(attachments.observeForNote(noteId).first()).isEmpty()
        // A file of exactly the limit fits (the limit equals the backup's per-file limit).
        attachments.addFile(noteId, "ok.bin", "", zeros(AttachmentLimits.MAX_FILE_BYTES))
        assertThat(files()).hasSize(1)
    }

    @Test
    fun imageSizing_fitsTheLongerSideAndPicksASampleSize() {
        assertThat(ImageSizing.fit(4000, 3000)).isEqualTo(2560 to 1920)
        assertThat(ImageSizing.fit(1200, 5120)).isEqualTo(600 to 2560)
        assertThat(ImageSizing.fit(800, 600)).isEqualTo(800 to 600)
        assertThat(ImageSizing.sampleSize(12000, 9000, 2560)).isEqualTo(4)
        assertThat(ImageSizing.sampleSize(2000, 1000, 2560)).isEqualTo(1)
    }

    @Test
    fun recognizedText_isSearchable_andShownAsFoundInImage() = runBlocking<Unit> {
        val scan = attachments.addImage(noteId, AttachmentKind.SCAN, "scan", bytes(10))
        write(NoteBlock("s", BlockType.SCAN, attachmentId = scan.id))
        attachments.setOcrText(scan.id, "فاکتور شمارهٔ ۱۲۴ INVOICE")
        val audio = attachments.addRecording(noteId, File.createTempFile("rec", ".m4a").apply { writeText("x") }, 1000, "")
        attachments.setTranscript(audio.id, "جلسهٔ بودجه")

        val inImage = graph.search.search("invoice").single()
        assertThat(inImage.type to inImage.id).isEqualTo(SearchEntityType.NOTE to noteId)
        assertThat(inImage.foundIn).isEqualTo(SearchMatchSource.IMAGE)
        assertThat(inImage.snippet).contains("INVOICE")
        // Persian digits and Arabic letters typed differently still match.
        assertThat(graph.search.search("فاكتور 124").single().foundIn).isEqualTo(SearchMatchSource.IMAGE)
        assertThat(graph.search.search("بودجه").single().foundIn).isEqualTo(SearchMatchSource.RECORDING)
        // A match in the note's own text is not attributed to an image.
        assertThat(graph.search.search("Receipts").single().foundIn).isNull()

        // The index rebuild (restore, normalizer upgrade) keeps the recognized text.
        graph.db.searchDao().clear()
        SearchIndexMaintenance(graph.db.backupDao(), graph.db.searchDao()).rebuild()
        assertThat(graph.search.search("invoice")).hasSize(1)

        // Clearing the text removes it from the index.
        attachments.setOcrText(scan.id, " ")
        assertThat(graph.search.search("invoice")).isEmpty()
    }

    @Test
    fun lockedNotes_neverIndexRecognizedText() = runBlocking<Unit> {
        val scan = attachments.addImage(noteId, AttachmentKind.SCAN, "scan", bytes(10))
        graph.db.noteDao().setLocked(noteId, true, byteArrayOf(1, 2, 3), NoteDocument.EMPTY.encode(), 0)
        attachments.setOcrText(scan.id, "secret salary")
        assertThat(graph.search.search("salary")).isEmpty()
        SearchIndexMaintenance(graph.db.backupDao(), graph.db.searchDao()).rebuild()
        assertThat(graph.search.search("salary")).isEmpty()
        assertThat(graph.search.search("Receipts").single().snippet).isEmpty()
    }

    @Test
    fun unusedAttachments_areDeleted_butDraftsVersionsAndNewFilesKeepTheirs() = runBlocking<Unit> {
        val kept = attachments.addImage(noteId, AttachmentKind.IMAGE, "a", bytes(10))
        val removed = attachments.addImage(noteId, AttachmentKind.IMAGE, "b", bytes(10))
        val inDraft = attachments.addImage(noteId, AttachmentKind.IMAGE, "c", bytes(10))
        write(NoteBlock("1", BlockType.IMAGE, attachmentId = kept.id))
        graph.notes.saveDraft(noteId, "Receipts", NoteDocument(blocks = listOf(NoteBlock("2", BlockType.IMAGE, attachmentId = inDraft.id))))
        // Just added: its block may not be saved yet.
        assertThat(attachments.deleteUnused(noteId)).isEqualTo(0)
        graph.time.advance(Duration.ofMinutes(11))
        assertThat(attachments.deleteUnused(noteId)).isEqualTo(1)
        assertThat(attachments.get(removed.id)).isNull()
        assertThat(files()).containsExactly(kept.fileName, inDraft.fileName)

        // Locked notes keep everything (their body cannot be read).
        graph.notes.clearDraft(noteId)
        graph.db.noteDao().setLocked(noteId, true, byteArrayOf(1), NoteDocument.EMPTY.encode(), 0)
        assertThat(attachments.deleteUnusedEverywhere()).isEqualTo(0)
        graph.db.noteDao().setLocked(noteId, false, null, NoteDocument(blocks = listOf(NoteBlock("1", BlockType.IMAGE, attachmentId = kept.id))).encode(), 0)
        assertThat(attachments.deleteUnusedEverywhere()).isEqualTo(1)
        assertThat(files()).containsExactly(kept.fileName)
    }

    @Test
    fun trashedNotesKeepTheirFiles_andPermanentDeletionRemovesThem() = runBlocking<Unit> {
        graph.pro = true
        val image = attachments.addImage(noteId, AttachmentKind.IMAGE, "a", bytes(10))
        write(NoteBlock("1", BlockType.IMAGE, attachmentId = image.id))
        graph.notes.deleteNote(noteId)
        assertThat(files()).containsExactly(image.fileName)
        assertThat(attachments.get(image.id)).isNotNull()
        graph.notes.deleteNotePermanently(noteId)
        assertThat(files()).isEmpty()
        assertThat(attachments.get(image.id)).isNull()
    }

    @Test
    fun duplicatingANote_copiesItsFiles() = runBlocking<Unit> {
        val image = attachments.addImage(noteId, AttachmentKind.IMAGE, "a", bytes(10))
        val drawing = Drawing(strokes = listOf(Stroke(0xFF000000, 3f, listOf(1, 1, 100))))
        val (preview, vector) = attachments.saveDrawing(noteId, null, null, drawing, byteArrayOf(9, 9), 100, 60)
        write(
            NoteBlock("1", BlockType.IMAGE, attachmentId = image.id),
            NoteBlock("2", BlockType.DRAWING, attachmentId = preview.id, data = RichBlocks.encode(DrawingRef(vector.id))),
        )
        val copyId = graph.notes.duplicateNote(noteId, "copy")
        val copy = graph.notes.getNote(copyId)!!
        val copied = attachments.observeForNote(copyId).first()
        assertThat(copied).hasSize(3)
        assertThat(copy.document.attachmentIds()).isEqualTo(copied.map { it.id }.toSet())
        assertThat(copied.map { it.fileName }.intersect(listOf(image.fileName, preview.fileName, vector.fileName).toSet())).isEmpty()
        assertThat(attachments.readDrawing(RichBlocks.drawing(copy.document.blocks[1]).vectorId!!)).isEqualTo(drawing)
        // Deleting the original leaves the copy's files alone.
        graph.notes.deleteNotePermanently(noteId)
        assertThat(copied.all { attachments.file(it).isFile }).isTrue()
    }

    @Test
    fun drawings_areReplacedInPlace() = runBlocking<Unit> {
        val first = Drawing(strokes = listOf(Stroke(1, 2f, listOf(0, 0, 50))))
        val (preview, vector) = attachments.saveDrawing(noteId, null, null, first, byteArrayOf(1), 10, 10)
        val second = first.plus(Stroke(2, 2f, listOf(5, 5, 50)))
        val (preview2, vector2) = attachments.saveDrawing(noteId, preview.id, vector.id, second, byteArrayOf(1, 2), 10, 10)
        assertThat(preview2.id to vector2.id).isEqualTo(preview.id to vector.id)
        assertThat(preview2.fileName).isNotEqualTo(preview.fileName)
        assertThat(attachments.readDrawing(vector.id)).isEqualTo(second)
        assertThat(files()).containsExactly(preview2.fileName, vector2.fileName)
        assertThat(vector2.mimeType).isEqualTo("application/vnd.planb.drawing+json")
    }
}
