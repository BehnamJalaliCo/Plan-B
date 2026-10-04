package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.NotebookSectionEntity
import com.behnamjalali.planb.core.database.model.NoteWithTags
import com.behnamjalali.planb.core.database.model.NotebookWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT n.*, (SELECT COUNT(*) FROM notes x WHERE x.notebook_id = n.id AND x.archived = 0) AS note_count " +
            "FROM notebooks n WHERE n.archived = :archived ORDER BY n.sort_order, n.id",
    )
    fun observeNotebooks(archived: Boolean): Flow<List<NotebookWithCount>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun observeNotebook(id: Long): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebook(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE archived = 0 ORDER BY sort_order, id LIMIT 1")
    suspend fun firstNotebook(): NotebookEntity?

    @Insert
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteNotebook(id: Long)

    @Query("UPDATE notebooks SET sort_order = :order WHERE id = :id")
    suspend fun setNotebookOrder(id: Long, order: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM notebooks")
    suspend fun maxNotebookOrder(): Long

    @Query("SELECT * FROM notebook_sections WHERE notebook_id = :notebookId ORDER BY sort_order, id")
    fun observeSections(notebookId: Long): Flow<List<NotebookSectionEntity>>

    @Query("SELECT * FROM notebook_sections WHERE id = :id")
    suspend fun getSection(id: Long): NotebookSectionEntity?

    @Insert
    suspend fun insertSection(section: NotebookSectionEntity): Long

    @Update
    suspend fun updateSection(section: NotebookSectionEntity)

    @Query("DELETE FROM notebook_sections WHERE id = :id")
    suspend fun deleteSection(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM notebook_sections WHERE notebook_id = :notebookId")
    suspend fun maxSectionOrder(notebookId: Long): Long

    @Query("UPDATE notes SET notebook_id = :notebookId, updated_at = :now WHERE section_id = :sectionId")
    suspend fun moveSectionNotes(sectionId: Long, notebookId: Long, now: Long)

    @Transaction
    @Query(
        "SELECT * FROM notes WHERE notebook_id = :notebookId AND archived = 0 " +
            "AND (:sectionId IS NULL OR section_id = :sectionId) " +
            "ORDER BY pinned DESC, sort_order, updated_at DESC",
    )
    fun observeNotes(notebookId: Long, sectionId: Long?): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE archived = 0 AND (pinned = 1 OR favorite = 1) ORDER BY pinned DESC, updated_at DESC LIMIT :limit")
    fun observePinnedOrFavorite(limit: Int): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteWithTags?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE notebook_id = :notebookId")
    suspend fun notesInNotebook(notebookId: Long): List<NoteEntity>

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM notes WHERE notebook_id = :notebookId")
    suspend fun maxNoteOrder(notebookId: Long): Long

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearTags(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<NoteTagCrossRef>)

    @Query("SELECT tag_id FROM note_tags WHERE note_id = :noteId")
    suspend fun tagIds(noteId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM notes WHERE created_at >= :from AND created_at < :to")
    suspend fun countCreatedBetween(from: Long, to: Long): Int
}
