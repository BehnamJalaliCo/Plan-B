package com.behnamjalali.planb.feature.tasks

import kotlinx.serialization.Serializable

@Serializable
data object TasksRoute

/** [taskId] 0 creates a new task, optionally pre-filled with parent/project/date. */
@Serializable
data class TaskEditorRoute(
    val taskId: Long = 0,
    val parentId: Long? = null,
    val projectId: Long? = null,
    val dueEpochDay: Long? = null,
)
