package com.behnamjalali.planb.core.model

import java.time.Instant

/** Kinds of items that appear in the activity history. The names are stored; never rename. */
enum class ActivityEntityType { TASK, PROJECT, NOTE, NOTEBOOK, HABIT, GOAL, EVENT }

/** What happened to an item. The names are stored; never rename. */
enum class ActivityAction { CREATED, UPDATED, COMPLETED, REOPENED, DELETED, RESTORED, ARCHIVED }

/**
 * One row of the activity history (Plan-B Pro). [summary] is only a short label such as the
 * item's title; it never holds note bodies.
 */
data class ActivityEntry(
    val id: EntityId,
    val entityType: ActivityEntityType,
    val entityId: EntityId,
    val action: ActivityAction,
    val at: Instant,
    val summary: String,
)

/** What kind of item sits in the trash. */
enum class TrashItemType { TASK, NOTE }

/** An item in the trash (Plan-B Pro), kept for [TrashItem.RETENTION_DAYS] days. */
data class TrashItem(
    val type: TrashItemType,
    val id: EntityId,
    val title: String,
    val deletedAt: Instant,
    /** Subtasks that went to the trash together with a task. */
    val childCount: Int = 0,
    /** A locked note: only its title is shown. */
    val locked: Boolean = false,
) {
    companion object {
        const val RETENTION_DAYS = 30L
    }
}
