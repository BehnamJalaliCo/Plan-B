package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.EntityId
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether Plan-B Pro is active. The app binds it to the billing entitlement; the data layer only
 * asks it, so it never depends on billing.
 */
fun interface ProStatusSource {
    suspend fun isPro(): Boolean

    companion object {
        val Free = ProStatusSource { false }
    }
}

/**
 * The trash and the activity history (Plan-B Pro #38) as seen by the repositories.
 *
 * For Pro users deletions of tasks and notes go to the trash and changes are written to
 * `activity_log`; for everyone else nothing changes (deletions stay permanent after the undo
 * snackbar, nothing is logged). Repositories ask [active] once per operation, outside their
 * transaction, then call [record] inside it, so a history row commits with its change.
 */
@Singleton
class DataHistory @Inject constructor(
    private val db: PlanBDatabase,
    private val time: TimeProvider,
    private val pro: ProStatusSource,
) {
    /** Whether deletions go to the trash and changes are logged. */
    suspend fun active(): Boolean = runCatching { pro.isPro() }.getOrDefault(false)

    /**
     * Writes one history row. [summary] is a short label (a title), never a note body. A burst of
     * edits of the same item becomes one entry: an edit within [MERGE_WINDOW] of the previous
     * "created" or "updated" entry only refreshes that entry.
     */
    suspend fun record(type: ActivityEntityType, id: EntityId, action: ActivityAction, summary: String) {
        val dao = db.activityLogDao()
        val now = time.now()
        val label = summary.trim().replace('\n', ' ').take(MAX_SUMMARY)
        if (action == ActivityAction.UPDATED) {
            val last = dao.latestFor(type.name, id)
            val age = last?.let { Duration.between(it.at, now) }
            if (last != null && age != null && !age.isNegative && age < MERGE_WINDOW &&
                (last.action == ActivityAction.UPDATED.name || last.action == ActivityAction.CREATED.name)
            ) {
                val at = if (last.action == ActivityAction.CREATED.name) last.at else now
                if (at != last.at || label != last.summary) dao.touch(last.id, at.toEpochMilli(), label)
                return
            }
        }
        val rowId = dao.insert(ActivityLogEntity(entityType = type.name, entityId = id, action = action.name, at = now, summary = label))
        if (rowId % PRUNE_EVERY == 0L) dao.pruneToNewest(MAX_ENTRIES)
    }

    /** Forgets an item that never really existed (an empty note discarded by the editor). */
    suspend fun forget(type: ActivityEntityType, id: EntityId) = db.activityLogDao().deleteForEntity(type.name, id)

    companion object {
        /** The history keeps at most this many entries (oldest are dropped). */
        const val MAX_ENTRIES = 5_000
        const val MAX_SUMMARY = 120
        val MERGE_WINDOW: Duration = Duration.ofMinutes(10)
        private const val PRUNE_EVERY = 100L
    }
}
