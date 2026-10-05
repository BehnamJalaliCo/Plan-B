package com.behnamjalali.planb.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val REF = "SELECT id, title, notebook_id, updated_at, locked, deleted_at IS NOT NULL AS trashed FROM notes"

/** A note as seen by links, backlinks, the link picker and the graph: no body. */
data class NoteRefRow(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "notebook_id") val notebookId: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val locked: Boolean,
    val trashed: Boolean,
)

data class NoteTagRow(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** A journal page with what the journal list shows (the body only of unlocked, live notes). */
data class JournalPageRow(
    val id: Long,
    val date: Long,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "prompt_id") val promptId: String?,
    val title: String,
    val content: String,
    val locked: Boolean,
)

/**
 * Queries for Plan-B Pro notes knowledge (#16 links and history, #21 graph, #25 journal) over
 * existing tables; no tables of its own.
 */
@Dao
interface NoteKnowledgeDao {
    /** Live notes for the link picker, most recently edited first. */
    @Query("$REF WHERE deleted_at IS NULL ORDER BY updated_at DESC, id DESC LIMIT :limit")
    suspend fun liveNotes(limit: Int): List<NoteRefRow>

    /** Titles and state of the notes [ids] (linked notes; missing ids were deleted for good). */
    @Query("$REF WHERE id IN (:ids)")
    fun observeRefs(ids: List<Long>): Flow<List<NoteRefRow>>

    @Query("$REF WHERE id IN (:ids)")
    suspend fun refs(ids: List<Long>): List<NoteRefRow>

    /** Live notes that link to [noteId], most recently edited first. */
    @Query(
        "SELECT n.id, n.title, n.notebook_id, n.updated_at, n.locked, 0 AS trashed FROM note_links l " +
            "JOIN notes n ON n.id = l.from_note_id WHERE l.to_note_id = :noteId AND n.deleted_at IS NULL AND n.id != :noteId " +
            "ORDER BY n.updated_at DESC, n.id DESC",
    )
    fun observeBacklinks(noteId: Long): Flow<List<NoteRefRow>>

    /** Every live note (graph nodes). */
    @Query("$REF WHERE deleted_at IS NULL ORDER BY id")
    fun observeGraphNotes(): Flow<List<NoteRefRow>>

    @Query("SELECT nt.note_id, nt.tag_id FROM note_tags nt JOIN notes n ON n.id = nt.note_id WHERE n.deleted_at IS NULL")
    fun observeNoteTags(): Flow<List<NoteTagRow>>

    /** Note history retention: drops versions of [noteId] older than [before] (epoch ms). */
    @Query("DELETE FROM note_versions WHERE note_id = :noteId AND created_at < :before")
    suspend fun deleteVersionsBefore(noteId: Long, before: Long): Int

    @Query("SELECT COUNT(*) FROM note_versions WHERE note_id = :noteId")
    suspend fun versionCount(noteId: Long): Int

    @Query("SELECT * FROM note_versions WHERE note_id = :noteId ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun newestVersion(noteId: Long): com.behnamjalali.planb.core.database.entity.NoteVersionEntity?

    /** Journal pages in [from, to] (epoch days) whose note still exists and is not in the trash. */
    @Query(
        "SELECT j.id, j.date, j.note_id, j.prompt_id, n.title, CASE WHEN n.locked THEN '' ELSE n.content END AS content, n.locked " +
            "FROM journal_entries j JOIN notes n ON n.id = j.note_id " +
            "WHERE j.date >= :from AND j.date <= :to AND n.deleted_at IS NULL ORDER BY j.date DESC",
    )
    fun observeJournalPages(from: Long, to: Long): Flow<List<JournalPageRow>>

    /** Days (epoch days) with a live journal page, for streaks and the calendar. */
    @Query(
        "SELECT j.date FROM journal_entries j JOIN notes n ON n.id = j.note_id " +
            "WHERE j.date >= :from AND j.date <= :to AND n.deleted_at IS NULL",
    )
    fun observeJournalDates(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT * FROM mood_entries WHERE date = :date AND note_id = :noteId ORDER BY id LIMIT 1")
    suspend fun moodForPage(date: Long, noteId: Long): com.behnamjalali.planb.core.database.entity.MoodEntryEntity?

    /** Tag names of a note (journal tags are the page note's tags). */
    @Query("SELECT t.name FROM tags t JOIN note_tags nt ON nt.tag_id = t.id WHERE nt.note_id = :noteId ORDER BY t.name COLLATE NOCASE")
    fun observeTagNames(noteId: Long): Flow<List<String>>
}
