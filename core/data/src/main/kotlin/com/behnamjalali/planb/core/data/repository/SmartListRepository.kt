package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.TaskDao
import com.behnamjalali.planb.core.database.entity.SavedFilterEntity
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.SavedFilter
import com.behnamjalali.planb.core.model.SmartDateRange
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.SmartFilterCodec
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Custom smart lists (Plan-B Pro #10), stored in `saved_filters` with a JSON query (see [SmartFilterCodec]). */
interface SmartListRepository {
    fun observeLists(): Flow<List<SavedFilter>>
    suspend fun getList(id: EntityId): SavedFilter?

    /** Inserts (appended at the end) or updates; returns the id. The name must not be blank. */
    suspend fun save(list: SavedFilter): EntityId
    suspend fun delete(id: EntityId)

    /** Puts the lists in this order. */
    suspend fun reorder(orderedIds: List<EntityId>)

    /** Top-level, non-archived tasks matching [filter], sorted by its sort. */
    fun observeTasks(filter: SmartFilter, today: LocalDate): Flow<List<Task>>
}

@Singleton
class OfflineSmartListRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val taskDao: TaskDao,
    private val time: TimeProvider,
) : SmartListRepository {
    private val dao get() = db.savedFilterDao()

    override fun observeLists(): Flow<List<SavedFilter>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getList(id: EntityId): SavedFilter? = dao.get(id)?.toModel()

    override suspend fun save(list: SavedFilter): EntityId {
        val name = list.name.trim()
        if (name.isBlank()) throw TaskValidationException("Smart list name must not be blank")
        val now = time.now()
        return db.withTransaction {
            val existing = if (list.id != NEW_ID) dao.get(list.id) else null
            val entity = SavedFilterEntity(
                id = existing?.id ?: 0,
                name = name,
                icon = list.icon.key,
                color = list.color.key,
                query = SmartFilterCodec.encode(list.filter),
                sortOrder = existing?.sortOrder ?: (dao.maxSortOrder() + 1),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            if (existing == null) dao.insert(entity) else entity.id.also { dao.update(entity) }
        }
    }

    override suspend fun delete(id: EntityId) = dao.delete(id)

    override suspend fun reorder(orderedIds: List<EntityId>) {
        db.withTransaction { orderedIds.distinct().forEachIndexed { index, id -> dao.setSortOrder(id, index + 1L) } }
    }

    override fun observeTasks(filter: SmartFilter, today: LocalDate): Flow<List<Task>> =
        taskDao.observeTasks(baseQuery(filter)).map { rows -> SmartFilterEvaluator.apply(rows.map { it.toModel() }, filter, today) }

    /** Narrows in SQL what is cheap (completion, archive, trash); the rest is [SmartFilterEvaluator]. */
    private fun baseQuery(filter: SmartFilter): SimpleSQLiteQuery {
        val openOnly = TaskStatus.DONE !in filter.statuses
        val where = buildList {
            add("t.deleted_at IS NULL")
            add("t.parent_task_id IS NULL")
            add("t.archived = 0")
            if (openOnly) add("t.completed = 0")
            if (filter.statuses == setOf(TaskStatus.DONE)) add("t.completed = 1")
        }
        return SimpleSQLiteQuery("${TaskDao.SELECT_WITH_COUNTS} WHERE ${where.joinToString(" AND ")} ORDER BY t.sort_order, t.id")
    }

    private fun SavedFilterEntity.toModel() = SavedFilter(
        id = id,
        name = name,
        icon = PlannerIcon.fromKey(icon),
        color = AccentColor.fromKey(color),
        filter = SmartFilterCodec.decode(query),
        sortOrder = sortOrder,
    )
}

/** Evaluates a [SmartFilter] on tasks: pure, so it is unit-tested directly. */
object SmartFilterEvaluator {
    private const val NEXT_DAYS = 7L

    fun matches(task: Task, filter: SmartFilter, today: LocalDate, normalizedText: String = SearchNormalizer.normalize(filter.text)): Boolean {
        if (task.archived || task.deletedAt != null) return false
        val statuses = filter.statuses.ifEmpty { OPEN }
        if (task.status !in statuses) return false
        if (filter.projectIds.isNotEmpty() || filter.noProject) {
            val inProject = task.projectId != null && task.projectId in filter.projectIds
            val withoutProject = filter.noProject && task.projectId == null
            if (!inProject && !withoutProject) return false
        }
        if (filter.tagIds.isNotEmpty() && task.tags.none { it.id in filter.tagIds }) return false
        if (filter.priorities.isNotEmpty() && task.priority !in filter.priorities) return false
        when (filter.hasDeadline) {
            true -> if (task.deadline == null) return false
            false -> if (task.deadline != null) return false
            null -> Unit
        }
        if (!dateMatches(task, filter, today)) return false
        if (normalizedText.isNotBlank()) {
            val haystack = SearchNormalizer.normalize(listOf(task.title, task.description, task.notes).joinToString(" "))
            if (!haystack.contains(normalizedText)) return false
        }
        return true
    }

    private fun dateMatches(task: Task, filter: SmartFilter, today: LocalDate): Boolean {
        val due = task.dueDate
        val from = filter.from
        val to = filter.to
        return when (filter.dateRange) {
            SmartDateRange.ANY -> true
            SmartDateRange.OVERDUE -> task.isOverdue(today)
            SmartDateRange.TODAY -> due == today
            SmartDateRange.NEXT_7_DAYS -> due != null && due >= today && due <= today.plusDays(NEXT_DAYS)
            SmartDateRange.NO_DATE -> due == null
            SmartDateRange.CUSTOM -> due != null && (from == null || due >= from) && (to == null || due <= to)
        }
    }

    fun apply(tasks: List<Task>, filter: SmartFilter, today: LocalDate): List<Task> {
        val text = SearchNormalizer.normalize(filter.text)
        return tasks.filter { matches(it, filter, today, text) }.sortedWith(comparator(filter.sort))
    }

    fun comparator(sort: TaskSort): Comparator<Task> {
        val manual = compareBy<Task> { it.sortOrder }.thenBy { it.id }
        val byDue = compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }.thenBy(nullsLast()) { it.dueTime }
        return when (sort) {
            TaskSort.MANUAL -> manual
            TaskSort.DUE_DATE -> byDue.then(manual)
            TaskSort.PRIORITY -> compareByDescending<Task> { it.priority.weight }.then(byDue).then(manual)
            TaskSort.CREATED -> compareByDescending<Task> { it.createdAt }.then(manual)
            TaskSort.TITLE -> compareBy<Task, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.then(manual)
            TaskSort.DEADLINE -> compareBy<Task, LocalDate?>(nullsLast()) { it.deadline }.then(byDue).then(manual)
        }
    }

    private val OPEN = setOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS)
}
