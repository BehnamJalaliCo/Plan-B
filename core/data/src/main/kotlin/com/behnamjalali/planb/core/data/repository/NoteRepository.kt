package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.DataHistory
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.security.NoteVault
import com.behnamjalali.planb.core.data.security.VaultLockedException
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.NoteDraftDao
import com.behnamjalali.planb.core.database.entity.NoteDraftEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TagDao
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
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

    /**
     * Content-only update used by autosave; does not touch other fields. [capturedAt] is when
     * the editor read this content; a draft saved after that is newer and is kept. When it is
     * omitted, the time of the call is used.
     */
    suspend fun updateContent(id: EntityId, title: String, document: NoteDocument, capturedAt: java.time.Instant? = null)
    suspend fun moveNote(id: EntityId, notebookId: EntityId, sectionId: EntityId?)
    suspend fun duplicateNote(id: EntityId, copySuffix: String): EntityId
    suspend fun setPinned(id: EntityId, pinned: Boolean)
    suspend fun setFavorite(id: EntityId, favorite: Boolean)
    suspend fun setArchived(id: EntityId, archived: Boolean)
    suspend fun setTags(id: EntityId, tags: List<Tag>)

    /**
     * Deletes a note. For Plan-B Pro users it goes to the trash (restorable for 30 days);
     * otherwise it is deleted permanently, as always.
     */
    suspend fun deleteNote(id: EntityId)

    /** Deletes a note permanently (e.g. an empty note the editor discards). */
    suspend fun deleteNotePermanently(id: EntityId)

    /**
     * Locked notes (Plan-B Pro #36). [lockNote] encrypts the stored body with the unlocked
     * [NoteVault] and drops its plain-text copies (versions, drafts, the search body); the title
     * stays plain text. All of them throw [VaultLockedException] while the vault is locked.
     */
    suspend fun lockNote(id: EntityId)

    /** Decrypts the body back into the note and removes the lock. */
    suspend fun removeLock(id: EntityId)

    /** The decrypted body of a locked note (kept in memory only). */
    suspend fun lockedContent(id: EntityId): NoteDocument

    /** The encrypted body, used to check a passphrase for notes restored from another device. */
    suspend fun encryptedPayload(id: EntityId): ByteArray?

    /** Draft recovery: latest unsaved editor state, if any. */
    suspend fun getDraft(noteId: EntityId): NoteDraft?
    suspend fun saveDraft(noteId: EntityId, title: String, document: NoteDocument)
    suspend fun clearDraft(noteId: EntityId)
}

data class NoteDraft(val noteId: EntityId, val title: String, val document: NoteDocument, val updatedAt: java.time.Instant)

@Singleton
class OfflineNoteRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: NoteDao,
    private val draftDao: NoteDraftDao,
    private val tagDao: TagDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    /** The trash and activity history (Plan-B Pro); null keeps the free behaviour. */
    private val history: DataHistory? = null,
    /** Keys of locked notes; without it, locked bodies cannot be changed. */
    private val vault: NoteVault? = null,
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
        val log = history?.active() == true
        return db.withTransaction {
            val existing = if (notebook.id != NEW_ID) dao.getNotebook(notebook.id) else null
            val entity = notebook.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (dao.maxNotebookOrder() + 1),
            ).toEntity()
            val id = if (existing == null) dao.insertNotebook(entity.copy(id = 0)) else entity.id.also { dao.updateNotebook(entity) }
            searchDao.upsert(SearchIndexer.notebook(entity.copy(id = id)))
            if (log) history?.record(ActivityEntityType.NOTEBOOK, id, if (existing == null) ActivityAction.CREATED else ActivityAction.UPDATED, notebook.title)
            id
        }
    }

    override suspend fun setNotebookArchived(id: EntityId, archived: Boolean) {
        val nb = dao.getNotebook(id) ?: return
        dao.updateNotebook(nb.copy(archived = archived, updatedAt = time.now()))
    }

    override suspend fun deleteNotebook(id: EntityId) {
        val log = history?.active() == true
        db.withTransaction {
            val title = dao.getNotebook(id)?.title
            dao.notesInNotebook(id).forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTE, it.id)) }
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTEBOOK, id))
            dao.deleteNotebook(id)
            db.attachmentDao().deleteOrphans()
            if (log && title != null) history?.record(ActivityEntityType.NOTEBOOK, id, ActivityAction.DELETED, title)
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
        val log = history?.active() == true
        return db.withTransaction {
            val existing = if (note.id != NEW_ID) dao.getNote(note.id) else null
            val plain = note.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (dao.maxNoteOrder(note.notebookId) + 1),
            ).toEntity(encryptedPayload = existing?.encryptedPayload)
            // Only lockNote and removeLock change the lock; a locked body is stored encrypted.
            val entity = if (existing != null && existing.isEncrypted) {
                val payload = if (note.document.isBlank() && note.document.blocks.isEmpty()) existing.encryptedPayload else seal(note.document)
                plain.copy(locked = true, encryptedPayload = payload, content = NoteDocument.EMPTY.encode())
            } else {
                plain.copy(locked = existing?.locked ?: false)
            }
            val id = if (existing == null) dao.insertNote(entity.copy(id = 0)) else entity.id.also { dao.updateNote(entity) }
            writeTags(id, note.tags)
            searchDao.upsert(SearchIndexer.note(entity.copy(id = id)))
            if (log) history?.record(ActivityEntityType.NOTE, id, if (existing == null) ActivityAction.CREATED else ActivityAction.UPDATED, note.title)
            id
        }
    }

    private val NoteEntity.isEncrypted: Boolean get() = encryptedPayload != null

    /** Encrypts a body with the vault (throws [VaultLockedException] while it is locked). */
    private fun seal(document: NoteDocument): ByteArray =
        (vault ?: throw VaultLockedException()).encrypt(document.encode().toByteArray(Charsets.UTF_8))

    override suspend fun updateContent(id: EntityId, title: String, document: NoteDocument, capturedAt: java.time.Instant?) {
        // Taken before waiting for the transaction: a draft written while this commit was
        // queued holds newer content than [document] and must survive.
        val captured = capturedAt ?: time.now()
        val log = history?.active() == true
        db.withTransaction {
            val existing = dao.getNote(id) ?: throw IllegalStateException("Note $id no longer exists")
            val updated = if (existing.isEncrypted) {
                existing.copy(title = title, encryptedPayload = seal(document), updatedAt = maxOf(captured, existing.updatedAt))
            } else {
                existing.copy(title = title, content = document.encode(), updatedAt = maxOf(captured, existing.updatedAt))
            }
            dao.updateNote(updated)
            searchDao.upsert(SearchIndexer.note(updated))
            if (log) history?.record(ActivityEntityType.NOTE, id, ActivityAction.UPDATED, title)
            // The committed note now contains drafts up to the capture time; newer ones stay.
            draftDao.deleteIfNotNewer(id, captured.toEpochMilli())
        }
    }

    override suspend fun getDraft(noteId: EntityId): NoteDraft? {
        // A locked note never keeps a plain-text draft.
        if (dao.getNote(noteId)?.locked == true) return null
        return draftDao.get(noteId)?.let { NoteDraft(it.noteId, it.title, NoteDocument.decode(it.content), it.updatedAt) }
    }

    override suspend fun saveDraft(noteId: EntityId, title: String, document: NoteDocument) {
        if (dao.getNote(noteId)?.locked == true) return
        draftDao.upsert(NoteDraftEntity(noteId, title, document.encode(), time.now()))
    }

    override suspend fun clearDraft(noteId: EntityId) = draftDao.delete(noteId)

    override suspend fun moveNote(id: EntityId, notebookId: EntityId, sectionId: EntityId?) {
        dao.move(id, notebookId, sectionId, time.now().toEpochMilli())
    }

    override suspend fun duplicateNote(id: EntityId, copySuffix: String): EntityId {
        val now = time.now()
        val log = history?.active() == true
        return db.withTransaction {
            val source = dao.getNote(id) ?: throw IllegalStateException("Note $id not found")
            val copy = source.copy(
                id = 0,
                title = listOf(source.title, copySuffix).filter { it.isNotBlank() }.joinToString(" "),
                pinned = false,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
                sortOrder = dao.maxNoteOrder(source.notebookId) + 1,
            )
            val newId = dao.insertNote(copy)
            dao.insertTagRefs(dao.tagIds(id).map { NoteTagCrossRef(newId, it) })
            searchDao.upsert(SearchIndexer.note(copy.copy(id = newId)))
            if (log) history?.record(ActivityEntityType.NOTE, newId, ActivityAction.CREATED, copy.title)
            newId
        }
    }

    override suspend fun setPinned(id: EntityId, pinned: Boolean) = dao.setPinned(id, pinned)

    override suspend fun setFavorite(id: EntityId, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun setArchived(id: EntityId, archived: Boolean) {
        val log = history?.active() == true
        db.withTransaction {
            dao.setArchived(id, archived)
            if (log) dao.getNote(id)?.let { history?.record(ActivityEntityType.NOTE, id, if (archived) ActivityAction.ARCHIVED else ActivityAction.RESTORED, it.title) }
        }
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
        if (history?.active() != true) {
            deleteNotePermanently(id)
            return
        }
        db.withTransaction {
            val note = dao.getNote(id) ?: return@withTransaction
            if (note.deletedAt != null) return@withTransaction
            dao.setDeletedAt(id, time.now().toEpochMilli())
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTE, id))
            draftDao.delete(id)
            history?.record(ActivityEntityType.NOTE, id, ActivityAction.DELETED, note.title)
        }
    }

    override suspend fun deleteNotePermanently(id: EntityId) {
        db.withTransaction {
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.NOTE, id))
            dao.deleteNote(id)
            db.attachmentDao().deleteOrphans()
        }
    }

    /** Brings a note back from the trash; it is searchable again. */
    suspend fun restoreFromTrash(id: EntityId) {
        val log = history?.active() == true
        db.withTransaction {
            val note = dao.getNote(id) ?: return@withTransaction
            if (note.deletedAt == null) return@withTransaction
            dao.setDeletedAt(id, null)
            searchDao.upsert(SearchIndexer.note(note.copy(deletedAt = null)))
            if (log) history?.record(ActivityEntityType.NOTE, id, ActivityAction.RESTORED, note.title)
        }
    }

    /** Ids of notes that went to the trash before [before], for the 30-day purge. */
    suspend fun trashedBefore(before: java.time.Instant): List<EntityId> = dao.trashedBefore(before.toEpochMilli())

    override suspend fun lockNote(id: EntityId) {
        val now = time.now().toEpochMilli()
        db.withTransaction {
            val note = dao.getNote(id) ?: throw IllegalStateException("Note $id not found")
            if (note.isEncrypted) return@withTransaction
            val payload = seal(NoteDocument.decode(note.content))
            val empty = NoteDocument.EMPTY.encode()
            dao.setLocked(id, true, payload, empty, now)
            // Plain-text copies of the body go: history versions, the draft and the search body.
            db.noteVersionDao().deleteForNote(id)
            draftDao.delete(id)
            searchDao.upsert(SearchIndexer.note(note.copy(locked = true, encryptedPayload = payload, content = empty)))
        }
    }

    override suspend fun removeLock(id: EntityId) {
        val now = time.now().toEpochMilli()
        db.withTransaction {
            val note = dao.getNote(id) ?: throw IllegalStateException("Note $id not found")
            val content = note.encryptedPayload?.let { open(it).encode() } ?: note.content
            dao.setLocked(id, false, null, content, now)
            searchDao.upsert(SearchIndexer.note(note.copy(locked = false, encryptedPayload = null, content = content)))
        }
    }

    override suspend fun lockedContent(id: EntityId): NoteDocument {
        val note = dao.getNote(id) ?: throw IllegalStateException("Note $id not found")
        return note.encryptedPayload?.let(::open) ?: NoteDocument.decode(note.content)
    }

    override suspend fun encryptedPayload(id: EntityId): ByteArray? = dao.getNote(id)?.encryptedPayload

    private fun open(payload: ByteArray): NoteDocument =
        NoteDocument.decode((vault ?: throw VaultLockedException()).decrypt(payload).toString(Charsets.UTF_8))
}
