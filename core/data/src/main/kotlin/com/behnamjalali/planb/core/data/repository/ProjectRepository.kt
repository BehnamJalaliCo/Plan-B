package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.DataHistory
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.ProjectDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TagDao
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.SearchEntityType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ProjectRepository {
    fun observeProjects(archived: Boolean = false): Flow<List<ProjectSummary>>
    fun observeProject(id: EntityId): Flow<ProjectSummary?>
    fun observeActiveProjects(): Flow<List<Project>>
    fun observeMilestones(projectId: EntityId): Flow<List<ProjectMilestone>>
    /** The project with its tags (so saving it back keeps them). */
    suspend fun getProject(id: EntityId): Project?
    suspend fun save(project: Project): EntityId

    /** Updates only the project's notes (its description); tags and other fields are untouched. */
    suspend fun updateNotes(id: EntityId, notes: String)
    suspend fun setStatus(id: EntityId, status: ProjectStatus)
    suspend fun setArchived(id: EntityId, archived: Boolean)

    /** Permanently deletes the project; its tasks are kept and moved to the inbox. */
    suspend fun delete(id: EntityId)
    suspend fun reorder(orderedIds: List<EntityId>)
    suspend fun saveMilestone(milestone: ProjectMilestone): EntityId
    suspend fun setMilestoneCompleted(milestone: ProjectMilestone, completed: Boolean)
    suspend fun deleteMilestone(id: EntityId)
}

@Singleton
class OfflineProjectRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: ProjectDao,
    private val tagDao: TagDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    /** Activity history (Plan-B Pro); null logs nothing. */
    private val history: DataHistory? = null,
) : ProjectRepository {
    override fun observeProjects(archived: Boolean) = dao.observeProjects(archived).map { list -> list.map { it.toModel() } }
    override fun observeProject(id: EntityId) = dao.observeProject(id).map { it?.toModel() }
    override fun observeActiveProjects() = dao.observeActiveEntities().map { list -> list.map { it.toModel() } }
    override fun observeMilestones(projectId: EntityId) = dao.observeMilestones(projectId).map { list -> list.map { it.toModel() } }
    override suspend fun getProject(id: EntityId): Project? = dao.observeProject(id).first()?.toModel()?.project

    override suspend fun updateNotes(id: EntityId, notes: String) {
        db.withTransaction {
            val entity = dao.getEntity(id) ?: return@withTransaction
            if (entity.description == notes) return@withTransaction
            val updated = entity.copy(description = notes, updatedAt = time.now())
            dao.update(updated)
            searchDao.upsert(SearchIndexer.project(updated))
        }
    }

    override suspend fun save(project: Project): EntityId {
        require(project.title.isNotBlank()) { "Project title must not be blank" }
        val now = time.now()
        val log = history?.active() == true
        return db.withTransaction {
            val existing = if (project.id != NEW_ID) dao.getEntity(project.id) else null
            val entity = project.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (dao.maxSortOrder() + 1),
                archived = project.status == ProjectStatus.ARCHIVED || project.archived,
            ).toEntity()
            val id = if (existing == null) dao.insert(entity.copy(id = 0)) else entity.id.also { dao.update(entity) }
            dao.clearTags(id)
            val tagIds = project.tags.map { tag ->
                if (tag.id != NEW_ID) tag.id else tagDao.findByName(tag.name.trim())?.id
                    ?: tagDao.insert(TagEntity(name = tag.name.trim(), color = tag.color.key))
            }
            dao.insertTagRefs(tagIds.distinct().map { ProjectTagCrossRef(id, it) })
            searchDao.upsert(SearchIndexer.project(entity.copy(id = id)))
            if (log) history?.record(ActivityEntityType.PROJECT, id, if (existing == null) ActivityAction.CREATED else ActivityAction.UPDATED, project.title)
            id
        }
    }

    override suspend fun setStatus(id: EntityId, status: ProjectStatus) {
        val entity = dao.getEntity(id) ?: return
        dao.update(entity.copy(status = status.name, archived = status == ProjectStatus.ARCHIVED, updatedAt = time.now()))
    }

    override suspend fun setArchived(id: EntityId, archived: Boolean) {
        val entity = dao.getEntity(id) ?: return
        val status = when {
            archived -> ProjectStatus.ARCHIVED.name
            entity.status == ProjectStatus.ARCHIVED.name -> ProjectStatus.ACTIVE.name
            else -> entity.status
        }
        val log = history?.active() == true
        db.withTransaction {
            dao.update(entity.copy(archived = archived, status = status, updatedAt = time.now()))
            if (log) history?.record(ActivityEntityType.PROJECT, id, if (archived) ActivityAction.ARCHIVED else ActivityAction.RESTORED, entity.title)
        }
    }

    override suspend fun delete(id: EntityId) {
        val log = history?.active() == true
        db.withTransaction {
            val title = dao.getEntity(id)?.title
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.PROJECT, id))
            dao.delete(id)
            if (log && title != null) history?.record(ActivityEntityType.PROJECT, id, ActivityAction.DELETED, title)
        }
    }

    override suspend fun reorder(orderedIds: List<EntityId>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setSortOrder(id, i.toLong()) } }
    }

    override suspend fun saveMilestone(milestone: ProjectMilestone): EntityId {
        require(milestone.title.isNotBlank()) { "Milestone title must not be blank" }
        return if (milestone.id == NEW_ID) {
            val order = dao.milestones(milestone.projectId).maxOfOrNull { it.sortOrder + 1 } ?: 0
            dao.insertMilestone(milestone.copy(sortOrder = order).toEntity().copy(id = 0))
        } else {
            dao.updateMilestone(milestone.toEntity())
            milestone.id
        }
    }

    override suspend fun setMilestoneCompleted(milestone: ProjectMilestone, completed: Boolean) {
        dao.updateMilestone(milestone.copy(completed = completed).toEntity())
    }

    override suspend fun deleteMilestone(id: EntityId) = dao.deleteMilestone(id)
}
