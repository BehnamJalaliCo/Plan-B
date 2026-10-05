package com.behnamjalali.planb.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import com.behnamjalali.planb.core.database.model.TaskWithDetails
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** A row of the task trash list. */
data class TaskTrashRow(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
    @ColumnInfo(name = "child_count") val childCount: Int,
)

/** A task's manual position (for reordering a subset without disturbing the others). */
data class TaskSortSlot(
    val id: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Long,
)

@Dao
interface TaskDao {
    /**
     * Task lists are built by TaskQueryBuilder so all filters share one
     * well-indexed query shape. The query must select `tasks.*` plus the two
     * subtask count columns.
     */
    @Transaction
    @RawQuery(observedEntities = [TaskEntity::class, TaskTagCrossRef::class, TagEntity::class, TaskDependencyEntity::class])
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
    @Query("$SELECT_WITH_COUNTS WHERE t.parent_task_id = :parentId AND t.deleted_at IS NULL ORDER BY t.completed, t.sort_order, t.id")
    fun observeSubtasks(parentId: Long): Flow<List<TaskWithDetails>>

    @Query("SELECT * FROM tasks WHERE parent_task_id = :parentId")
    suspend fun getSubtaskEntities(parentId: Long): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE parent_task_id IN (:parentIds)")
    suspend fun subtaskIds(parentIds: List<Long>): List<Long>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** Moves tasks to the trash (a time) or restores them (null). Lists never show trashed tasks. */
    @Query("UPDATE tasks SET deleted_at = :deletedAt, updated_at = :now WHERE id IN (:ids)")
    suspend fun setDeletedAt(ids: List<Long>, deletedAt: Long?, now: Long)

    /** The trash: top-level tasks with their subtask counts, most recently deleted first. */
    @Transaction
    @Query("$SELECT_WITH_COUNTS WHERE t.deleted_at IS NOT NULL ORDER BY t.deleted_at DESC")
    fun observeTrash(): Flow<List<TaskWithDetails>>

    /** Ids of tasks trashed before [before] (epoch ms), for purging after the retention period. */
    @Query("SELECT id FROM tasks WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun trashedBefore(before: Long): List<Long>

    @Query("UPDATE tasks SET archived = :archived, updated_at = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE tasks SET project_id = :projectId, updated_at = :now WHERE id IN (:ids)")
    suspend fun setProject(ids: List<Long>, projectId: Long?, now: Long)

    @Query("UPDATE tasks SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long, now: Long)

    @Query("SELECT id, sort_order FROM tasks WHERE id IN (:ids)")
    suspend fun sortSlots(ids: List<Long>): List<TaskSortSlot>

    @Query("UPDATE tasks SET actual_minutes = COALESCE(actual_minutes, 0) + :minutes, updated_at = :now WHERE id = :id")
    suspend fun addActualMinutes(id: Long, minutes: Int, now: Long)

    /**
     * The open occurrence that completing a recurring task created: inserted after it, at the
     * completion instant, as a copy of it (same title, parent, project and series anchor) and
     * never modified since.
     */
    @Query(
        "SELECT * FROM tasks WHERE id > :afterId AND completed = 0 AND recurrence IS NOT NULL AND deleted_at IS NULL " +
            "AND created_at = :createdAt AND updated_at = created_at AND title = :title " +
            "AND parent_task_id IS :parentId AND project_id IS :projectId AND recurrence_anchor IS :anchor " +
            "ORDER BY id LIMIT 1",
    )
    suspend fun findSpawnedOccurrence(
        afterId: Long,
        createdAt: Instant,
        title: String,
        parentId: Long?,
        projectId: Long?,
        anchor: LocalDate?,
    ): TaskEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM tasks")
    suspend fun maxSortOrder(): Long

    @Query("DELETE FROM task_tags WHERE task_id = :taskId")
    suspend fun clearTags(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<TaskTagCrossRef>)

    @Query("SELECT tag_id FROM task_tags WHERE task_id = :taskId")
    suspend fun tagIds(taskId: Long): List<Long>

    /**
     * Open tasks with a reminder (for rescheduling after boot): the primary one, or extra
     * reminders in task_reminders (Plan-B Pro #12; `NAG` rows are settings, not reminders).
     */
    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND deleted_at IS NULL AND " +
            "((reminder_offset_minutes IS NOT NULL AND due_date IS NOT NULL) OR " +
            "EXISTS (SELECT 1 FROM task_reminders r WHERE r.task_id = tasks.id AND r.kind != 'NAG'))",
    )
    suspend fun tasksWithReminders(): List<TaskEntity>

    /**
     * Completed part of the Today list: top-level, non-archived tasks due on or before [dueBy]
     * (or with a deadline on or before [deadlineBy]) that were completed in [from, to). Matches
     * the open TODAY view, so done + open is the day's total.
     */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE completed = 1 AND archived = 0 AND deleted_at IS NULL AND parent_task_id IS NULL " +
            "AND ((due_date IS NOT NULL AND due_date <= :dueBy) OR (deadline IS NOT NULL AND deadline <= :deadlineBy)) " +
            "AND completed_at >= :from AND completed_at < :to",
    )
    fun observeCompletedForToday(from: Long, to: Long, dueBy: LocalDate, deadlineBy: LocalDate): Flow<Int>

    @Query(
        "SELECT * FROM tasks WHERE completed = 1 AND deleted_at IS NULL AND completed_at >= :from AND completed_at < :to " +
            "ORDER BY completed_at",
    )
    suspend fun completedBetween(from: Long, to: Long): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND deleted_at IS NULL AND due_date IS NOT NULL " +
            "AND due_date >= :from AND due_date < :to ORDER BY due_date",
    )
    suspend fun openDueBetween(from: Long, to: Long): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND deleted_at IS NULL AND due_date IS NOT NULL " +
            "AND due_date >= :from AND due_date <= :to ORDER BY priority DESC, due_date LIMIT :limit",
    )
    suspend fun priorities(from: Long, to: Long, limit: Int): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    /**
     * The trash as shown to the user: trashed tasks whose parent did not go to the trash with
     * them, with the number of subtasks trashed together (same `deleted_at`), newest first.
     */
    @Query(
        "SELECT t.id, t.title, t.deleted_at, " +
            "(SELECT COUNT(*) FROM tasks s WHERE s.parent_task_id = t.id AND s.deleted_at = t.deleted_at) AS child_count " +
            "FROM tasks t WHERE t.deleted_at IS NOT NULL AND NOT EXISTS " +
            "(SELECT 1 FROM tasks p WHERE p.id = t.parent_task_id AND p.deleted_at = t.deleted_at) " +
            "ORDER BY t.deleted_at DESC, t.id DESC",
    )
    fun observeTrashRows(): Flow<List<TaskTrashRow>>

    /** Live (not trashed) subtasks of [parentIds]; they go to the trash with their parent. */
    @Query("SELECT id FROM tasks WHERE parent_task_id IN (:parentIds) AND deleted_at IS NULL")
    suspend fun liveSubtaskIds(parentIds: List<Long>): List<Long>

    /** Subtasks that went to the trash together with [parentId] (same time). */
    @Query("SELECT id FROM tasks WHERE parent_task_id = :parentId AND deleted_at = :deletedAt")
    suspend fun subtasksTrashedWith(parentId: Long, deletedAt: Long): List<Long>

    companion object {
        const val SELECT_WITH_COUNTS =
            "SELECT t.*, " +
                "(SELECT COUNT(*) FROM tasks s WHERE s.parent_task_id = t.id AND s.deleted_at IS NULL) AS subtask_count, " +
                "(SELECT COUNT(*) FROM tasks s WHERE s.parent_task_id = t.id AND s.deleted_at IS NULL AND s.completed = 1) " +
                "AS completed_subtask_count, " +
                "(SELECT COUNT(*) FROM task_dependencies d JOIN tasks b ON b.id = d.depends_on_task_id " +
                "WHERE d.task_id = t.id AND b.completed = 0 AND b.deleted_at IS NULL) AS open_blocker_count " +
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
