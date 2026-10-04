package com.behnamjalali.planb.core.model

import java.time.Instant
import java.time.LocalDate

data class Goal(
    val id: EntityId = NEW_ID,
    val title: String,
    val description: String = "",
    val target: Double = 100.0,
    val currentValue: Double = 0.0,
    val unit: String = "",
    val deadline: LocalDate? = null,
    val projectId: EntityId? = null,
    val notes: String = "",
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val archived: Boolean = false,
) {
    val progress: Float
        get() = if (target <= 0.0) 0f else (currentValue / target).toFloat().coerceIn(0f, 1f)
}

data class GoalMilestone(
    val id: EntityId = NEW_ID,
    val goalId: EntityId,
    val title: String,
    val target: Double? = null,
    val completed: Boolean = false,
    val sortOrder: Long = 0,
)
