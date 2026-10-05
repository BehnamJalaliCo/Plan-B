package com.behnamjalali.planb.core.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.AttachmentLimits
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.data.repository.OfflineAttachmentRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.DrawingRef
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.Stroke
import com.behnamjalali.planb.core.model.rich.TableData
import com.behnamjalali.planb.core.testing.CopyImageProcessor
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro rich notes (#15, #17, #19): every kind of attachment survives a backup and the Markdown export. */
@RunWith(RobolectricTestRunner::class)
class RichNotesBackupTest {
    private object NoReminders : ReminderScheduler {
        override suspend fun syncTask(taskId: EntityId) = Unit
        override suspend fun syncEvent(eventId: EntityId) = Unit
        override suspend fun syncHabit(habitId: EntityId) = Unit
        override suspend fun cancelTask(taskId: EntityId) = Unit
        override suspend fun cancelEvent(eventId: EntityId) = Unit
        override suspend fun cancelHabit(habitId: EntityId) = Unit
        override suspend fun rescheduleAll() = Unit
        override fun scheduleFocusEnd(at: Instant) = Unit
        override fun cancelFocusEnd() = Unit
    }

    private lateinit var db: PlanBDatabase
    private lateinit var manager: BackupManager
    private lateinit var notes: OfflineNoteRepository
    private lateinit var attachments: OfflineAttachmentRepository
    private val time = FakeTimeProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filesRoot: File = Files.createTempDirectory("files").toFile()
    private val files = AttachmentFiles(filesRoot)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()
        val dir = Files.createTempDirectory("prefs").toFile()
        val prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") })
        manager = BackupManager(
            db, db.backupDao(), prefs, DocumentFiles(context, Dispatchers.IO), SearchIndexMaintenance(db.backupDao(), db.searchDao()),
            NoReminders, time, AppVersion("1.0.0", 1), files,
        )
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time, attachmentFiles = files)
        attachments = OfflineAttachmentRepository(db, files, CopyImageProcessor(), time)
    }

    @After
    fun tearDown() {
        db.close()
        filesRoot.deleteRecursively()
    }

    private fun bytes(seed: Int, size: Int = 500) = ByteArray(size) { ((it * seed) % 251).toByte() }

    private val drawing = Drawing(strokes = listOf(Stroke(0xFF3366FF, 4f, listOf(10, 10, 80, 200, 120, 100))))

    /** A note with a photo, a scan with recognized text, a file, a recording with a transcript and a drawing. */
    private suspend fun seedRichNote(): Long {
        val notebook = notes.saveNotebook(Notebook(title = "دفتر"))
        val noteId = notes.saveNote(Note(notebookId = notebook, title = "جلسه"))
        val photo = attachments.addImage(noteId, AttachmentKind.IMAGE, "photo.jpg") { ByteArrayInputStream(bytes(3)) }
        val scan = attachments.addImage(noteId, AttachmentKind.SCAN, "scan") { ByteArrayInputStream(bytes(5)) }
        attachments.setOcrText(scan.id, "صورتحساب INVOICE 42")
        val pdf = attachments.addFile(noteId, "گزارش.pdf", "application/pdf") { ByteArrayInputStream(bytes(7)) }
        val recording = File.createTempFile("rec", ".m4a").apply { writeBytes(bytes(11)) }
        val audio = attachments.addRecording(noteId, recording, 4_200, "Voice")
        attachments.setTranscript(audio.id, "یادآوری خرید")
        val (preview, vector) = attachments.saveDrawing(noteId, null, null, drawing, bytes(13, 200), 300, 188)
        notes.updateContent(
            noteId, "جلسه",
            NoteDocument(
                blocks = listOf(
                    NoteBlock("p", BlockType.IMAGE, "Whiteboard", attachmentId = photo.id),
                    NoteBlock("s", BlockType.SCAN, attachmentId = scan.id),
                    NoteBlock("f", BlockType.FILE, attachmentId = pdf.id),
                    NoteBlock("a", BlockType.AUDIO, attachmentId = audio.id),
                    NoteBlock("d", BlockType.DRAWING, attachmentId = preview.id, data = RichBlocks.encode(DrawingRef(vector.id, 300, 188))),
                    NoteBlock("t", BlockType.TABLE, data = RichBlocks.encode(TableData(listOf(listOf("a", "b"), listOf("1", "2"))))),
                ),
            ),
        )
        return noteId
    }

    @Test
    fun attachmentLimits_matchTheBackupLimits() {
        assertThat(AttachmentLimits.MAX_FILE_BYTES).isEqualTo(BackupFormat.MAX_ATTACHMENT_BYTES)
        assertThat(AttachmentLimits.MAX_TOTAL_BYTES).isEqualTo(BackupFormat.MAX_ATTACHMENTS_TOTAL_BYTES)
        assertThat(AttachmentLimits.MAX_COUNT).isEqualTo(BackupFormat.MAX_ATTACHMENTS)
    }

    @Test
    fun everyAttachmentKind_roundTripsThroughABackup() = runBlocking<Unit> {
        val noteId = seedRichNote()
        val dao = db.backupDao()
        val rowsBefore = dao.attachments()
        val contentBefore = files.directory.listFiles()!!.associate { it.name to it.readBytes() }
        assertThat(rowsBefore.map { it.kind }).containsExactly("IMAGE", "SCAN", "FILE", "AUDIO", "DRAWING", "DRAWING")
        val noteBefore = notes.getNote(noteId)!!.document

        val out = ByteArrayOutputStream()
        BackupCodec.write(manager.snapshot(), out)
        // Lose everything, then restore.
        notes.deleteNotePermanently(noteId)
        files.deleteAll()
        val archive = BackupCodec.read(ByteArrayInputStream(out.toByteArray()), stagingDirectory = files.newStagingDirectory())
        manager.restore(archive)

        assertThat(dao.attachments()).isEqualTo(rowsBefore)
        assertThat(files.directory.listFiles()!!.associate { it.name to it.readBytes().toList() })
            .isEqualTo(contentBefore.mapValues { it.value.toList() })
        assertThat(notes.getNote(noteId)!!.document).isEqualTo(noteBefore)
        val vectorId = RichBlocks.drawing(noteBefore.blocks[4]).vectorId!!
        assertThat(attachments.readDrawing(vectorId)).isEqualTo(drawing)
        // Recognized text and transcripts are searchable again after the restore.
        assertThat(db.searchDao().search("invoice*", 10).map { it.entityId }).containsExactly(noteId)
        assertThat(db.searchDao().search("خرید*", 10).map { it.entityId }).containsExactly(noteId)
    }

    @Test
    fun markdownZip_includesTheFilesAndLinksThem() = runBlocking<Unit> {
        seedRichNote()
        val transfer = DataTransfer(
            db.backupDao(),
            OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, NoReminders),
            OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time),
            DocumentFiles(context, Dispatchers.IO),
            files,
        )
        val zipFile = File(Files.createTempDirectory("export").toFile(), "notes.zip")
        transfer.exportNotesMarkdownZip(Uri.fromFile(zipFile))
        ZipFile(zipFile).use { zip ->
            val names = zip.entries().toList().map { it.name }
            // The note plus five files (photo, scan, PDF, recording and the drawing as PNG).
            assertThat(names).hasSize(6)
            assertThat(names).contains("دفتر/جلسه.md")
            val md = zip.getInputStream(zip.getEntry("دفتر/جلسه.md")).readBytes().toString(Charsets.UTF_8)
            val links = Regex("""\]\((attachments/[^)]+)\)""").findAll(md).map { it.groupValues[1] }.toList()
            assertThat(links).hasSize(5)
            links.forEach { assertThat(names).contains("دفتر/$it") }
            assertThat(md).contains("> صورتحساب INVOICE 42")
            assertThat(md).contains("> یادآوری خرید")
            assertThat(md).contains("| a | b |")
            val photo = db.backupDao().attachments().first { it.kind == "IMAGE" }
            assertThat(zip.getInputStream(zip.getEntry("دفتر/attachments/${photo.fileName}")).readBytes()).isEqualTo(bytes(3))
        }
    }
}
