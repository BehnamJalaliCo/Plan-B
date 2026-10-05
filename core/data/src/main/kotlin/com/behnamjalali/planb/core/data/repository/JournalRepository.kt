package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.JournalEntryEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.JournalEntry
import com.behnamjalali.planb.core.model.JournalPage
import com.behnamjalali.planb.core.model.MoodEntry
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteLinks
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Tag
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The daily journal (Plan-B Pro #25). A page is a regular note in the journal notebook, linked
 * from `journal_entries` (one per date) exactly like the morning and evening ritual
 * reflections (#8), which append to the same page. Mood and energy are `mood_entries` rows; the
 * journal keeps one per page (linked by `note_id`), so other check-ins of the day (the mood
 * tracker) stay untouched. Tags are the page note's tags.
 */
interface JournalRepository {
    /** Pages in [from, to], newest first; pages whose note is in the trash are left out. */
    fun observePages(from: LocalDate, to: LocalDate): Flow<List<JournalPage>>

    /** Dates with a live page in [from, to]. */
    fun observePageDates(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>>

    fun observeMoods(from: LocalDate, to: LocalDate): Flow<List<MoodEntry>>

    suspend fun entryOn(date: LocalDate): JournalEntry?

    /**
     * The note of [date]'s page, created when missing (with [prompt] as its first block and
     * [promptId] recorded) in the notebook titled [notebookTitle] (created when no notebook has
     * that title). A page whose note went to the trash gets a new note.
     */
    suspend fun openPage(date: LocalDate, notebookTitle: String, pageTitle: String, promptId: String?, prompt: String?): EntityId

    /** Records the prompt shown on [date]'s page. */
    suspend fun setPrompt(date: LocalDate, promptId: String?)

    /** The page's own check-in (mood and energy 1..5, null = not set). */
    suspend fun pageMood(date: LocalDate, noteId: EntityId): MoodEntry?

    /** Sets the page's check-in; both null removes it. */
    suspend fun setPageMood(date: LocalDate, noteId: EntityId, mood: Int?, energy: Int?)

    fun observeTags(noteId: EntityId): Flow<List<String>>

    suspend fun setTags(noteId: EntityId, tags: List<String>)
}

@Singleton
class OfflineJournalRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val notes: NoteRepository,
    private val time: TimeProvider,
) : JournalRepository {
    private val journal get() = db.journalDao()
    private val knowledge get() = db.noteKnowledgeDao()

    override fun observePages(from: LocalDate, to: LocalDate): Flow<List<JournalPage>> =
        knowledge.observeJournalPages(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.map { row ->
                JournalPage(
                    entryId = row.id,
                    date = LocalDate.ofEpochDay(row.date),
                    noteId = row.noteId,
                    promptId = row.promptId,
                    title = row.title,
                    preview = if (row.locked) "" else preview(NoteDocument.decode(row.content)),
                    locked = row.locked,
                )
            }
        }

    /** The page's text without its prompt line (a quote block at the top), trimmed for a list row. */
    private fun preview(document: NoteDocument): String = document.blocks
        .dropWhile { it.type == BlockType.QUOTE }
        .filter { it.type != BlockType.DIVIDER && it.text.isNotBlank() }
        .take(3)
        .joinToString(" · ") { NoteLinks.plain(it.text).replace('\n', ' ').trim() }
        .take(PREVIEW)

    override fun observePageDates(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        knowledge.observeJournalDates(from.toEpochDay(), to.toEpochDay()).map { days -> days.map(LocalDate::ofEpochDay).toSet() }

    override fun observeMoods(from: LocalDate, to: LocalDate): Flow<List<MoodEntry>> =
        journal.observeMoods(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    override suspend fun entryOn(date: LocalDate): JournalEntry? = journal.entryOn(date.toEpochDay())?.toModel()

    override suspend fun openPage(date: LocalDate, notebookTitle: String, pageTitle: String, promptId: String?, prompt: String?): EntityId {
        val now = time.now()
        return db.withTransaction {
            val entry = journal.entryOn(date.toEpochDay())
            val page = entry?.let { notes.getNote(it.noteId) }
            if (entry != null && page != null && page.deletedAt == null) return@withTransaction page.id
            val notebook = notes.observeNotebooks(archived = false).first().firstOrNull { it.title.trim() == notebookTitle.trim() }?.id
                ?: notes.saveNotebook(Notebook(title = notebookTitle, icon = PlannerIcon.BOOK, color = AccentColor.SAND))
            val blocks = buildList {
                prompt?.takeIf { it.isNotBlank() }?.let { add(NoteBlock(UUID.randomUUID().toString(), BlockType.QUOTE, it.trim())) }
                add(NoteBlock(UUID.randomUUID().toString(), BlockType.TEXT, ""))
            }
            val id = notes.saveNote(Note(notebookId = notebook, title = pageTitle, document = NoteDocument(blocks = blocks)))
            if (entry != null) {
                journal.updateEntry(entry.copy(noteId = id, promptId = promptId ?: entry.promptId, updatedAt = now))
            } else {
                journal.insertEntry(JournalEntryEntity(date = date, noteId = id, promptId = promptId, createdAt = now, updatedAt = now))
            }
            id
        }
    }

    override suspend fun setPrompt(date: LocalDate, promptId: String?) {
        val entry = journal.entryOn(date.toEpochDay()) ?: return
        journal.updateEntry(entry.copy(promptId = promptId, updatedAt = time.now()))
    }

    override suspend fun pageMood(date: LocalDate, noteId: EntityId): MoodEntry? = knowledge.moodForPage(date.toEpochDay(), noteId)?.toModel()

    override suspend fun setPageMood(date: LocalDate, noteId: EntityId, mood: Int?, energy: Int?) {
        require(mood == null || mood in MoodEntry.RANGE) { "Mood must be 1..5" }
        require(energy == null || energy in MoodEntry.RANGE) { "Energy must be 1..5" }
        val now = time.now()
        db.withTransaction {
            val existing = knowledge.moodForPage(date.toEpochDay(), noteId)
            when {
                mood == null && energy == null -> existing?.let { journal.deleteMood(it.id) }
                existing == null -> journal.insertMood(
                    MoodEntryEntity(date = date, time = time.now().atZone(time.zone()).toLocalTime().withNano(0), mood = mood, energy = energy, noteId = noteId, createdAt = now, updatedAt = now),
                )
                else -> journal.updateMood(existing.copy(mood = mood, energy = energy, updatedAt = now))
            }
            journal.entryOn(date.toEpochDay())?.let { journal.updateEntry(it.copy(updatedAt = now)) }
        }
    }

    override fun observeTags(noteId: EntityId): Flow<List<String>> = knowledge.observeTagNames(noteId)

    override suspend fun setTags(noteId: EntityId, tags: List<String>) {
        notes.setTags(noteId, tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().map { Tag(name = it) })
    }

    private companion object {
        const val PREVIEW = 160
    }
}

internal fun JournalEntryEntity.toModel() = JournalEntry(id, date, noteId, promptId, createdAt, updatedAt)

internal fun MoodEntryEntity.toModel() = MoodEntry(
    id = id,
    date = date,
    time = time,
    mood = mood,
    energy = energy,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    noteId = noteId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
