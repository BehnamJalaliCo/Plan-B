package com.behnamjalali.planb.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import com.behnamjalali.planb.core.database.entity.NoteEntity
import com.behnamjalali.planb.core.database.entity.NoteTagCrossRef
import com.behnamjalali.planb.core.database.entity.NotebookEntity
import com.behnamjalali.planb.core.database.entity.ProjectEntity
import com.behnamjalali.planb.core.database.entity.ProjectTagCrossRef
import com.behnamjalali.planb.core.database.entity.TagEntity
import com.behnamjalali.planb.core.database.entity.TaskEntity
import com.behnamjalali.planb.core.database.entity.TaskTagCrossRef

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TaskTagCrossRef::class, parentColumn = "task_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
    @ColumnInfo(name = "subtask_count") val subtaskCount: Int,
    @ColumnInfo(name = "completed_subtask_count") val completedSubtaskCount: Int,
    /** Open, live tasks this one waits for (task_dependencies, Plan-B Pro #14). */
    @ColumnInfo(name = "open_blocker_count") val openBlockerCount: Int = 0,
)

data class ProjectWithCounts(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ProjectTagCrossRef::class, parentColumn = "project_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
    @ColumnInfo(name = "total_tasks") val totalTasks: Int,
    @ColumnInfo(name = "completed_tasks") val completedTasks: Int,
    @ColumnInfo(name = "total_milestones") val totalMilestones: Int,
    @ColumnInfo(name = "completed_milestones") val completedMilestones: Int,
)

data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteTagCrossRef::class, parentColumn = "note_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
)

data class NotebookWithCount(
    @Embedded val notebook: NotebookEntity,
    @ColumnInfo(name = "note_count") val noteCount: Int,
)

data class HabitWithCompletions(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id")
    val completions: List<HabitCompletionEntity>,
)

/** Daily aggregates used by Weekly Review and statistics. */
data class DateCount(
    val date: Long,
    val count: Int,
)
