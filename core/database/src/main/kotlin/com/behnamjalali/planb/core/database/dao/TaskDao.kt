package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import com.behnamjalali.planb.core.database.model.TaskWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /**
     * Task lists are built by TaskQueryBuilder so all filters share one
     * well-indexed query shape. The query must select `tasks.*` plus the two
     * subtask count columns.
     */
    @Transaction
    @RawQuery(observedEntities = [TaskEntity::class, TaskTagCrossRef::class, TagEntity::class])
    fun observeTasks(query: SupportSQLiteQuery): Flow<List<TaskWithDetails>>

    @Transaction
    @RawQuery
    suspend fun getTasks(query: SupportSQLiteQuery): List<TaskWithDetails>

    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE t.id = :id")
    fun observeTask(id: Long): Flow<TaskWithDetails?>

    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE t.id = :id")
    suspend fun getTask(id: Long): TaskWithDetails?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getEntity(id: Long): TaskEntity?

    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE t.parent_task_id = :parentId ORDER BY t.completed, t.sort_order, t.id")
    fun observeSubtasks(parentId: Long): Flow<List<TaskWithDetails>>

    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentId")
    suspend fun getSubtaskEntities(parentId: Long): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE tasks SET archived = :archived, updated_at = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE tasks SET project_id = :projectId, updated_at = :now WHERE id IN (:ids)")
    suspend fun setProject(ids: List<Long>, projectId: Long?, now: Long)

    @Query("UPDATE tasks SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long, now: Long)

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM tasks")
    suspend fun maxSortOrder(): Long

    @Query("DELETE FROM task_tags WHERE task_id = :taskId")
    suspend fun clearTags(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<TaskTagCrossRef>)

    @Query("SELECT tag_id FROM task_tags WHERE task_id = :taskId")
    suspend fun tagIds(taskId: Long): List<Long>

    /** Tasks with a reminder that are still open (for rescheduling after boot). */
    @Query(
        "SELECT * FROM tasks WHERE reminder_offset_minutes IS NOT NULL AND completed = 0 " +
            "AND archived = 0 AND due_date IS NOT NULL",
    )
    suspend fun tasksWithReminders(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE completed = 1 AND completed_at >= :from AND completed_at < :to")
    fun observeCompletedBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT * FROM tasks WHERE completed = 1 AND completed_at >= :from AND completed_at < :to ORDER BY completed_at")
    suspend fun completedBetween(from: Long, to: Long): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND due_date IS NOT NULL " +
            "AND due_date >= :from AND due_date < :to ORDER BY due_date",
    )
    suspend fun openDueBetween(from: Long, to: Long): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND due_date IS NOT NULL " +
            "AND due_date >= :from AND due_date <= :to ORDER BY priority DESC, due_date LIMIT :limit",
    )
    suspend fun priorities(from: Long, to: Long, limit: Int): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    companion object {
        const val SELECT_WITH_COUNTS =
            "SELECT t.*, " +
                "(SELECT COUNT(*) FROM tasks s WHERE s.parent_task_id = t.id) AS subtask_count, " +
                "(SELECT COUNT(*) FROM tasks s WHERE s.parent_task_id = t.id AND s.completed = 1) AS completed_subtask_count " +
                "FROM tasks t"
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}
