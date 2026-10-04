package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.data.SearchIndexer
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
) : SearchRepository {
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
                    val snippet = if (it.locked || it.encryptedPayload != null) "" else NoteDocument.decode(it.content).plainText().snippet()
                    SearchResult(SearchEntityType.NOTE, it.id, it.title, snippet, archived)
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
