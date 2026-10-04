package com.behnamjalali.planb.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.behnamjalali.planb.core.database.entity.TagEntity
import java.time.Instant
import java.time.LocalDate

/** A completed task as statistics read it. */
data class CompletedTaskRow(
    val id: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "project_id") val projectId: Long?,
)

data class TaskTagRow(
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** A planned (top-level, not archived) task: its planned date and completion time. */
data class DueTaskRow(
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    val completed: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
)

data class FocusRow(
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "actual_duration_ms") val actualDurationMillis: Long,
)

/**
 * Read-only aggregation queries for Plan-B Pro statistics and the yearly report (#31). Rows in
 * the trash never count. Instants are epoch milliseconds; dates are epoch days.
 */
@Dao
interface StatisticsDao {
    @Query(
        "SELECT id, completed_at, due_date, project_id FROM tasks " +
            "WHERE completed = 1 AND deleted_at IS NULL AND completed_at >= :from AND completed_at < :to",
    )
    suspend fun completedTasks(from: Long, to: Long): List<CompletedTaskRow>

    @Query(
        "SELECT tt.task_id, tt.tag_id FROM task_tags tt JOIN tasks t ON t.id = tt.task_id " +
            "WHERE t.completed = 1 AND t.deleted_at IS NULL AND t.completed_at >= :from AND t.completed_at < :to",
    )
    suspend fun completedTaskTags(from: Long, to: Long): List<TaskTagRow>

    @Query(
        "SELECT due_date, completed, completed_at FROM tasks " +
            "WHERE deleted_at IS NULL AND archived = 0 AND parent_task_id IS NULL " +
            "AND due_date IS NOT NULL AND due_date >= :fromDay AND due_date <= :toDay",
    )
    suspend fun dueTasks(fromDay: Long, toDay: Long): List<DueTaskRow>

    @Query(
        "SELECT started_at, actual_duration_ms FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND started_at >= :from AND started_at < :to",
    )
    suspend fun focusSessions(from: Long, to: Long): List<FocusRow>

    @Query("SELECT created_at FROM notes WHERE deleted_at IS NULL AND created_at >= :from AND created_at < :to")
    suspend fun notesCreated(from: Long, to: Long): List<Instant>

    @Query("SELECT * FROM tags")
    suspend fun tags(): List<TagEntity>
}
