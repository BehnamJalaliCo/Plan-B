package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.GoalDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.GoalMilestone
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.SearchEntityType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface GoalRepository {
    fun observeGoals(archived: Boolean = false): Flow<List<Goal>>
    fun observeGoal(id: EntityId): Flow<Goal?>
    fun observeMilestones(goalId: EntityId): Flow<List<GoalMilestone>>
    suspend fun getGoal(id: EntityId): Goal?
    suspend fun save(goal: Goal): EntityId
    suspend fun updateProgress(id: EntityId, currentValue: Double)
    suspend fun setArchived(id: EntityId, archived: Boolean)
    suspend fun delete(id: EntityId)
    suspend fun saveMilestone(milestone: GoalMilestone): EntityId
    suspend fun setMilestoneCompleted(milestone: GoalMilestone, completed: Boolean)
    suspend fun deleteMilestone(id: EntityId)
}

@Singleton
internal class OfflineGoalRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: GoalDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
) : GoalRepository {
    override fun observeGoals(archived: Boolean) = dao.observeGoals(archived).map { l -> l.map { it.toModel() } }
    override fun observeGoal(id: EntityId) = dao.observeGoal(id).map { it?.toModel() }
    override fun observeMilestones(goalId: EntityId) = dao.observeMilestones(goalId).map { l -> l.map { it.toModel() } }
    override suspend fun getGoal(id: EntityId) = dao.getGoal(id)?.toModel()

    override suspend fun save(goal: Goal): EntityId {
        require(goal.title.isNotBlank()) { "Goal title must not be blank" }
        require(goal.target > 0) { "Goal target must be positive" }
        val now = time.now()
        return db.withTransaction {
            val existing = if (goal.id != NEW_ID) dao.getGoal(goal.id) else null
            val entity = goal.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                currentValue = goal.currentValue.coerceAtLeast(0.0),
            ).toEntity()
            val id = if (existing == null) dao.insert(entity.copy(id = 0)) else entity.id.also { dao.update(entity) }
            searchDao.upsert(SearchIndexer.goal(entity.copy(id = id)))
            id
        }
    }

    override suspend fun updateProgress(id: EntityId, currentValue: Double) {
        val goal = dao.getGoal(id) ?: return
        dao.update(goal.copy(currentValue = currentValue.coerceAtLeast(0.0), updatedAt = time.now()))
    }

    override suspend fun setArchived(id: EntityId, archived: Boolean) {
        val goal = dao.getGoal(id) ?: return
        dao.update(goal.copy(archived = archived, updatedAt = time.now()))
    }

    override suspend fun delete(id: EntityId) {
        db.withTransaction {
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.GOAL, id))
            dao.delete(id)
        }
    }

    override suspend fun saveMilestone(milestone: GoalMilestone): EntityId {
        require(milestone.title.isNotBlank()) { "Milestone title must not be blank" }
        return if (milestone.id == NEW_ID) {
            dao.insertMilestone(milestone.copy(sortOrder = dao.maxMilestoneOrder(milestone.goalId) + 1).toEntity().copy(id = 0))
        } else {
            dao.updateMilestone(milestone.toEntity())
            milestone.id
        }
    }

    override suspend fun setMilestoneCompleted(milestone: GoalMilestone, completed: Boolean) =
        dao.updateMilestone(milestone.copy(completed = completed).toEntity())

    override suspend fun deleteMilestone(id: EntityId) = dao.deleteMilestone(id)
}
