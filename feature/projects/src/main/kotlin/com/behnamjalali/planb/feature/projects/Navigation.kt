package com.behnamjalali.planb.feature.projects

import kotlinx.serialization.Serializable

@Serializable
data object ProjectsRoute

@Serializable
data class ProjectDetailRoute(val projectId: Long)

@Serializable
data class ProjectEditorRoute(val projectId: Long = 0)
