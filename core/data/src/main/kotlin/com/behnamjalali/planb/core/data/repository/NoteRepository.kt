package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.NoteDraftDao
import com.behnamjalali.planb.core.database.entity.NoteDraftEntity
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TagDao
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.NotebookSection
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Tag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface NoteRepository {
    fun observeNotebooks(archived: Boolean = false): Flow<List<Notebook>>
    fun observeNotebook(id: EntityId): Flow<Notebook?>
    fun observeSections(notebookId: EntityId): Flow<List<NotebookSection>>
    fun observeNotes(notebookId: EntityId, sectionId: EntityId? = null): Flow<List<Note>>
    fun observePinnedOrFavorite(limit: Int = 6): Flow<List<Note>>
    fun observeRecent(limit: Int = 6): Flow<List<Note>>
    fun observeArchivedNotes(): Flow<List<Note>>
    fun observeNote(id: EntityId): Flow<Note?>
    suspend fun getNote(id: EntityId): Note?
    suspend fun getNotebook(id: EntityId): Notebook?

    suspend fun saveNotebook(notebook: Notebook): EntityId
    suspend fun setNotebookArchived(id: EntityId, archived: Boolean)
    suspend fun deleteNotebook(id: EntityId)
    suspend fun reorderNotebooks(orderedIds: List<EntityId>)

    /** Returns the first active notebook, creating one with [defaultTitle] if none exists. */
    suspend fun ensureDefaultNotebook(defaultTitle: String): EntityId

    suspend fun saveSection(section: NotebookSection): EntityId
    suspend fun moveSection(sectionId: EntityId, targetNotebookId: EntityId)
    suspend fun deleteSection(id: EntityId)

    suspend fun saveNote(note: Note): EntityId

    /** Content-only update used by autosave; does not touch other fields. */
    suspend fun updateContent(id: EntityId, title: String, document: NoteDocument)
    suspend fun moveNote(id: EntityId, notebookId: EntityId, sectionId: EntityId?)
    suspend fun duplicateNote(id: EntityId, copySuffix: String): EntityId
    suspend fun setPinned(id: EntityId, pinned: Boolean)
    suspend fun setFavorite(id: EntityId, favorite: Boolean)
    suspend fun setArchived(id: EntityId, archived: Boolean)
    suspend fun setTags(id: EntityId, tags: List<Tag>)
    suspend fun deleteNote(id: EntityId)

    /** Draft recovery: latest unsaved editor state, if any. */
    suspend fun getDraft(noteId: EntityId): NoteDraft?
    suspend fun saveDraft(noteId: EntityId, title: String, document: NoteDocument)
    suspend fun clearDraft(noteId: EntityId)
}

data class NoteDraft(val noteId: EntityId, val title: String, val document: NoteDocument, val updatedAt: java.time.Instant)

@Singleton
internal class OfflineNoteRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: NoteDao,
    private val draftDao: NoteDraftDao,
    private val tagDao: TagDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
) : NoteRepository {
    override fun observeNotebooks(archived: Boolean) = dao.observeNotebooks(archived).map { l -> l.map { it.toModel() } }
    override fun observeNotebook(id: EntityId) = dao.observeNotebook(id).map { it?.toModel() }
    override fun observeSections(notebookId: EntityId) = dao.observeSections(notebookId).map { l -> l.map { it.toModel() } }
    override fun observeNotes(notebookId: EntityId, sectionId: EntityId?) =
        dao.observeNotes(notebookId, sectionId).map { l -> l.map { it.toModel() } }
    override fun observePinnedOrFavorite(limit: Int) = dao.observePinnedOrFavorite(limit).map { l -> l.map { it.toModel() } }
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).map { l -> l.map { it.toModel() } }
    override fun observeArchivedNotes() = dao.observeArchived().map { l -> l.map { it.toModel() } }
    override fun observeNote(id: EntityId) = dao.observeNote(id).map { it?.toModel() }
    override suspend fun getNote(id: EntityId) = dao.getNote(id)?.toModel()
    override suspend fun getNotebook(id: EntityId) = dao.getNotebook(id)?.toModel()

    override suspend fun saveNotebook(notebook: Notebook): EntityId {
        require(notebook.title.isNotBlank()) { "Notebook title must not be blank" }
        val now = time.now()
        return db.withTransaction {
            val existing = if (notebook.id != NEW_ID) dao.getNotebook(notebook.id) else null
            val entity = notebook.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (dao.maxNotebookOrder() + 1),
            ).toEntity()
            val id = if (existing == null) dao.insertNotebook(entity.copy(id = 0)) else entity.id.also { dao.updateNotebook(entity) }
            searchDao.upsert(SearchIndexer.notebook(entity.copy(id = id)))
            id
        }
    }

    override suspend fun setNotebookArchived(id: EntityId, archived: Boolean) {
        val nb = dao.getNotebook(id) ?: return
        dao.updateNotebook(nb.copy(archived = archived, updatedAt = time.now()))
    }

    override suspend fun deleteNotebook(id: EntityId) {
        db.withTransaction {
            dao.notesInNotebook(id).forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTE, it.id)) }
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTEBOOK, id))
            dao.deleteNotebook(id)
        }
    }

    override suspend fun reorderNotebooks(orderedIds: List<EntityId>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setNotebookOrder(id, i.toLong()) } }
    }

    override suspend fun ensureDefaultNotebook(defaultTitle: String): EntityId =
        dao.firstNotebook()?.id ?: saveNotebook(Notebook(title = defaultTitle))

    override suspend fun saveSection(section: NotebookSection): EntityId {
        require(section.title.isNotBlank()) { "Section title must not be blank" }
        return if (section.id == NEW_ID) {
            dao.insertSection(section.copy(sortOrder = dao.maxSectionOrder(section.notebookId) + 1).toEntity().copy(id = 0))
        } else {
            dao.updateSection(section.toEntity())
            section.id
        }
    }

    override suspend fun moveSection(sectionId: EntityId, targetNotebookId: EntityId) {
        db.withTransaction {
            val section = dao.getSection(sectionId) ?: return@withTransaction
            dao.updateSection(section.copy(notebookId = targetNotebookId, sortOrder = dao.maxSectionOrder(targetNotebookId) + 1))
            dao.moveSectionNotes(sectionId, targetNotebookId, time.now().toEpochMilli())
        }
    }

    override suspend fun deleteSection(id: EntityId) = dao.deleteSection(id)

    override suspend fun saveNote(note: Note): EntityId {
        val now = time.now()
        return db.withTransaction {
            val existing = if (note.id != NEW_ID) dao.getNote(note.id) else null
            val entity = note.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (dao.maxNoteOrder(note.notebookId) + 1),
            ).toEntity()
            val id = if (existing == null) dao.insertNote(entity.copy(id = 0)) else entity.id.also { dao.updateNote(entity) }
            writeTags(id, note.tags)
            searchDao.upsert(SearchIndexer.note(entity.copy(id = id)))
            id
        }
    }

    override suspend fun updateContent(id: EntityId, title: String, document: NoteDocument) {
        db.withTransaction {
            val existing = dao.getNote(id) ?: throw IllegalStateException("Note $id no longer exists")
            val now = time.now()
            val updated = existing.copy(title = title, content = document.encode(), updatedAt = now)
            dao.updateNote(updated)
            searchDao.upsert(SearchIndexer.note(updated))
            // The committed note now contains the draft; drop it unless a newer draft arrived.
            draftDao.deleteIfNotNewer(id, now.toEpochMilli())
        }
    }

    override suspend fun getDraft(noteId: EntityId): NoteDraft? =
        draftDao.get(noteId)?.let { NoteDraft(it.noteId, it.title, NoteDocument.decode(it.content), it.updatedAt) }

    override suspend fun saveDraft(noteId: EntityId, title: String, document: NoteDocument) {
        draftDao.upsert(NoteDraftEntity(noteId, title, document.encode(), time.now()))
    }

    override suspend fun clearDraft(noteId: EntityId) = draftDao.delete(noteId)

    override suspend fun moveNote(id: EntityId, notebookId: EntityId, sectionId: EntityId?) {
        val note = dao.getNote(id) ?: return
        dao.updateNote(note.copy(notebookId = notebookId, sectionId = sectionId, updatedAt = time.now()))
    }

    override suspend fun duplicateNote(id: EntityId, copySuffix: String): EntityId {
        val now = time.now()
        return db.withTransaction {
            val source = dao.getNote(id) ?: throw IllegalStateException("Note $id not found")
            val copy = source.copy(
                id = 0,
                title = listOf(source.title, copySuffix).filter { it.isNotBlank() }.joinToString(" "),
                pinned = false,
                createdAt = now,
                updatedAt = now,
                sortOrder = dao.maxNoteOrder(source.notebookId) + 1,
            )
            val newId = dao.insertNote(copy)
            dao.insertTagRefs(dao.tagIds(id).map { NoteTagCrossRef(newId, it) })
            searchDao.upsert(SearchIndexer.note(copy.copy(id = newId)))
            newId
        }
    }

    override suspend fun setPinned(id: EntityId, pinned: Boolean) {
        dao.getNote(id)?.let { dao.updateNote(it.copy(pinned = pinned)) }
    }

    override suspend fun setFavorite(id: EntityId, favorite: Boolean) {
        dao.getNote(id)?.let { dao.updateNote(it.copy(favorite = favorite)) }
    }

    override suspend fun setArchived(id: EntityId, archived: Boolean) {
        dao.getNote(id)?.let { dao.updateNote(it.copy(archived = archived, pinned = if (archived) false else it.pinned)) }
    }

    override suspend fun setTags(id: EntityId, tags: List<Tag>) {
        db.withTransaction { writeTags(id, tags) }
    }

    private suspend fun writeTags(noteId: EntityId, tags: List<Tag>) {
        dao.clearTags(noteId)
        val ids = tags.map { tag ->
            if (tag.id != NEW_ID) tag.id else tagDao.findByName(tag.name.trim())?.id
                ?: tagDao.insert(TagEntity(name = tag.name.trim(), color = tag.color.key))
        }
        dao.insertTagRefs(ids.distinct().map { NoteTagCrossRef(noteId, it) })
    }

    override suspend fun deleteNote(id: EntityId) {
        db.withTransaction {
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTE, id))
            dao.deleteNote(id)
        }
    }
}
