package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.NoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteHistoryRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteLinkRepository
import com.behnamjalali.planb.core.data.repository.OfflineNoteRepository
import com.behnamjalali.planb.core.data.repository.OfflineRitualJournalRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro notes knowledge in the data layer: links (#16), history (#16), graph (#21) and the journal (#25). */
@RunWith(RobolectricTestRunner::class)
class NoteKnowledgeRepositoryTest {
    private lateinit var db: PlanBDatabase
    private lateinit var notes: OfflineNoteRepository
    private lateinit var links: OfflineNoteLinkRepository
    private lateinit var history: OfflineNoteHistoryRepository
    private lateinit var journal: OfflineJournalRepository
    private val time = FakeTimeProvider()
    private var pro = true
    private var notebook = 0L

    @Before
    fun setUp() = runTest {
        db = TestDatabase.create()
        val status = ProStatusSource { pro }
        notes = OfflineNoteRepository(db, db.noteDao(), db.noteDraftDao(), db.tagDao(), db.searchDao(), time, DataHistory(db, time, status))
        links = OfflineNoteLinkRepository(db)
        history = OfflineNoteHistoryRepository(db, notes, time, status)
        journal = OfflineJournalRepository(db, notes, time)
        notebook = notes.saveNotebook(Notebook(title = "Notes"))
    }

    @After
    fun tearDown() = db.close()

    private fun doc(vararg texts: String) = NoteDocument(blocks = texts.mapIndexed { i, t -> NoteBlock("b$i", BlockType.TEXT, t) })

    private suspend fun note(title: String, vararg texts: String) = notes.saveNote(Note(notebookId = notebook, title = title, document = doc(*texts)))

    // region links

    @Test
    fun saving_maintainsOutgoingLinksAndBacklinks() = runTest {
        val trip = note("Travel plan")
        val budget = note("Budget")
        val index = note("Index", "See ${NoteLinks.token(trip, "Travel plan")} and ${NoteLinks.token(budget, "Budget")}")
        assertThat(db.noteLinkDao().observeOutgoing(index).first()).containsExactly(trip, budget)
        assertThat(links.observeBacklinks(trip).first().map { it.title }).containsExactly("Index")

        // Autosave rewrites the links: one removed, a self-link and a link to a missing note ignored.
        notes.updateContent(index, "Index", doc("Only ${NoteLinks.token(budget, "Budget")} ${NoteLinks.token(index, "me")} ${NoteLinks.token(999, "gone")}"))
        assertThat(db.noteLinkDao().observeOutgoing(index).first()).containsExactly(budget)
        assertThat(links.observeBacklinks(trip).first()).isEmpty()
    }

    @Test
    fun renamesAndTrash_areSeenByReferencesAndBacklinks() = runTest {
        val target = note("Old name")
        val source = note("Source", NoteLinks.token(target, "Old name"))
        notes.updateContent(target, "New name", doc())
        assertThat(links.refs(setOf(target)).getValue(target).title).isEqualTo("New name")

        // A trashed note (Pro) is still a reference, marked as trashed; its backlinks are hidden.
        notes.deleteNote(source)
        assertThat(links.observeBacklinks(target).first()).isEmpty()
        notes.deleteNote(target)
        assertThat(links.observeRefs(setOf(target)).first().getValue(target).trashed).isTrue()
        // Deleted for good: the link token stays as text, the reference is gone.
        notes.deleteNotePermanently(target)
        assertThat(links.refs(setOf(target))).isEmpty()
        assertThat(db.noteLinkDao().observeOutgoing(source).first()).isEmpty()
    }

    @Test
    fun duplicate_copiesLinks_andLinkTitlesAreSearchable() = runTest {
        val target = note("سفر شمال")
        val source = note("Plans", "برو به ${NoteLinks.token(target, "سفر شمال")}")
        val copy = notes.duplicateNote(source, "copy")
        assertThat(db.noteLinkDao().observeOutgoing(copy).first()).containsExactly(target)
        // The index holds the link's title, not the markup.
        val hits = db.searchDao().search(SearchIndexer.matchQuery("شمال")!!, 20).filter { it.entityType == SearchEntityType.NOTE.code }.map { it.entityId }
        assertThat(hits).containsAtLeast(target, source)
        assertThat(db.searchDao().search(SearchIndexer.matchQuery("note")!!, 20).filter { it.entityType == SearchEntityType.NOTE.code }).isEmpty()
    }

    @Test
    fun searchNotes_matchesNormalizedTitlePrefixes() = runTest {
        val a = note("كتاب" + Char(0x200C) + "های خوب") // Arabic kaf, half-space
        note("Books to read")
        val c = note("Reading list")
        assertThat(links.searchNotes("کتاب").map { it.id }).containsExactly(a)
        assertThat(links.searchNotes("read").map { it.title }).containsExactly("Reading list", "Books to read").inOrder()
        assertThat(links.searchNotes("read", excludeId = c).map { it.title }).containsExactly("Books to read")
        assertThat(links.searchNotes("").size).isEqualTo(3)
    }

    @Test
    fun graph_containsLiveNotesLinksAndTags() = runTest {
        val a = note("A")
        val b = note("B", NoteLinks.token(a, "A"))
        val orphan = note("Orphan")
        notes.setTags(orphan, listOf(com.behnamjalali.planb.core.model.Tag(name = "solo")))
        val graph = links.observeGraph().first()
        assertThat(graph.notes.map { it.id }).containsExactly(a, b, orphan)
        assertThat(graph.edges).containsExactly(b to a)
        assertThat(graph.tags.getValue(orphan)).hasSize(1)
    }

    // endregion

    // region history

    @Test
    fun snapshot_respectsIntervalForceAndDuplicates() = runTest {
        val id = note("Draft", "one")
        assertThat(history.snapshot(id)).isTrue()
        // Unchanged: never a duplicate, even when forced.
        assertThat(history.snapshot(id, force = true)).isFalse()
        notes.updateContent(id, "Draft", doc("two"))
        // Within 10 minutes only a forced snapshot (closing the editor) is taken.
        time.advance(Duration.ofMinutes(5))
        assertThat(history.snapshot(id)).isFalse()
        time.advance(Duration.ofMinutes(6))
        assertThat(history.snapshot(id)).isTrue()
        notes.updateContent(id, "Draft", doc("three"))
        assertThat(history.snapshot(id, force = true)).isTrue()
        assertThat(history.observeVersions(id).first().map { it.document.blocks.single().text }).containsExactly("three", "two", "one").inOrder()
    }

    @Test
    fun snapshot_needsProAndSkipsLockedNotes() = runTest {
        val id = note("Secret", "text")
        pro = false
        assertThat(history.snapshot(id, force = true)).isFalse()
        pro = true
        db.noteDao().setLocked(id, true, byteArrayOf(1, 2, 3), NoteDocument.EMPTY.encode(), time.now().toEpochMilli())
        assertThat(history.snapshot(id, force = true)).isFalse()
        assertThat(history.observeVersions(id).first()).isEmpty()
    }

    @Test
    fun retention_keepsFiftyAndDropsOlderThanNinetyDays() = runTest {
        val id = note("Busy", "v0")
        repeat(60) { i ->
            notes.updateContent(id, "Busy", doc("v${i + 1}"))
            assertThat(history.snapshot(id, force = true)).isTrue()
            time.advance(Duration.ofMinutes(1))
        }
        val kept = history.observeVersions(id).first()
        assertThat(kept).hasSize(NoteHistoryRepository.MAX_VERSIONS)
        assertThat(kept.first().document.blocks.single().text).isEqualTo("v60")
        assertThat(kept.last().document.blocks.single().text).isEqualTo("v11")

        // 91 days later, a new version drops everything older than 90 days.
        time.advance(Duration.ofDays(91))
        notes.updateContent(id, "Busy", doc("later"))
        assertThat(history.snapshot(id)).isTrue()
        assertThat(history.observeVersions(id).first().map { it.document.blocks.single().text }).containsExactly("later")
    }

    @Test
    fun restore_savesTheCurrentStateFirst() = runTest {
        val id = note("Essay", "first draft")
        history.snapshot(id, force = true)
        val firstVersion = history.observeVersions(id).first().single()
        time.advance(Duration.ofMinutes(1))
        notes.updateContent(id, "Essay v2", doc("second draft"))
        time.advance(Duration.ofMinutes(1))

        history.restore(firstVersion.id)
        val restored = notes.getNote(id)!!
        assertThat(restored.title).isEqualTo("Essay")
        assertThat(restored.document.blocks.single().text).isEqualTo("first draft")
        // The state that was replaced is now a version too.
        assertThat(history.observeVersions(id).first().map { it.title }).containsExactly("Essay v2", "Essay").inOrder()
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { history.restore(12345) } }
    }

    // endregion

    // region journal

    @Test
    fun journalPage_isSharedWithRitualReflections() = runTest {
        val today = time.today()
        val rituals = OfflineRitualJournalRepository(db, notes, time)
        val ritualPage = rituals.append(today, "Intention", "Focus on writing", "Journal", "Today", "ritual_morning")
        // The journal opens the same page and its note keeps the reflection.
        val page = journal.openPage(today, "Journal", "Today", "p01", "What are you grateful for?")
        assertThat(page).isEqualTo(ritualPage)
        assertThat(notes.getNote(page)!!.document.blocks.map { it.text }).contains("Focus on writing")
        // A new day gets its own page in the same notebook, starting with the prompt.
        val tomorrow = journal.openPage(today.plusDays(1), "Journal", "Tomorrow", "p02", "Prompt two")
        val note = notes.getNote(tomorrow)!!
        assertThat(note.notebookId).isEqualTo(notes.getNote(page)!!.notebookId)
        assertThat(note.document.blocks.first().type).isEqualTo(BlockType.QUOTE)
        assertThat(journal.entryOn(today.plusDays(1))!!.promptId).isEqualTo("p02")
        assertThat(journal.observePageDates(today, today.plusDays(5)).first()).containsExactly(today, today.plusDays(1))
        // Pages are found by search like any note.
        assertThat(db.searchDao().search(SearchIndexer.matchQuery("writing")!!, 10).map { it.entityId }).contains(page)
    }

    @Test
    fun journalPage_inTheTrashIsReplaced_andMoodIsOnePerPage() = runTest {
        val today = time.today()
        val first = journal.openPage(today, "Journal", "Today", null, null)
        journal.setPageMood(today, first, mood = 4, energy = null)
        journal.setPageMood(today, first, mood = 5, energy = 2)
        assertThat(journal.observeMoods(today, today).first().map { it.mood to it.energy }).containsExactly(5 to 2)
        journal.setTags(first, listOf("#calm", "work", "calm"))
        assertThat(journal.observeTags(first).first()).containsExactly("calm", "work").inOrder()

        notes.deleteNote(first) // Pro: to the trash
        assertThat(journal.observePages(today, today).first()).isEmpty()
        val second = journal.openPage(today, "Journal", "Today", null, null)
        assertThat(second).isNotEqualTo(first)
        assertThat(journal.observePages(today, today).first().single().noteId).isEqualTo(second)
        journal.setPageMood(today, second, mood = null, energy = null)
        assertThat(journal.pageMood(today, second)).isNull()
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { journal.setPageMood(today, second, 6, null) } }
    }

    @Test
    fun journalPreview_skipsThePromptAndHidesLockedPages() = runTest {
        val today = time.today()
        val id = journal.openPage(today, "Journal", "Today", "p01", "A prompt")
        val page = notes.getNote(id)!!
        notes.updateContent(id, page.title, page.document.copy(blocks = page.document.blocks + NoteBlock("x", BlockType.TEXT, "I walked by the sea")))
        assertThat(journal.observePages(today, today).first().single().preview).isEqualTo("I walked by the sea")
        db.noteDao().setLocked(id, true, byteArrayOf(1), NoteDocument.EMPTY.encode(), time.now().toEpochMilli())
        val locked = journal.observePages(today, today).first().single()
        assertThat(locked.locked).isTrue()
        assertThat(locked.preview).isEmpty()
    }

    // endregion
}
