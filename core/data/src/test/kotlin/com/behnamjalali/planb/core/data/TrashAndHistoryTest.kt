package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineActivityRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineProjectRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.data.repository.OfflineTrashRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.security.NoteVault
import com.behnamjalali.planb.core.data.security.SecurityPreferences
import com.behnamjalali.planb.core.data.security.VaultLockedException
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.TrashItemType
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro #36 (locked notes) and #38 (trash and activity history) in the repositories. */
@RunWith(RobolectricTestRunner::class)
class TrashAndHistoryTest {
    private lateinit var db: PlanBDatabase
    private val time = FakeTimeProvider()
    private val reminders = RecordingReminderScheduler()
    private var pro = true
    private lateinit var history: DataHistory
    private lateinit var tasks: OfflineTaskRepository
    private lateinit var notes: OfflineNoteRepository
    private lateinit var projects: OfflineProjectRepository
    private lateinit var trash: OfflineTrashRepository
    private lateinit var activity: OfflineActivityRepository
    private lateinit var search: FtsSearchRepository
    private lateinit var vault: NoteVault
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsDir: File = Files.createTempDirectory("planb-vault").toFile()

    @Before
    fun setUp() {
        db = TestDatabase.create()
        history = DataHistory(db, time) { pro }
        val prefs = SecurityPreferences(createPreferencesDataStore(scope) { File(prefsDir, "s.preferences_pb") })
        vault = NoteVault(prefs, 1_000, Dispatchers.Default)
        tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, reminders, history)
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time, history, vault)
        projects = OfflineProjectRepository(db, db.projectDao(), db.tagDao(), db.searchDao(), time, history)
        trash = OfflineTrashRepository(tasks, notes, db.taskDao(), db.noteDao(), time)
        activity = OfflineActivityRepository(db.activityLogDao())
        search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        prefsDir.deleteRecursively()
    }

    private suspend fun allTasks() = tasks.observeTasks(TaskFilter(TaskView.ALL, today = time.today(), topLevelOnly = false)).first()

    @Test
    fun freeUsers_deletePermanently_andNothingIsLogged() = runTest {
        pro = false
        val id = tasks.save(Task(title = "Free task", dueDate = time.today(), reminderOffsetMinutes = 5))
        tasks.delete(listOf(id))
        assertThat(db.taskDao().getEntity(id)).isNull()
        assertThat(trash.observeTrash().first()).isEmpty()
        assertThat(db.activityLogDao().count()).isEqualTo(0)
    }

    @Test
    fun proDelete_movesTaskAndSubtasksToTrash_andRestoreBringsThemBack() = runTest {
        val parent = tasks.save(Task(title = "گزارش فصلی", dueDate = time.today(), reminderOffsetMinutes = 10))
        val sub = tasks.save(Task(title = "Slides", parentTaskId = parent))
        tasks.delete(listOf(parent))

        assertThat(allTasks()).isEmpty()
        assertThat(search.search("گزارش")).isEmpty()
        assertThat(reminders.cancelled).containsAtLeast("task:$parent", "task:$sub")
        val items = trash.observeTrash().first()
        assertThat(items.map { it.id }).containsExactly(parent)
        assertThat(items.single().childCount).isEqualTo(1)
        assertThat(items.single().type).isEqualTo(TrashItemType.TASK)

        reminders.synced.clear()
        trash.restore(items.single())
        assertThat(allTasks().map { it.id }).containsExactly(parent, sub)
        assertThat(search.search("گزارش").map { it.id }).containsExactly(parent)
        assertThat(reminders.synced).containsAtLeast("task:$parent", "task:$sub")
        assertThat(trash.observeTrash().first()).isEmpty()
        val actions = activity.observeFor(ActivityEntityType.TASK, parent).first().map { it.action }
        assertThat(actions).containsAtLeast(ActivityAction.CREATED, ActivityAction.DELETED, ActivityAction.RESTORED)
    }

    @Test
    fun restoringASubtask_bringsBackItsTrashedParent() = runTest {
        val parent = tasks.save(Task(title = "Parent"))
        val sub = tasks.save(Task(title = "Child", parentTaskId = parent))
        tasks.delete(listOf(sub))
        time.advance(Duration.ofMinutes(1))
        tasks.delete(listOf(parent))
        assertThat(trash.observeTrash().first().map { it.id }).containsExactly(parent, sub)
        tasks.restoreFromTrash(sub)
        assertThat(allTasks().map { it.id }).containsExactly(parent, sub)
    }

    @Test
    fun purge_deletesOnlyItemsOlderThan30Days_andEmptyRemovesTheRest() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "NB"))
        val old = tasks.save(Task(title = "Old"))
        val oldNote = notes.saveNote(Note(notebookId = notebook, title = "Old note"))
        tasks.delete(listOf(old))
        notes.deleteNote(oldNote)
        time.advance(Duration.ofDays(20))
        val recent = tasks.save(Task(title = "Recent"))
        tasks.delete(listOf(recent))
        time.advance(Duration.ofDays(11))

        assertThat(trash.purgeExpired()).isEqualTo(2)
        assertThat(db.taskDao().getEntity(old)).isNull()
        assertThat(db.noteDao().getNote(oldNote)).isNull()
        assertThat(trash.observeTrash().first().map { it.id }).containsExactly(recent)

        assertThat(trash.emptyTrash()).isEqualTo(1)
        assertThat(db.taskDao().getEntity(recent)).isNull()
    }

    @Test
    fun notes_goToTrash_leaveSearch_andComeBackSearchable() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "NB"))
        val id = notes.saveNote(Note(notebookId = notebook, title = "Recipe", document = doc("cardamom tea")))
        notes.deleteNote(id)
        assertThat(search.search("cardamom")).isEmpty()
        assertThat(notes.observeNotes(notebook).first()).isEmpty()
        val item = trash.observeTrash().first().single()
        assertThat(item.type).isEqualTo(TrashItemType.NOTE)
        trash.restore(item)
        assertThat(search.search("cardamom").map { it.id }).containsExactly(id)
        trash.observeTrash().first().let { assertThat(it).isEmpty() }
        // The editor's discard of an empty note is always permanent.
        notes.deleteNotePermanently(id)
        assertThat(db.noteDao().getNote(id)).isNull()
    }

    @Test
    fun history_mergesBurstsOfEdits_andNeverStoresNoteBodies() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "NB"))
        val id = notes.saveNote(Note(notebookId = notebook, title = ""))
        repeat(5) { i ->
            time.advance(Duration.ofSeconds(5))
            notes.updateContent(id, "Diary", doc("very private body $i"))
        }
        val entries = activity.observeFor(ActivityEntityType.NOTE, id).first()
        // Created and edited right away: one entry that carries the latest title.
        assertThat(entries.map { it.action }).containsExactly(ActivityAction.CREATED)
        assertThat(entries.single().summary).isEqualTo("Diary")

        time.advance(Duration.ofMinutes(30))
        notes.updateContent(id, "Diary", doc("later body"))
        time.advance(Duration.ofMinutes(1))
        notes.updateContent(id, "Diary 2", doc("later body 2"))
        val later = activity.observeFor(ActivityEntityType.NOTE, id).first()
        assertThat(later.map { it.action }).containsExactly(ActivityAction.UPDATED, ActivityAction.CREATED).inOrder()
        assertThat(later.first().summary).isEqualTo("Diary 2")
        assertThat(db.backupDao().activityLog().joinToString { it.summary }).doesNotContain("body")
    }

    @Test
    fun history_coversCompletionArchiveAndOtherItems_andIsCapped() = runTest {
        val id = tasks.save(Task(title = "Run"))
        tasks.setCompleted(id, true)
        tasks.setCompleted(id, false)
        tasks.setArchived(listOf(id), true)
        val project = projects.save(Project(title = "Launch"))
        projects.delete(project)
        assertThat(activity.observeFor(ActivityEntityType.TASK, id).first().map { it.action })
            .containsExactly(ActivityAction.ARCHIVED, ActivityAction.REOPENED, ActivityAction.COMPLETED, ActivityAction.CREATED).inOrder()
        assertThat(activity.observeRecent(ActivityEntityType.PROJECT).first().map { it.action })
            .containsExactly(ActivityAction.DELETED, ActivityAction.CREATED).inOrder()

        repeat(DataHistory.MAX_ENTRIES + 50) { history.record(ActivityEntityType.GOAL, it.toLong(), ActivityAction.DELETED, "g") }
        activity.prune()
        assertThat(db.activityLogDao().count()).isEqualTo(DataHistory.MAX_ENTRIES)
    }

    @Test
    fun lockedNote_storesOnlyCiphertext_andItsBodyLeavesSearchDraftsAndVersions() = runTest {
        val notebook = notes.saveNotebook(Notebook(title = "NB"))
        val id = notes.saveNote(Note(notebookId = notebook, title = "Bank", document = doc("PIN 4321 zebra")))
        db.noteVersionDao().insert(NoteVersionEntity(noteId = id, createdAt = time.now(), title = "Bank", content = doc("PIN 4321 zebra").encode(), size = 10))
        notes.saveDraft(id, "Bank", doc("draft zebra"))

        assertThrows(VaultLockedException::class.java) { kotlinx.coroutines.runBlocking { notes.lockNote(id) } }
        vault.setUp("long enough phrase".toCharArray())
        notes.lockNote(id)

        val stored = db.noteDao().getNote(id)!!
        assertThat(stored.locked).isTrue()
        assertThat(stored.encryptedPayload).isNotNull()
        assertThat(stored.content).doesNotContain("zebra")
        assertThat(String(stored.encryptedPayload!!, Charsets.ISO_8859_1)).doesNotContain("zebra")
        assertThat(search.search("zebra")).isEmpty()
        assertThat(search.search("Bank").map { it.id }).containsExactly(id)
        assertThat(db.backupDao().noteVersions()).isEmpty()
        assertThat(notes.getDraft(id)).isNull()
        assertThat(notes.getNote(id)!!.document.plainText()).doesNotContain("zebra")

        // Editing keeps it encrypted; drafts are never written for it.
        notes.updateContent(id, "Bank", doc("PIN 9999 zebra"))
        notes.saveDraft(id, "Bank", doc("draft zebra"))
        assertThat(db.noteDraftDao().get(id)).isNull()
        assertThat(db.noteDao().getNote(id)!!.content).doesNotContain("zebra")
        assertThat(notes.lockedContent(id).plainText()).contains("PIN 9999")

        vault.lock()
        assertThrows(VaultLockedException::class.java) { kotlinx.coroutines.runBlocking { notes.lockedContent(id) } }
        assertThrows(VaultLockedException::class.java) { kotlinx.coroutines.runBlocking { notes.updateContent(id, "Bank", doc("leak")) } }

        assertThat(vault.unlock("long enough phrase".toCharArray())).isTrue()
        notes.removeLock(id)
        assertThat(db.noteDao().getNote(id)!!.locked).isFalse()
        assertThat(db.noteDao().getNote(id)!!.encryptedPayload).isNull()
        assertThat(search.search("zebra").map { it.id }).containsExactly(id)
    }

    private fun doc(text: String) = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, text)))
}
