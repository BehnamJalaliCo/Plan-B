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

/** Creates ([filterId] 0) or edits a custom smart list (Plan-B Pro #10). */
@Serializable
data class SmartListEditorRoute(val filterId: Long = 0)

/** Reorders and deletes the custom smart lists. */
@Serializable
data object SmartListsRoute

/** The Eisenhower matrix of open tasks (Plan-B Pro #13). */
@Serializable
data object EisenhowerRoute
