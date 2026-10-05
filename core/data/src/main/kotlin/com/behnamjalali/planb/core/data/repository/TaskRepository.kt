package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.DataHistory
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
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.Deadlines
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.RecurrenceBasis
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
    /**
     * With [dueFrom]/[dueTo]: also include tasks whose time block (Plan-B Pro #6) starts in
     * `[scheduledFrom, scheduledTo)`, whatever their due date.
     */
    val scheduledFrom: java.time.Instant? = null,
    val scheduledTo: java.time.Instant? = null,
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

    /**
     * Deletes tasks with their subtasks. For Plan-B Pro users they go to the trash (restorable
     * for 30 days); otherwise they are deleted permanently, as always.
     */
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
    /** The trash and activity history (Plan-B Pro); null keeps the free behaviour. */
    private val history: DataHistory? = null,
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
        val deadlineBy = dueBy.plusDays(Deadlines.TODAY_WINDOW_DAYS.toLong())
        return taskDao.observeCompletedForToday(from.toEpochMilli(), to.toEpochMilli(), dueBy, deadlineBy)
    }

    override suspend fun getTask(id: EntityId): Task? = taskDao.getTask(id)?.toModel()

    override suspend fun tasksWithReminders(): List<Task> = taskDao.tasksWithReminders().map { it.toModel() }

    override suspend fun save(task: Task): EntityId {
        if (task.title.isBlank()) throw TaskValidationException("Task title must not be blank")
        if (task.parentTaskId != null && task.parentTaskId == task.id) throw TaskValidationException("Task cannot be its own parent")
        val now = time.now()
        val changes = ReminderChanges()
        val log = history?.active() == true
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
            if (log) {
                val action = when {
                    existing == null -> ActivityAction.CREATED
                    task.isCompleted && !wasCompleted -> ActivityAction.COMPLETED
                    wasCompleted && !task.isCompleted -> ActivityAction.REOPENED
                    else -> ActivityAction.UPDATED
                }
                history?.record(ActivityEntityType.TASK, savedId, action, task.title)
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
        val log = history?.active() == true
        db.withTransaction {
            val entity = taskDao.getEntity(id) ?: return@withTransaction
            if (entity.completed == completed) return@withTransaction
            if (completed) {
                nextId = complete(entity, now, changes)
            } else {
                taskDao.update(entity.copy(completed = false, status = TaskStatus.TODO.name, completedAt = null, updatedAt = now))
                takeSeriesBack(entity, id, now, changes)
            }
            if (log) history?.record(ActivityEntityType.TASK, id, if (completed) ActivityAction.COMPLETED else ActivityAction.REOPENED, entity.title)
        }
        reminders.syncTask(id)
        changes.apply()
        return nextId
    }

    /**
     * Marks [entity] done (inside a transaction). For a recurring task the completed
     * occurrence leaves the series and the next occurrence is created with the same tags and
     * fresh copies of the subtasks, all shifted by the same number of days. Returns its id.
     *
     * An "after completion" rule (Plan-B Pro #4) places the next occurrence its interval after
     * today instead of after the schedule; its count is carried as "occurrences left", so each
     * new occurrence holds one less and the last one ends the series.
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
        val afterCompletion = rule.basis == RecurrenceBasis.COMPLETION
        val next = when {
            !afterCompletion -> RecurrenceEngine.nextOccurrence(rule, anchor, current)
            (rule.count ?: Int.MAX_VALUE) <= 1 -> null
            else -> RecurrenceEngine.nextAfterCompletion(rule, time.today())
        }
        if (next != null) {
            val nextRule = if (afterCompletion) rule.copy(count = rule.count?.minus(1)) else rule
            val shift = ChronoUnit.DAYS.between(current, next)
            val nextEntity = entity.copy(
                id = 0,
                recurrence = nextRule.encode(),
                dueDate = next,
                startDate = entity.startDate?.plusDays(shift),
                deadline = entity.deadline?.plusDays(shift),
                // A time block belongs to one occurrence; the next one is planned again.
                scheduledStart = null,
                scheduledEnd = null,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                completed = false,
                status = TaskStatus.TODO.name,
                actualMinutes = null,
            )
            val newId = taskDao.insert(nextEntity)
            taskDao.insertTagRefs(taskDao.tagIds(entity.id).map { TaskTagCrossRef(newId, it) })
            copyRelativeReminders(entity.id, newId)
            taskDao.getSubtaskEntities(entity.id).filter { it.deletedAt == null }.forEach { sub ->
                val copy = sub.copy(
                    id = 0, parentTaskId = newId, completed = false, status = TaskStatus.TODO.name,
                    dueDate = sub.dueDate?.plusDays(shift), startDate = sub.startDate?.plusDays(shift),
                    deadline = sub.deadline?.plusDays(shift), scheduledStart = null, scheduledEnd = null,
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
     * Extra reminders relative to the due time or the deadline (`OFFSET`, `DEADLINE`) and the
     * nag interval (`NAG`) follow the task to its copy; fixed `ABSOLUTE` reminders belong to one
     * moment and are not copied.
     */
    private suspend fun copyRelativeReminders(fromId: EntityId, toId: EntityId) {
        val reminders = db.taskReminderDao().forTask(fromId).filter { it.kind != "ABSOLUTE" }
        if (reminders.isNotEmpty()) db.taskReminderDao().insertAll(reminders.map { it.copy(id = 0, taskId = toId) })
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
            // An "after completion" occurrence holds one occurrence less; give it back.
            val rule = RecurrenceRule.decode(spawned.recurrence)
            val left = rule?.count
            val recurrence = if (rule?.basis == RecurrenceBasis.COMPLETION && left != null) {
                rule.copy(count = left + 1).encode()
            } else {
                spawned.recurrence
            }
            taskDao.update(reopened.copy(recurrence = recurrence, recurrenceAnchor = spawned.recurrenceAnchor, updatedAt = now))
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
        val log = history?.active() == true
        db.withTransaction {
            taskDao.update(current.copy(status = status.name, updatedAt = time.now()))
            if (log) history?.record(ActivityEntityType.TASK, id, ActivityAction.UPDATED, current.title)
        }
    }

    override suspend fun delete(ids: List<EntityId>) {
        if (ids.isEmpty()) return
        if (history?.active() == true) moveToTrash(ids) else deletePermanently(ids)
    }

    /** Tasks and their live subtasks get the same `deleted_at`, so they are restored together. */
    private suspend fun moveToTrash(ids: List<EntityId>) {
        val now = time.now().toEpochMilli()
        val trashed = db.withTransaction {
            val roots = ids.distinct().mapNotNull { taskDao.getEntity(it) }.filter { it.deletedAt == null }
            val all = roots.map { it.id }.toMutableList()
            var frontier = all.toList()
            while (frontier.isNotEmpty()) {
                frontier = frontier.chunked(QUERY_CHUNK).flatMap { taskDao.liveSubtaskIds(it) }.filter { it !in all }
                all += frontier
            }
            all.chunked(QUERY_CHUNK).forEach { taskDao.setDeletedAt(it, now, now) }
            all.forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.TASK, it)) }
            roots.forEach { history?.record(ActivityEntityType.TASK, it.id, ActivityAction.DELETED, it.title) }
            all
        }
        trashed.forEach { reminders.cancelTask(it) }
    }

    /**
     * Brings a task back from the trash with the subtasks that went there with it (and a
     * trashed parent, without which it would stay hidden). It is searchable again and its
     * reminders are scheduled again.
     */
    suspend fun restoreFromTrash(id: EntityId) {
        val now = time.now().toEpochMilli()
        val log = history?.active() == true
        val restored = db.withTransaction {
            val entity = taskDao.getEntity(id) ?: return@withTransaction emptyList()
            val deletedAt = entity.deletedAt?.toEpochMilli() ?: return@withTransaction emptyList()
            val all = mutableListOf(id)
            var frontier = listOf(id)
            while (frontier.isNotEmpty()) {
                frontier = frontier.flatMap { taskDao.subtasksTrashedWith(it, deletedAt) }.filter { it !in all }
                all += frontier
            }
            var parentId = entity.parentTaskId
            while (parentId != null && parentId !in all) {
                val parent = taskDao.getEntity(parentId) ?: break
                if (parent.deletedAt != null) all += parent.id
                parentId = parent.parentTaskId
            }
            all.chunked(QUERY_CHUNK).forEach { taskDao.setDeletedAt(it, null, now) }
            all.forEach { taskId -> taskDao.getEntity(taskId)?.let { searchDao.upsert(SearchIndexer.task(it)) } }
            if (log) history?.record(ActivityEntityType.TASK, id, ActivityAction.RESTORED, entity.title)
            all
        }
        restored.forEach { reminders.syncTask(it) }
    }

    /** Ids of tasks that went to the trash before [before], for the 30-day purge. */
    suspend fun trashedBefore(before: Instant): List<EntityId> = taskDao.trashedBefore(before.toEpochMilli())

    /** Permanent delete (free users, "Delete forever" and the trash purge). */
    suspend fun deletePermanently(ids: List<EntityId>) {
        if (ids.isEmpty()) return
        val allIds = db.withTransaction {
            // Subtasks are removed by cascade; their index rows must go too.
            val subtaskIds = ids.flatMap { id -> taskDao.getSubtaskEntities(id).map { it.id } }
            val all = ids + subtaskIds
            all.forEach { searchDao.delete(SearchIndexer.rowId(SearchEntityType.TASK, it)) }
            taskDao.delete(ids)
            db.attachmentDao().deleteOrphans()
            all
        }
        allIds.forEach { reminders.cancelTask(it) }
    }

    override suspend fun setArchived(ids: List<EntityId>, archived: Boolean) {
        if (ids.isEmpty()) return
        val log = history?.active() == true
        val all = db.withTransaction {
            // Subtasks share their parent's fate, so their reminders stop (and resume) with it.
            val withSubtasks = (ids + ids.chunked(QUERY_CHUNK).flatMap { taskDao.subtaskIds(it) }).distinct()
            withSubtasks.chunked(QUERY_CHUNK).forEach { taskDao.setArchived(it, archived, time.now().toEpochMilli()) }
            if (log) {
                ids.distinct().forEach { taskId ->
                    val title = taskDao.getEntity(taskId)?.title ?: return@forEach
                    history?.record(ActivityEntityType.TASK, taskId, if (archived) ActivityAction.ARCHIVED else ActivityAction.RESTORED, title)
                }
            }
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
        val log = history?.active() == true
        val newId = db.withTransaction {
            val source = taskDao.getEntity(id) ?: throw TaskValidationException("Task $id not found")
            val copy = source.copy(id = 0, createdAt = now, updatedAt = now, sortOrder = taskDao.maxSortOrder() + SORT_STEP, deletedAt = null)
            val newId = taskDao.insert(copy)
            taskDao.insertTagRefs(taskDao.tagIds(id).map { TaskTagCrossRef(newId, it) })
            copyRelativeReminders(id, newId)
            taskDao.getSubtaskEntities(id).filter { it.deletedAt == null }.forEach { sub ->
                val subId = taskDao.insert(sub.copy(id = 0, parentTaskId = newId, createdAt = now, updatedAt = now))
                searchDao.upsert(SearchIndexer.task(sub.copy(id = subId)))
                subtaskIds += subId
            }
            searchDao.upsert(SearchIndexer.task(copy.copy(id = newId)))
            if (log) history?.record(ActivityEntityType.TASK, newId, ActivityAction.CREATED, copy.title)
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
        // Trashed tasks never appear in a list (the trash has its own query).
        where += "t.deleted_at IS NULL"
        if (filter.topLevelOnly) where += "t.parent_task_id IS NULL"
        when (filter.view) {
            TaskView.INBOX -> where += "t.archived = 0 AND t.completed = 0 AND t.project_id IS NULL AND t.due_date IS NULL"
            TaskView.TODAY -> {
                // Planned for today or earlier, or a hard deadline within a few days (Pro #11).
                where += "t.archived = 0 AND t.completed = 0 AND ((t.due_date IS NOT NULL AND t.due_date <= ?) OR " +
                    "(t.deadline IS NOT NULL AND t.deadline <= ?))"
                args += today
                args += today + Deadlines.TODAY_WINDOW_DAYS
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
            if (filter.scheduledFrom != null && filter.scheduledTo != null) {
                where += "((t.due_date >= ? AND t.due_date <= ?) OR (t.scheduled_start >= ? AND t.scheduled_start < ?))"
                args += filter.dueFrom.toEpochDay()
                args += filter.dueTo.toEpochDay()
                args += filter.scheduledFrom.toEpochMilli()
                args += filter.scheduledTo.toEpochMilli()
            } else {
                where += "t.due_date >= ? AND t.due_date <= ?"
                args += filter.dueFrom.toEpochDay()
                args += filter.dueTo.toEpochDay()
            }
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
            filter.sort == TaskSort.MANUAL && filter.view == TaskView.TODAY ->
                "COALESCE(t.due_date, t.deadline), t.due_time IS NULL, t.due_time, t.sort_order"
            filter.sort == TaskSort.MANUAL && filter.view in setOf(TaskView.UPCOMING, TaskView.SCHEDULED) ->
                "t.due_date, t.due_time IS NULL, t.due_time, t.sort_order"
            filter.sort == TaskSort.MANUAL -> "t.sort_order, t.id"
            filter.sort == TaskSort.DUE_DATE -> "t.due_date IS NULL, t.due_date, t.due_time IS NULL, t.due_time, t.sort_order"
            filter.sort == TaskSort.PRIORITY -> "t.priority DESC, t.due_date IS NULL, t.due_date, t.sort_order"
            filter.sort == TaskSort.CREATED -> "t.created_at DESC"
            filter.sort == TaskSort.DEADLINE -> "t.deadline IS NULL, t.deadline, t.due_date IS NULL, t.due_date, t.sort_order"
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
