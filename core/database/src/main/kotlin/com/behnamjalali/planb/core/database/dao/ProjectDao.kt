package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectMilestoneEntity
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.model.ProjectWithCounts
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE p.archived = :archived ORDER BY p.sort_order, p.id")
    fun observeProjects(archived: Boolean): Flow<List<ProjectWithCounts>>

    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE p.id = :id")
    fun observeProject(id: Long): Flow<ProjectWithCounts?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getEntity(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY sort_order, id")
    fun observeActiveEntities(): Flow<List<ProjectEntity>>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE projects SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM projects")
    suspend fun maxSortOrder(): Long

    @Query("DELETE FROM project_tags WHERE project_id = :projectId")
    suspend fun clearTags(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<ProjectTagCrossRef>)

    @Query("SELECT * FROM project_milestones WHERE project_id = :projectId ORDER BY sort_order, id")
    fun observeMilestones(projectId: Long): Flow<List<ProjectMilestoneEntity>>

    @Query("SELECT * FROM project_milestones WHERE project_id = :projectId ORDER BY sort_order, id")
    suspend fun milestones(projectId: Long): List<ProjectMilestoneEntity>

    @Insert
    suspend fun insertMilestone(milestone: ProjectMilestoneEntity): Long

    @Update
    suspend fun updateMilestone(milestone: ProjectMilestoneEntity)

    @Query("DELETE FROM project_milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    companion object {
        const val SELECT_WITH_COUNTS =
            "SELECT p.*, " +
                "(SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.parent_task_id IS NULL AND t.archived = 0) AS total_tasks, " +
                "(SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.parent_task_id IS NULL AND t.archived = 0 AND t.completed = 1) AS completed_tasks, " +
                "(SELECT COUNT(*) FROM project_milestones m WHERE m.project_id = p.id) AS total_milestones, " +
                "(SELECT COUNT(*) FROM project_milestones m WHERE m.project_id = p.id AND m.completed = 1) AS completed_milestones " +
                "FROM projects p"
    }
}
