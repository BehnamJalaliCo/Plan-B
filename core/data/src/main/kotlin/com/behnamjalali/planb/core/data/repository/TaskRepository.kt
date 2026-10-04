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
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef
import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.model.TaskView
import java.time.Instant
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

    /**
     * Number of tasks from the Today list completed in [from, to): top-level, not archived and
     * due on or before the day [to] ends. With the open TODAY view this is the day's total.
     */
    fun observeCompletedCount(from: java.time.Instant, to: java.time.Instant): Flow<Int>
    suspend fun getTask(id: EntityId): Task?
    suspend fun tasksWithReminders(): List<Task>

    /**
     * Inserts or updates; returns the id. Tags are replaced by [Task.tags]. Marking a recurring
     * task done here completes it exactly like [setCompleted] (the series continues in a new
     * occurrence), and reopening a completed occurrence takes the series back.
     */
    suspend fun save(task: Task): EntityId

    /**
     * Completing a recurring task completes this occurrence and creates the next one; returns
     * its id. Reopening an occurrence whose completion created a still untouched next
     * occurrence removes that occurrence again and moves the series back onto this task.
     */
    suspend fun setCompleted(id: EntityId, completed: Boolean): EntityId?
    suspend fun setCompleted(ids: List<EntityId>, completed: Boolean)

    /** Board moves: DONE completes (spawning recurrences), others reopen if needed. */
    suspend fun setStatus(id: EntityId, status: TaskStatus)
    suspend fun delete(ids: List<EntityId>)

    /** Archives (or restores) the tasks together with their subtasks. */
    suspend fun setArchived(ids: List<EntityId>, archived: Boolean)
    suspend fun moveToProject(ids: List<EntityId>, projectId: EntityId?)
    suspend fun duplicate(id: EntityId): EntityId

    /**
     * Puts [orderedIds] in this order by permuting their existing positions, so tasks that are
     * not in the list (other views, other projects) keep their place relative to them.
     */
    suspend fun reorder(orderedIds: List<EntityId>)

    /** Adds tracked time (e.g. a finished focus session) without rewriting the rest of the task. */
    suspend fun addActualMinutes(id: EntityId, minutes: Int)
    suspend fun upsertTag(tag: Tag): EntityId
    suspend fun deleteTag(id: EntityId)
}

@Singleton
class OfflineTaskRepository @Inject constructor(
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

    override fun observeCompletedCount(from: java.time.Instant, to: java.time.Instant): Flow<Int> {
        // The TODAY view holds tasks due on or before the day being shown, i.e. the day `to` closes.
        val dueBy = to.minusMillis(1).atZone(time.zone()).toLocalDate()
        return taskDao.observeCompletedForToday(from.toEpochMilli(), to.toEpochMilli(), dueBy)
    }

    override suspend fun getTask(id: EntityId): Task? = taskDao.getTask(id)?.toModel()

    override suspend fun tasksWithReminders(): List<Task> = taskDao.tasksWithReminders().map { it.toModel() }

    override suspend fun save(task: Task): EntityId {
        if (task.title.isBlank()) throw TaskValidationException("Task title must not be blank")
        if (task.parentTaskId != null && task.parentTaskId == task.id) throw TaskValidationException("Task cannot be its own parent")
        val now = time.now()
        val changes = ReminderChanges()
        val id = db.withTransaction {
            val existing = if (task.id != NEW_ID) taskDao.getEntity(task.id) else null
            val wasCompleted = existing?.completed == true
            // Done from the editor on a recurring task: store it open, then complete it through
            // the same path as the checkbox so the next occurrence is created.
            val completesSeries = task.isCompleted && !wasCompleted && task.recurrence != null
            val stored = if (completesSeries) task.copy(status = TaskStatus.TODO) else task
            val entity = stored.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = existing?.sortOrder ?: (taskDao.maxSortOrder() + SORT_STEP),
                completedAt = when {
                    stored.isCompleted -> existing?.completedAt ?: task.completedAt ?: now
                    else -> null
                },
                recurrenceAnchor = recurrenceAnchor(task, existing),
            ).toEntity()
            val savedId = if (existing == null) {
                taskDao.insert(entity.copy(id = 0))
            } else {
                taskDao.update(entity)
                entity.id
            }
            replaceTags(savedId, task.tags)
            searchDao.upsert(SearchIndexer.task(entity.copy(id = savedId)))
            when {
                completesSeries -> complete(taskDao.getEntity(savedId)!!, now, changes)
                wasCompleted && !task.isCompleted -> takeSeriesBack(existing!!, savedId, now, changes)
            }
            savedId
        }
        reminders.syncTask(id)
        changes.apply()
        return id
    }

    /**
     * The anchor counts occurrences of the series. It follows the task when the user moves
     * the date or changes the repeat pattern, so the next occurrence is computed from the
     * new schedule (a weekly task moved from Monday to Wednesday repeats on Wednesdays).
     * Changing only the end (until/count) keeps it.
     */
    private fun recurrenceAnchor(task: Task, existing: TaskEntity?): LocalDate? {
        val rule = task.recurrence ?: return null
        val date = task.dueDate ?: task.startDate ?: time.today()
        if (existing == null) return task.recurrenceAnchor ?: date
        val storedRule = RecurrenceRule.decode(existing.recurrence)
        val dateChanged = (existing.dueDate ?: existing.startDate) != (task.dueDate ?: task.startDate)
        val patternChanged = storedRule != null && storedRule.copy(until = null, count = null) != rule.copy(until = null, count = null)
        return if (dateChanged || patternChanged) date else task.recurrenceAnchor ?: date
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

    /** Reminder work collected inside a transaction and applied once it committed. */
    private inner class ReminderChanges {
        val sync = mutableListOf<EntityId>()
        val cancel = mutableListOf<EntityId>()

        suspend fun apply() {
            sync.forEach { reminders.syncTask(it) }
            cancel.forEach { reminders.cancelTask(it) }
        }
    }

    override suspend fun setCompleted(id: EntityId, completed: Boolean): EntityId? {
        val now = time.now()
        val changes = ReminderChanges()
        var nextId: EntityId? = null
        db.withTransaction {
            val entity = taskDao.getEntity(id) ?: return@withTransaction
            if (entity.completed == completed) return@withTransaction
            if (completed) {
                nextId = complete(entity, now, changes)
            } else {
                taskDao.update(entity.copy(completed = false, status = TaskStatus.TODO.name, completedAt = null, updatedAt = now))
                takeSeriesBack(entity, id, now, changes)
            }
        }
        reminders.syncTask(id)
        changes.apply()
        return nextId
    }

    /**
     * Marks [entity] done (inside a transaction). For a recurring task the completed
     * occurrence leaves the series and the next occurrence is created with the same tags and
     * fresh copies of the subtasks, all shifted by the same number of days. Returns its id.
     */
    private suspend fun complete(entity: TaskEntity, now: Instant, changes: ReminderChanges): EntityId? {
        val done = entity.copy(completed = true, status = TaskStatus.DONE.name, completedAt = now, updatedAt = now)
        val rule = RecurrenceRule.decode(entity.recurrence)
        if (rule == null) {
            taskDao.update(done)
            return null
        }
        var nextId: EntityId? = null
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
            taskDao.insertTagRefs(taskDao.tagIds(entity.id).map { TaskTagCrossRef(newId, it) })
            taskDao.getSubtaskEntities(entity.id).forEach { sub ->
                val copy = sub.copy(
                    id = 0, parentTaskId = newId, completed = false, status = TaskStatus.TODO.name,
                    dueDate = sub.dueDate?.plusDays(shift), startDate = sub.startDate?.plusDays(shift),
                    completedAt = null, createdAt = now, updatedAt = now, actualMinutes = null,
                )
                val subId = taskDao.insert(copy)
                searchDao.upsert(SearchIndexer.task(copy.copy(id = subId)))
                changes.sync += subId
            }
            searchDao.upsert(SearchIndexer.task(nextEntity.copy(id = newId)))
            changes.sync += newId
            nextId = newId
        }
        // The completed occurrence leaves the series; the series continues in the new task.
        taskDao.update(done.copy(recurrence = null))
        return nextId
    }

    /**
     * Reopening a completed occurrence (inside a transaction, [completed] being the row as it
     * was while done): if its completion created a next occurrence that nobody has touched
     * since (task and subtasks unchanged), that occurrence is removed and the series moves
     * back onto task [id]. Without this, unchecking leaves two open copies of the series.
     */
    private suspend fun takeSeriesBack(completed: TaskEntity, id: EntityId, now: Instant, changes: ReminderChanges) {
        val completedAt = completed.completedAt ?: return
        if (completed.recurrence != null) return
        val spawned = taskDao.findSpawnedOccurrence(
            afterId = completed.id,
            createdAt = completedAt,
            title = completed.title,
            parentId = completed.parentTaskId,
            projectId = completed.projectId,
            anchor = completed.recurrenceAnchor,
        ) ?: return
        val subtasks = taskDao.getSubtaskEntities(spawned.id)
        if (subtasks.any { it.updatedAt != it.createdAt }) return
        val removed = listOf(spawned.id) + subtasks.map { it.id }
        removed.forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.TASK, it)) }
        taskDao.delete(listOf(spawned.id))
        changes.cancel += removed
        val reopened = taskDao.getEntity(id) ?: return
        if (reopened.recurrence == null) {
            taskDao.update(reopened.copy(recurrence = spawned.recurrence, recurrenceAnchor = spawned.recurrenceAnchor, updatedAt = now))
        }
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
        if (ids.isEmpty()) return
        val all = db.withTransaction {
            // Subtasks share their parent's fate, so their reminders stop (and resume) with it.
            val withSubtasks = (ids + ids.chunked(QUERY_CHUNK).flatMap { taskDao.subtaskIds(it) }).distinct()
            withSubtasks.chunked(QUERY_CHUNK).forEach { taskDao.setArchived(it, archived, time.now().toEpochMilli()) }
            withSubtasks
        }
        all.forEach { reminders.syncTask(it) }
    }

    override suspend fun moveToProject(ids: List<EntityId>, projectId: EntityId?) {
        taskDao.setProject(ids, projectId, time.now().toEpochMilli())
    }

    override suspend fun duplicate(id: EntityId): EntityId {
        val now = time.now()
        val subtaskIds = mutableListOf<EntityId>()
        val newId = db.withTransaction {
            val source = taskDao.getEntity(id) ?: throw TaskValidationException("Task $id not found")
            val copy = source.copy(id = 0, createdAt = now, updatedAt = now, sortOrder = taskDao.maxSortOrder() + SORT_STEP)
            val newId = taskDao.insert(copy)
            taskDao.insertTagRefs(taskDao.tagIds(id).map { TaskTagCrossRef(newId, it) })
            taskDao.getSubtaskEntities(id).forEach { sub ->
                val subId = taskDao.insert(sub.copy(id = 0, parentTaskId = newId, createdAt = now, updatedAt = now))
                searchDao.upsert(SearchIndexer.task(sub.copy(id = subId)))
                subtaskIds += subId
            }
            searchDao.upsert(SearchIndexer.task(copy.copy(id = newId)))
            newId
        }
        reminders.syncTask(newId)
        subtaskIds.forEach { reminders.syncTask(it) }
        return newId
    }

    override suspend fun reorder(orderedIds: List<EntityId>) {
        val ids = orderedIds.distinct()
        if (ids.isEmpty()) return
        val now = time.now().toEpochMilli()
        db.withTransaction {
            val current = ids.chunked(QUERY_CHUNK).flatMap { taskDao.sortSlots(it) }.associate { it.id to it.sortOrder }
            val present = ids.filter { it in current }
            // The subset keeps the positions it already occupies, only in the new order. Ties
            // (from older data) are spread out so the new order is unambiguous.
            val slots = current.values.sorted().toMutableList()
            for (i in 1 until slots.size) if (slots[i] <= slots[i - 1]) slots[i] = slots[i - 1] + 1
            present.forEachIndexed { index, id ->
                if (current[id] != slots[index]) taskDao.setSortOrder(id, slots[index], now)
            }
        }
    }

    override suspend fun addActualMinutes(id: EntityId, minutes: Int) {
        if (minutes <= 0) return
        taskDao.addActualMinutes(id, minutes, time.now().toEpochMilli())
    }

    override suspend fun upsertTag(tag: Tag): EntityId {
        if (tag.name.isBlank()) throw TaskValidationException("Tag name must not be blank")
        return if (tag.id == NEW_ID) resolveTagId(tag) else tag.id.also { tagDao.update(tag.toEntity()) }
    }

    override suspend fun deleteTag(id: EntityId) = tagDao.delete(id)

    private companion object {
        const val SORT_STEP = 1024L

        /** Stays below SQLite's bound-parameter limit for IN (...) lists. */
        const val QUERY_CHUNK = 500
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
