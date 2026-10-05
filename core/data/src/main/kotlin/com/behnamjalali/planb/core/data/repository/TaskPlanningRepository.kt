package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.TaskDependencyEntity
import com.behnamjalali.planb.core.database.entity.TaskReminderEntity
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.TaskDependencies
import com.behnamjalali.planb.core.model.TaskPlanning
import com.behnamjalali.planb.core.model.TaskReminder
import com.behnamjalali.planb.core.model.TaskReminderKind
import com.behnamjalali.planb.core.model.TaskReminderRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Adding this dependency would make tasks wait for each other in a circle. */
class DependencyCycleException(val taskId: EntityId, val blockerId: EntityId) :
    IllegalArgumentException("Task $taskId cannot wait for $blockerId: that would create a cycle")

/**
 * Extra reminders, nagging and dependencies of tasks (Plan-B Pro #12 and #14), stored in
 * `task_reminders` and `task_dependencies`.
 *
 * `task_reminders` holds up to [TaskReminderRules.MAX_EXTRA] reminder rows (`OFFSET`,
 * `ABSOLUTE`, `DEADLINE`) per task, plus at most one `NAG` row whose `offset_minutes` is the
 * nag interval when it differs from the default. Whether a task nags at all is `tasks.nag`.
 */
interface TaskPlanningRepository {
    fun observePlanning(taskId: EntityId): Flow<TaskPlanning>
    suspend fun planning(taskId: EntityId): TaskPlanning

    /**
     * Replaces the extra reminders (at most four; extra ones are rejected) and the nag interval
     * of [taskId], then reschedules its alarm.
     */
    suspend fun setReminders(taskId: EntityId, reminders: List<TaskReminder>, nagIntervalMinutes: Int)

    /** Every dependency as task id → ids it waits for. */
    fun observeDependencies(): Flow<Map<EntityId, List<EntityId>>>

    /** Whether "[taskId] waits for [blockerId]" would create a cycle (or a self-dependency). */
    suspend fun wouldCreateCycle(taskId: EntityId, blockerId: EntityId): Boolean

    /**
     * Makes [taskId] wait for exactly [blockerIds]. Throws [DependencyCycleException] (and
     * changes nothing) when one of them would close a cycle.
     */
    suspend fun setDependencies(taskId: EntityId, blockerIds: Collection<EntityId>)
}

@Singleton
class OfflineTaskPlanningRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val reminders: ReminderScheduler,
) : TaskPlanningRepository {
    private val reminderDao get() = db.taskReminderDao()
    private val dependencyDao get() = db.taskDependencyDao()

    override fun observePlanning(taskId: EntityId): Flow<TaskPlanning> =
        combine(reminderDao.observeForTask(taskId), dependencyDao.observeDependencies(taskId)) { rows, blockers ->
            toPlanning(rows, blockers)
        }

    override suspend fun planning(taskId: EntityId): TaskPlanning =
        toPlanning(reminderDao.forTask(taskId), dependencyDao.dependencies(taskId))

    override suspend fun setReminders(taskId: EntityId, reminders: List<TaskReminder>, nagIntervalMinutes: Int) {
        val valid = reminders.filter { it.isValid() }
        if (valid.size > TaskReminderRules.MAX_EXTRA) {
            throw TaskValidationException("At most ${TaskReminderRules.MAX_EXTRA} extra reminders")
        }
        val interval = TaskReminderRules.normalizeNagInterval(nagIntervalMinutes)
        val rows = valid.map { it.toEntity(taskId) } +
            listOfNotNull(
                TaskReminderEntity(taskId = taskId, kind = NAG, offsetMinutes = interval)
                    .takeIf { interval != TaskReminderRules.DEFAULT_NAG_INTERVAL },
            )
        db.withTransaction { reminderDao.replace(taskId, rows) }
        this.reminders.syncTask(taskId)
    }

    override fun observeDependencies(): Flow<Map<EntityId, List<EntityId>>> =
        dependencyDao.observeAll().map { rows -> rows.groupBy({ it.taskId }, { it.dependsOnTaskId }) }

    override suspend fun wouldCreateCycle(taskId: EntityId, blockerId: EntityId): Boolean =
        TaskDependencies.wouldCreateCycle(edges(), taskId, blockerId)

    override suspend fun setDependencies(taskId: EntityId, blockerIds: Collection<EntityId>) {
        val wanted = blockerIds.toSet()
        db.withTransaction {
            val edges = edges().toMutableMap()
            edges.remove(taskId)
            // Checked one by one against the graph as it grows, so two new blockers that
            // depend on each other through this task are caught too.
            val accepted = mutableListOf<EntityId>()
            wanted.forEach { blocker ->
                if (TaskDependencies.wouldCreateCycle(edges, taskId, blocker)) throw DependencyCycleException(taskId, blocker)
                accepted += blocker
                edges[taskId] = accepted.toList()
            }
            dependencyDao.deleteForTask(taskId)
            accepted.forEach { dependencyDao.insert(TaskDependencyEntity(taskId, it)) }
        }
    }

    private suspend fun edges(): Map<EntityId, List<EntityId>> =
        dependencyDao.all().groupBy({ it.taskId }, { it.dependsOnTaskId })

    private fun toPlanning(rows: List<TaskReminderEntity>, blockers: List<Long>) = TaskPlanning(
        reminders = rows.mapNotNull { it.toModel() },
        nagIntervalMinutes = TaskReminderRules.normalizeNagInterval(rows.firstOrNull { it.kind == NAG }?.offsetMinutes),
        blockedBy = blockers,
    )

    private fun TaskReminder.isValid(): Boolean = when (kind) {
        TaskReminderKind.ABSOLUTE -> at != null
        TaskReminderKind.OFFSET, TaskReminderKind.DEADLINE -> (offsetMinutes ?: -1) >= 0
    }

    private fun TaskReminder.toEntity(taskId: EntityId) = TaskReminderEntity(
        taskId = taskId,
        kind = kind.name,
        offsetMinutes = if (kind == TaskReminderKind.ABSOLUTE) null else offsetMinutes,
        at = if (kind == TaskReminderKind.ABSOLUTE) at else null,
    )

    companion object {
        /** The settings row holding the nag interval; not a reminder. */
        const val NAG = "NAG"

        /** Unknown kinds (from a newer version) and the `NAG` row are not reminders. */
        internal fun TaskReminderEntity.toModel(): TaskReminder? {
            val kind = TaskReminderKind.entries.firstOrNull { it.name == this.kind } ?: return null
            return TaskReminder(id = id, kind = kind, offsetMinutes = offsetMinutes, at = at)
                .takeIf { if (kind == TaskReminderKind.ABSOLUTE) at != null else offsetMinutes != null }
        }
    }
}
