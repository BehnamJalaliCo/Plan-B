package com.behnamjalali.planb.core.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.data.DataHistory
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexMaintenance
import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan-B Pro notes knowledge in backups (format 2): links, versions, journal pages and mood
 * check-ins made through the repositories round-trip, writing-goal and journal preferences are
 * part of the exported preferences, and the Markdown export writes relative links.
 */
@RunWith(RobolectricTestRunner::class)
class NotesKnowledgeBackupTest {
    private lateinit var db: PlanBDatabase
    private lateinit var manager: BackupManager
    private lateinit var prefs: UserPreferencesDataSource
    private lateinit var notes: OfflineNoteRepository
    private val time = FakeTimeProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val filesRoot: File = Files.createTempDirectory("files").toFile()
    private val reminders = object : ReminderScheduler {
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()
        val dir = Files.createTempDirectory("prefs").toFile()
        prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") })
        manager = BackupManager(
            db, db.backupDao(), prefs, DocumentFiles(context, Dispatchers.IO), SearchIndexMaintenance(db.backupDao(), db.searchDao()),
            reminders, time, AppVersion("1.0.0", 1), AttachmentFiles(filesRoot),
        )
        val pro = ProStatusSource { true }
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time, DataHistory(db, time, pro))
    }

    @After
    fun tearDown() {
        db.close()
        filesRoot.deleteRecursively()
    }

    private fun doc(text: String) = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.TEXT, text)))

    @Test
    fun linksVersionsJournalAndMoods_roundTrip() = runBlocking<Unit> {
        val history = OfflineNoteHistoryRepository(db, notes, time, ProStatusSource { true })
        val journal = OfflineJournalRepository(db, notes, time)
        val notebook = notes.saveNotebook(Notebook(title = "دفتر"))
        val target = notes.saveNote(Note(notebookId = notebook, title = "مقصد", document = doc("target")))
        val source = notes.saveNote(Note(notebookId = notebook, title = "Source", document = doc("see ${NoteLinks.token(target, "مقصد")}")))
        history.snapshot(source, force = true)
        time.advance(Duration.ofMinutes(15))
        notes.updateContent(source, "Source", doc("now ${NoteLinks.token(target, "مقصد")}"))
        history.snapshot(source)
        val page = journal.openPage(time.today(), "دفتر روزانه", "Today", "p03", "Prompt")
        journal.setPageMood(time.today(), page, mood = 4, energy = 5)
        prefs.update {
            it.copy(
                writing = it.writing.copy(dailyGoal = 750, typewriter = true, streak = 3, goalReachedOn = time.today(), progressDate = time.today(), wordsToday = 800),
                journal = it.journal.copy(reminder = true, reminderTime = LocalTime.of(22, 15), customPrompts = listOf("What surprised you?", "سه چیز خوب امروز")),
            )
        }
        val before = db.backupDao()
        val links = before.noteLinks()
        val versions = before.noteVersions()
        val entries = before.journalEntries()
        val moods = before.moodEntries()
        assertThat(links).hasSize(1)
        assertThat(versions).hasSize(2)

        val out = ByteArrayOutputStream()
        BackupCodec.write(manager.snapshot(), out)
        val archive = BackupCodec.read(ByteArrayInputStream(out.toByteArray()))
        // Wipe everything, including preferences, then restore.
        db.backupDao().clearAll()
        prefs.update { it.copy(writing = it.writing.copy(dailyGoal = 0, typewriter = false, streak = 0), journal = it.journal.copy(reminder = false, customPrompts = emptyList())) }
        manager.restore(archive)

        val after = db.backupDao()
        assertThat(after.noteLinks()).isEqualTo(links)
        assertThat(after.noteVersions()).isEqualTo(versions)
        assertThat(after.journalEntries()).isEqualTo(entries)
        assertThat(after.moodEntries()).isEqualTo(moods)
        assertThat(NoteLinks.targets(notes.getNote(source)!!.document)).containsExactly(target)
        val restored = prefs.current()
        assertThat(restored.writing.dailyGoal).isEqualTo(750)
        assertThat(restored.writing.typewriter).isTrue()
        assertThat(restored.writing.streak).isEqualTo(3)
        assertThat(restored.writing.wordsOn(time.today())).isEqualTo(800)
        assertThat(restored.journal.reminder).isTrue()
        assertThat(restored.journal.reminderTime).isEqualTo(LocalTime.of(22, 15))
        assertThat(restored.journal.customPrompts).containsExactly("What surprised you?", "سه چیز خوب امروز").inOrder()
    }

    @Test
    fun markdownZip_writesRelativeLinksBetweenNotes() = runBlocking<Unit> {
        val travel = notes.saveNotebook(Notebook(title = "Travel"))
        val ideas = notes.saveNotebook(Notebook(title = "Ideas"))
        val trip = notes.saveNote(Note(notebookId = travel, title = "Trip", document = doc("x")))
        notes.saveNote(Note(notebookId = travel, title = "Packing", document = doc("for ${NoteLinks.token(trip, "Trip")}")))
        notes.saveNote(Note(notebookId = ideas, title = "Someday", document = doc("${NoteLinks.token(trip, "Trip")} and ${NoteLinks.token(999, "Gone")}")))
        val file = File(Files.createTempDirectory("export").toFile(), "notes.zip")
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataTransfer(
            db.backupDao(),
            OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders),
            OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time),
            DocumentFiles(context, Dispatchers.IO),
        ).exportNotesMarkdownZip(Uri.fromFile(file))
        val texts = java.util.zip.ZipFile(file).use { zip -> zip.entries().toList().associate { it.name to zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) } }
        assertThat(texts.getValue("Travel/Packing.md")).contains("for [Trip](<Trip.md>)")
        // Another folder, and a link whose note is gone degrades to its title.
        assertThat(texts.getValue("Ideas/Someday.md")).contains("[Trip](<../Travel/Trip.md>) and Gone")
        assertThat(texts.values.none { it.contains("[[note:") }).isTrue()
    }
}
