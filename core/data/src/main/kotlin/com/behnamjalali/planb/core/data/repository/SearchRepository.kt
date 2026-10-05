package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.database.dao.AttachmentDao
import com.behnamjalali.planb.core.model.AttachmentOwner
import com.behnamjalali.planb.core.model.SearchMatchSource
import com.behnamjalali.planb.core.database.dao.EventDao
import com.behnamjalali.planb.core.database.dao.GoalDao
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.database.dao.NoteDao
import com.behnamjalali.planb.core.database.dao.ProjectDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

interface SearchRepository {
    /** Persian-aware prefix search across all entity types. */
    suspend fun search(query: String, limit: Int = 100): List<SearchResult>
}

@Singleton
class FtsSearchRepository @Inject constructor(
    private val searchDao: SearchDao,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val noteDao: NoteDao,
    private val habitDao: HabitDao,
    private val goalDao: GoalDao,
    private val eventDao: EventDao,
    /** Text recognized in notes' images and recordings (Plan-B Pro #17, #19). */
    private val attachmentDao: AttachmentDao? = null,
) : SearchRepository {
    /**
     * When a note's own text does not contain every query word, the match came from an image's
     * recognized text or a recording's transcript: that text becomes the snippet.
     */
    private suspend fun attachmentMatch(noteId: Long, query: String, noteText: String): Pair<String, SearchMatchSource>? {
        val dao = attachmentDao ?: return null
        val words = SearchNormalizer.tokens(query).take(8)
        fun matches(text: String): Boolean {
            val tokens = SearchNormalizer.indexTokens(text)
            return words.all { w -> tokens.any { it.startsWith(w) } }
        }
        if (words.isEmpty() || matches(noteText)) return null
        dao.forOwner(AttachmentOwner.NOTE.name, noteId).forEach { a ->
            a.ocrText?.takeIf(::matches)?.let { return it.snippet() to SearchMatchSource.IMAGE }
            a.transcript?.takeIf(::matches)?.let { return it.snippet() to SearchMatchSource.RECORDING }
        }
        return null
    }

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        val match = SearchIndexer.matchQuery(query) ?: return emptyList()
        return searchDao.search(match, limit).mapNotNull { hit ->
            when (SearchEntityType.fromCode(hit.entityType)) {
                SearchEntityType.TASK -> taskDao.getEntity(hit.entityId)?.takeIf { it.deletedAt == null }?.let {
                    SearchResult(SearchEntityType.TASK, it.id, it.title, it.description.ifBlank { it.notes }.snippet(), it.archived)
                }
                SearchEntityType.PROJECT -> projectDao.getEntity(hit.entityId)?.let {
                    SearchResult(SearchEntityType.PROJECT, it.id, it.title, it.description.snippet(), it.archived)
                }
                SearchEntityType.NOTE -> noteDao.getNote(hit.entityId)?.takeIf { it.deletedAt == null }?.let {
                    // A note in an archived notebook is archived with it.
                    val archived = it.archived || noteDao.isNotebookArchived(it.notebookId) == true
                    // A locked note never shows its text outside the unlocked editor.
                    val locked = it.locked || it.encryptedPayload != null
                    val text = if (locked) "" else NoteDocument.decode(it.content).plainText()
                    val inAttachment = if (locked) null else attachmentMatch(it.id, query, it.title + " " + text)
                    SearchResult(SearchEntityType.NOTE, it.id, it.title, inAttachment?.first ?: text.snippet(), archived, inAttachment?.second)
                }
                SearchEntityType.NOTEBOOK -> noteDao.getNotebook(hit.entityId)?.let {
                    SearchResult(SearchEntityType.NOTEBOOK, it.id, it.title, archived = it.archived)
                }
                SearchEntityType.HABIT -> habitDao.getHabit(hit.entityId)?.let {
                    SearchResult(SearchEntityType.HABIT, it.id, it.title, archived = it.archived)
                }
                SearchEntityType.GOAL -> goalDao.getGoal(hit.entityId)?.let {
                    SearchResult(SearchEntityType.GOAL, it.id, it.title, it.description.snippet(), it.archived)
                }
                SearchEntityType.EVENT -> eventDao.getEvent(hit.entityId)?.let {
                    SearchResult(SearchEntityType.EVENT, it.id, it.title, it.description.snippet())
                }
                null -> null
            }
        }.sortedWith(compareBy({ it.archived }, { it.type.ordinal }))
    }

    private fun String.snippet(): String = lineSequence().firstOrNull { it.isNotBlank() }?.take(140).orEmpty()
}
