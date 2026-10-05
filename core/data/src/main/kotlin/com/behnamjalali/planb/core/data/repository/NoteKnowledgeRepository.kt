package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.NoteRefRow
import com.behnamjalali.planb.core.database.entity.NoteVersionEntity
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.NoteGraph
import com.behnamjalali.planb.core.model.NoteRef
import com.behnamjalali.planb.core.model.NoteVersion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Links between notes (Plan-B Pro #16) and the note graph (#21). The links themselves are
 * written by [NoteRepository] whenever a note is saved; this reads them.
 */
interface NoteLinkRepository {
    /** Live notes whose title matches [query] (Persian/English normalized), best matches first. */
    suspend fun searchNotes(query: String, excludeId: EntityId? = null, limit: Int = 20): List<NoteRef>

    /** Current title and state of each note in [ids]; ids missing from the map were deleted for good. */
    fun observeRefs(ids: Set<EntityId>): Flow<Map<EntityId, NoteRef>>

    suspend fun refs(ids: Set<EntityId>): Map<EntityId, NoteRef>

    /** Live notes that link to [noteId]. */
    fun observeBacklinks(noteId: EntityId): Flow<List<NoteRef>>

    /** All live notes, the links between them and their tags. */
    fun observeGraph(): Flow<NoteGraph>
}

@Singleton
class OfflineNoteLinkRepository @Inject constructor(private val db: PlanBDatabase) : NoteLinkRepository {
    private val dao get() = db.noteKnowledgeDao()

    override suspend fun searchNotes(query: String, excludeId: EntityId?, limit: Int): List<NoteRef> {
        val notes = dao.liveNotes(MAX_PICKER_NOTES).filter { it.id != excludeId }.map { it.toModel() }
        val terms = SearchNormalizer.tokens(query)
        if (terms.isEmpty()) return notes.take(limit)
        return notes.mapNotNull { note ->
            val words = SearchNormalizer.tokens(note.title)
            val joined = words.joinToString(" ")
            // Every typed word must start a word of the title; a title starting with the search ranks first.
            if (terms.all { term -> words.any { it.startsWith(term) } }) {
                note to if (joined.startsWith(terms.joinToString(" "))) 0 else 1
            } else {
                null
            }
        }.sortedBy { it.second }.take(limit).map { it.first }
    }

    override fun observeRefs(ids: Set<EntityId>): Flow<Map<EntityId, NoteRef>> =
        if (ids.isEmpty()) flowOf(emptyMap()) else dao.observeRefs(ids.take(MAX_REFS)).map { rows -> rows.associate { it.id to it.toModel() } }

    override suspend fun refs(ids: Set<EntityId>): Map<EntityId, NoteRef> =
        if (ids.isEmpty()) emptyMap() else ids.chunked(MAX_REFS).flatMap { dao.refs(it) }.associate { it.id to it.toModel() }

    override fun observeBacklinks(noteId: EntityId): Flow<List<NoteRef>> = dao.observeBacklinks(noteId).map { rows -> rows.map { it.toModel() } }

    override fun observeGraph(): Flow<NoteGraph> = combine(
        dao.observeGraphNotes(),
        db.noteLinkDao().observeGraph(),
        dao.observeNoteTags(),
    ) { notes, links, tags ->
        NoteGraph(
            notes = notes.map { it.toModel() },
            edges = links.filter { it.fromNoteId != it.toNoteId }.map { it.fromNoteId to it.toNoteId },
            tags = tags.groupBy({ it.noteId }, { it.tagId }).mapValues { it.value.toSet() },
        )
    }

    private companion object {
        const val MAX_PICKER_NOTES = 5_000
        const val MAX_REFS = 500
    }
}

internal fun NoteRefRow.toModel() = NoteRef(id, title, notebookId, Instant.ofEpochMilli(updatedAt), locked, trashed)

/**
 * Note history (Plan-B Pro #16): snapshots in `note_versions`, only for Pro users and never of
 * locked notes. Retention: the newest [MAX_VERSIONS] per note, none older than [MAX_AGE].
 */
interface NoteHistoryRepository {
    fun observeVersions(noteId: EntityId): Flow<List<NoteVersion>>
    suspend fun version(id: EntityId): NoteVersion?

    /**
     * Saves the note's stored state as a version, unless it equals the newest version or the
     * note is locked. Without [force], only when the newest version is at least [INTERVAL] old
     * (a long editing session gets a version every 10 minutes). Returns whether one was saved.
     */
    suspend fun snapshot(noteId: EntityId, force: Boolean = false): Boolean

    /** Puts a version back; the current state is saved as a version first. Returns the note id. */
    suspend fun restore(versionId: EntityId): EntityId

    companion object {
        const val MAX_VERSIONS = 50
        val MAX_AGE: Duration = Duration.ofDays(90)
        val INTERVAL: Duration = Duration.ofMinutes(10)
    }
}

@Singleton
class OfflineNoteHistoryRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val notes: NoteRepository,
    private val time: TimeProvider,
    private val pro: ProStatusSource,
) : NoteHistoryRepository {
    private val versions get() = db.noteVersionDao()
    private val dao get() = db.noteKnowledgeDao()

    override fun observeVersions(noteId: EntityId): Flow<List<NoteVersion>> =
        versions.observeForNote(noteId, NoteHistoryRepository.MAX_VERSIONS).map { rows -> rows.map { it.toModel() } }

    override suspend fun version(id: EntityId): NoteVersion? = versions.get(id)?.toModel()

    override suspend fun snapshot(noteId: EntityId, force: Boolean): Boolean {
        if (!runCatching { pro.isPro() }.getOrDefault(false)) return false
        val now = time.now()
        return db.withTransaction {
            val note = db.noteDao().getNote(noteId) ?: return@withTransaction false
            // Locked notes keep no plain-text history at all.
            if (note.locked || note.encryptedPayload != null || note.deletedAt != null) return@withTransaction false
            if (note.title.isBlank() && NoteDocument.decode(note.content).isBlank()) return@withTransaction false
            val newest = dao.newestVersion(noteId)
            if (newest != null && newest.title == note.title && newest.content == note.content) return@withTransaction false
            if (!force && newest != null && Duration.between(newest.createdAt, now) < NoteHistoryRepository.INTERVAL) return@withTransaction false
            versions.insert(
                NoteVersionEntity(
                    noteId = noteId,
                    createdAt = now,
                    title = note.title,
                    content = note.content,
                    size = note.content.toByteArray(Charsets.UTF_8).size.toLong(),
                ),
            )
            prune(noteId, now)
            true
        }
    }

    private suspend fun prune(noteId: EntityId, now: Instant) {
        dao.deleteVersionsBefore(noteId, now.minus(NoteHistoryRepository.MAX_AGE).toEpochMilli())
        versions.prune(noteId, NoteHistoryRepository.MAX_VERSIONS)
    }

    override suspend fun restore(versionId: EntityId): EntityId {
        val version = versions.get(versionId) ?: throw IllegalStateException("Version $versionId not found")
        val note = notes.getNote(version.noteId) ?: throw IllegalStateException("Note ${version.noteId} not found")
        check(!note.locked) { "A locked note has no history" }
        snapshot(version.noteId, force = true)
        notes.updateContent(version.noteId, version.title, NoteDocument.decode(version.content))
        return version.noteId
    }
}

internal fun NoteVersionEntity.toModel() = NoteVersion(id, noteId, createdAt, title, NoteDocument.decode(content), size)

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NoteKnowledgeModule {
    @Binds abstract fun links(impl: OfflineNoteLinkRepository): NoteLinkRepository
    @Binds abstract fun history(impl: OfflineNoteHistoryRepository): NoteHistoryRepository
    @Binds abstract fun journal(impl: OfflineJournalRepository): JournalRepository
}
