package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate

enum class ProjectStatus { ACTIVE, PAUSED, COMPLETED, ARCHIVED }

enum class ProgressMode { TASKS, MILESTONES, MANUAL }

data class Project(
    val id: EntityId = NEW_ID,
    val title: String,
    val description: String = "",
    val color: AccentColor = AccentColor.LAVENDER,
    val icon: PlannerIcon = PlannerIcon.FOLDER,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val progressMode: ProgressMode = ProgressMode.TASKS,
    /** 0..1, used when [progressMode] is MANUAL. */
    val manualProgress: Float = 0f,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val sortOrder: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
    val tags: List<Tag> = emptyList(),
)

data class ProjectMilestone(
    val id: EntityId = NEW_ID,
    val projectId: EntityId,
    val title: String,
    val date: LocalDate? = null,
    val completed: Boolean = false,
    val sortOrder: Long = 0,
)

/** Project with the aggregate counts needed to compute progress. */
data class ProjectSummary(
    val project: Project,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val totalMilestones: Int = 0,
    val completedMilestones: Int = 0,
) {
    val progress: Float
        get() = ProjectProgress.compute(this)
}

object ProjectProgress {
    fun compute(summary: ProjectSummary): Float {
        val project = summary.project
        if (project.status == ProjectStatus.COMPLETED) return 1f
        return when (project.progressMode) {
            ProgressMode.MANUAL -> project.manualProgress.coerceIn(0f, 1f)
            ProgressMode.TASKS -> ratio(summary.completedTasks, summary.totalTasks)
            ProgressMode.MILESTONES -> ratio(summary.completedMilestones, summary.totalMilestones)
        }
    }

    private fun ratio(done: Int, total: Int): Float =
        if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}
