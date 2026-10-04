package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.dao.TagDao
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TaskView
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Filter for task lists. [view] is combined with the optional project/tag filters. */
data class TaskFilter(
    val view: TaskView = TaskView.ALL,
    val today: LocalDate,
    val projectId: EntityId? = null,
    val tagId: EntityId? = null,
    val sort: TaskSort = TaskSort.MANUAL,
    val topLevelOnly: Boolean = true,
    val limit: Int? = null,
    /** Inclusive upper bound for UPCOMING (null = no bound). */
    val upcomingUntil: LocalDate? = null,
    /** Optional inclusive due-date range applied on top of [view]. */
    val dueFrom: LocalDate? = null,
    val dueTo: LocalDate? = null,
)

class TaskValidationException(message: String) : IllegalArgumentException(message)

interface TaskRepository {
    fun observeTasks(filter: TaskFilter): Flow<List<Task>>
    fun observeTask(id: EntityId): Flow<Task?>
    fun observeSubtasks(parentId: EntityId): Flow<List<Task>>
    fun observeTags(): Flow<List<Tag>>
    fun observeCompletedCount(from: java.time.Instant, to: java.time.Instant): Flow<Int>
    suspend fun getTask(id: EntityId): Task?
    suspend fun tasksWithReminders(): List<Task>

    /** Inserts or updates; returns the id. Tags are replaced by [Task.tags]. */
    suspend fun save(task: Task): EntityId
    suspend fun setCompleted(id: EntityId, completed: Boolean): EntityId?
    suspend fun setCompleted(ids: List<EntityId>, completed: Boolean)

    /** Board moves: DONE completes (spawning recurrences), others reopen if needed. */
    suspend fun setStatus(id: EntityId, status: TaskStatus)
    suspend fun delete(ids: List<EntityId>)
    suspend fun setArchived(ids: List<EntityId>, archived: Boolean)
    suspend fun moveToProject(ids: List<EntityId>, projectId: EntityId?)
    suspend fun duplicate(id: EntityId): EntityId
    suspend fun reorder(orderedIds: List<EntityId>)
    suspend fun upsertTag(tag: Tag): EntityId
    suspend fun deleteTag(id: EntityId)
}

@Singleton
internal class OfflineTaskRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val taskDao: TaskDao,
    private val tagDao: TagDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    private val reminders: ReminderScheduler,
) : TaskRepository {

    override fun observeTasks(filter: TaskFilter): Flow<List<Task>> =
        taskDao.observeTasks(TaskQueryBuilder.build(filter)).map { rows -> rows.map { it.toModel() } }

    override fun observeTask(id: EntityId): Flow<Task?> = taskDao.observeTask(id).map { it?.toModel() }

    override fun observeSubtasks(parentId: EntityId): Flow<List<Task>> =
        taskDao.observeSubtasks(parentId).map { rows -> rows.map { it.toModel() } }

    override fun observeTags(): Flow<List<Tag>> = tagDao.observeTags().map { tags -> tags.map { it.toModel() } }

    override fun observeCompletedCount(from: java.time.Instant, to: java.time.Instant): Flow<Int> =
        taskDao.observeCompletedBetween(from.toEpochMilli(), to.toEpochMilli())

    override suspend fun getTask(id: EntityId): Task? = taskDao.getTask(id)?.toModel()

    override suspend fun tasksWithReminders(): List<Task> = taskDao.tasksWithReminders().map { it.toModel() }

    override suspend fun save(task: Task): EntityId {
        if (task.title.isBlank()) throw TaskValidationException("Task title must not be blank")
        if (task.parentTaskId != null && task.parentTaskId == task.id) throw TaskValidationException("Task cannot be its own parent")
        val now = time.now()
        val id = db.withTransaction {
            val existing = if (task.id != NEW_ID) taskDao.getEntity(task.id) else null
            val recurrenceAnchor = when {
                task.recurrence == null -> null
                task.recurrenceAnchor != null -> task.recurrenceAnchor
                else -> task.dueDate ?: task.startDate ?: time.today()
            }
            val entity = task.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (taskDao.maxSortOrder() + SORT_STEP),
                completedAt = when {
                    task.isCompleted -> existing?.completedAt ?: task.completedAt ?: now
                    else -> null
                },
                recurrenceAnchor = recurrenceAnchor,
            ).toEntity()
            val savedId = if (existing == null) {
                taskDao.insert(entity.copy(id = 0))
            } else {
                taskDao.update(entity)
                entity.id
            }
            replaceTags(savedId, task.tags)
            searchDao.upsert(SearchIndexer.task(entity.copy(id = savedId)))
            savedId
        }
        reminders.syncTask(id)
        return id
    }

    private suspend fun replaceTags(taskId: EntityId, tags: List<Tag>) {
        taskDao.clearTags(taskId)
        if (tags.isEmpty()) return
        val ids = tags.map { tag -> if (tag.id != NEW_ID) tag.id else resolveTagId(tag) }.distinct()
        taskDao.insertTagRefs(ids.map { TaskTagCrossRef(taskId, it) })
    }

    private suspend fun resolveTagId(tag: Tag): EntityId {
        val name = tag.name.trim().removePrefix("#")
        return tagDao.findByName(name)?.id ?: tagDao.insert(TagEntity(name = name, color = tag.color.key))
    }

    /**
     * Completing a recurring task completes this occurrence and creates the next
     * one (with the same tags and fresh subtasks). Returns the id of the next
     * occurrence, if any.
     */
    override suspend fun setCompleted(id: EntityId, completed: Boolean): EntityId? {
        val now = time.now()
        var nextId: EntityId? = null
        db.withTransaction {
            val entity = taskDao.getEntity(id) ?: return@withTransaction
            if (entity.completed == completed) return@withTransaction
            val rule = com.behnamjalali.planb.core.model.RecurrenceRule.decode(entity.recurrence)
            if (completed && rule != null) {
                val anchor = entity.recurrenceAnchor ?: entity.dueDate ?: time.today()
                val current = entity.dueDate ?: time.today()
                val next = RecurrenceEngine.nextOccurrence(rule, anchor, current)
                if (next != null) {
                    val shift = ChronoUnit.DAYS.between(current, next)
                    val nextEntity = entity.copy(
                        id = 0,
                        dueDate = next,
                        startDate = entity.startDate?.plusDays(shift),
                        createdAt = now,
                        updatedAt = now,
                        completedAt = null,
                        completed = false,
                        status = TaskStatus.TODO.name,
                        actualMinutes = null,
                    )
                    val newId = taskDao.insert(nextEntity)
                    taskDao.insertTagRefs(taskDao.tagIds(id).map { TaskTagCrossRef(newId, it) })
                    taskDao.getSubtaskEntities(id).forEach { sub ->
                        val subId = taskDao.insert(
                            sub.copy(
                                id = 0, parentTaskId = newId, completed = false, status = TaskStatus.TODO.name,
                                completedAt = null, createdAt = now, updatedAt = now,
                            ),
                        )
                        searchDao.upsert(SearchIndexer.task(sub.copy(id = subId)))
                    }
                    searchDao.upsert(SearchIndexer.task(nextEntity.copy(id = newId)))
                    nextId = newId
                }
                // The completed occurrence leaves the series; the series continues in the new task.
                taskDao.update(entity.copy(completed = true, status = TaskStatus.DONE.name, completedAt = now, updatedAt = now, recurrence = null))
            } else {
                taskDao.update(
                    entity.copy(
                        completed = completed,
                        status = if (completed) TaskStatus.DONE.name else TaskStatus.TODO.name,
                        completedAt = if (completed) now else null,
                        updatedAt = now,
                    ),
                )
            }
        }
        reminders.syncTask(id)
        nextId?.let { reminders.syncTask(it) }
        return nextId
    }

    override suspend fun setCompleted(ids: List<EntityId>, completed: Boolean) {
        ids.forEach { setCompleted(it, completed) }
    }

    override suspend fun setStatus(id: EntityId, status: TaskStatus) {
        if (status == TaskStatus.DONE) {
            setCompleted(id, true)
            return
        }
        val entity = taskDao.getEntity(id) ?: return
        if (entity.completed) setCompleted(id, false)
        val current = taskDao.getEntity(id) ?: return
        taskDao.update(current.copy(status = status.name, updatedAt = time.now()))
    }

    override suspend fun delete(ids: List<EntityId>) {
        if (ids.isEmpty()) return
        val allIds = db.withTransaction {
            // Subtasks are removed by cascade; their index rows must go too.
            val subtaskIds = ids.flatMap { id -> taskDao.getSubtaskEntities(id).map { it.id } }
            val all = ids + subtaskIds
            all.forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.TASK, it)) }
            taskDao.delete(ids)
            all
        }
        allIds.forEach { reminders.cancelTask(it) }
    }

    override suspend fun setArchived(ids: List<EntityId>, archived: Boolean) {
        taskDao.setArchived(ids, archived, time.now().toEpochMilli())
        ids.forEach { reminders.syncTask(it) }
    }

    override suspend fun moveToProject(ids: List<EntityId>, projectId: EntityId?) {
        taskDao.setProject(ids, projectId, time.now().toEpochMilli())
    }

    override suspend fun duplicate(id: EntityId): EntityId {
        val now = time.now()
        val newId = db.withTransaction {
            val source = taskDao.getEntity(id) ?: throw TaskValidationException("Task $id not found")
            val copy = source.copy(id = 0, createdAt = now, updatedAt = now, sortOrder = taskDao.maxSortOrder() + SORT_STEP)
            val newId = taskDao.insert(copy)
            taskDao.insertTagRefs(taskDao.tagIds(id).map { TaskTagCrossRef(newId, it) })
            taskDao.getSubtaskEntities(id).forEach { sub ->
                val subId = taskDao.insert(sub.copy(id = 0, parentTaskId = newId, createdAt = now, updatedAt = now))
                searchDao.upsert(SearchIndexer.task(sub.copy(id = subId)))
            }
            searchDao.upsert(SearchIndexer.task(copy.copy(id = newId)))
            newId
        }
        reminders.syncTask(newId)
        return newId
    }

    override suspend fun reorder(orderedIds: List<EntityId>) {
        val now = time.now().toEpochMilli()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> taskDao.setSortOrder(id, (index + 1) * SORT_STEP, now) }
        }
    }

    override suspend fun upsertTag(tag: Tag): EntityId {
        if (tag.name.isBlank()) throw TaskValidationException("Tag name must not be blank")
        return if (tag.id == NEW_ID) resolveTagId(tag) else tag.id.also { tagDao.update(tag.toEntity()) }
    }

    override suspend fun deleteTag(id: EntityId) = tagDao.delete(id)

    private companion object {
        const val SORT_STEP = 1024L
    }
}

/** Builds the single parameterized SQL shape used by every task list. */
internal object TaskQueryBuilder {
    fun build(filter: TaskFilter): SimpleSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val today = filter.today.toEpochDay()
        if (filter.topLevelOnly) where += "t.parent_task_id IS NULL"
        when (filter.view) {
            TaskView.INBOX -> where += "t.archived = 0 AND t.completed = 0 AND t.project_id IS NULL AND t.due_date IS NULL"
            TaskView.TODAY -> {
                where += "t.archived = 0 AND t.completed = 0 AND t.due_date IS NOT NULL AND t.due_date <= ?"
                args += today
            }
            TaskView.UPCOMING -> {
                where += "t.archived = 0 AND t.completed = 0 AND t.due_date > ?"
                args += today
                filter.upcomingUntil?.let {
                    where += "t.due_date <= ?"
                    args += it.toEpochDay()
                }
            }
            TaskView.SCHEDULED -> where += "t.archived = 0 AND t.completed = 0 AND t.due_date IS NOT NULL"
            TaskView.COMPLETED -> where += "t.archived = 0 AND t.completed = 1"
            TaskView.ARCHIVED -> where += "t.archived = 1"
            TaskView.ALL -> where += "t.archived = 0 AND t.completed = 0"
        }
        if (filter.dueFrom != null && filter.dueTo != null) {
            where += "t.due_date >= ? AND t.due_date <= ?"
            args += filter.dueFrom.toEpochDay()
            args += filter.dueTo.toEpochDay()
        }
        filter.projectId?.let {
            where += "t.project_id = ?"
            args += it
        }
        filter.tagId?.let {
            where += "EXISTS (SELECT 1 FROM task_tags tt WHERE tt.task_id = t.id AND tt.tag_id = ?)"
            args += it
        }
        val order = when {
            filter.view == TaskView.COMPLETED -> "t.completed_at DESC"
            filter.sort == TaskSort.MANUAL && filter.view in setOf(TaskView.TODAY, TaskView.UPCOMING, TaskView.SCHEDULED) ->
                "t.due_date, t.due_time IS NULL, t.due_time, t.sort_order"
            filter.sort == TaskSort.MANUAL -> "t.sort_order, t.id"
            filter.sort == TaskSort.DUE_DATE -> "t.due_date IS NULL, t.due_date, t.due_time IS NULL, t.due_time, t.sort_order"
            filter.sort == TaskSort.PRIORITY -> "t.priority DESC, t.due_date IS NULL, t.due_date, t.sort_order"
            filter.sort == TaskSort.CREATED -> "t.created_at DESC"
            else -> "t.title COLLATE NOCASE"
        }
        val sql = buildString {
            append(TaskDao.SELECT_WITH_COUNTS)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(order)
            filter.limit?.let { append(" LIMIT ").append(it) }
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
