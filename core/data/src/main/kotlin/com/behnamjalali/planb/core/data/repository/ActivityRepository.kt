package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.data.DataHistory
import com.behnamjalali.planb.core.database.dao.ActivityLogDao
import com.behnamjalali.planb.core.database.entity.ActivityLogEntity
import com.behnamjalali.planb.core.model.ActivityAction
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.ActivityEntry
import com.behnamjalali.planb.core.model.EntityId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reading the activity history (Plan-B Pro #38); the repositories write it. */
interface ActivityRepository {
    /** The newest entries, optionally of one kind of item. */
    fun observeRecent(type: ActivityEntityType? = null, limit: Int = DEFAULT_LIMIT): Flow<List<ActivityEntry>>

    /** The history of one item, newest first. */
    fun observeFor(type: ActivityEntityType, id: EntityId): Flow<List<ActivityEntry>>

    /** Keeps the newest [DataHistory.MAX_ENTRIES] entries. */
    suspend fun prune(): Int

    companion object {
        const val DEFAULT_LIMIT = 500
    }
}

@Singleton
class OfflineActivityRepository @Inject constructor(private val dao: ActivityLogDao) : ActivityRepository {
    override fun observeRecent(type: ActivityEntityType?, limit: Int): Flow<List<ActivityEntry>> =
        (if (type == null) dao.observeRecent(limit) else dao.observeRecentOfType(type.name, limit)).map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeFor(type: ActivityEntityType, id: EntityId): Flow<List<ActivityEntry>> =
        dao.observeForEntity(type.name, id).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun prune(): Int = dao.pruneToNewest(DataHistory.MAX_ENTRIES)
}

/** Rows with a kind or action this version does not know (written by a newer app) are skipped. */
internal fun ActivityLogEntity.toModel(): ActivityEntry? {
    val type = ActivityEntityType.entries.firstOrNull { it.name == entityType } ?: return null
    val action = ActivityAction.entries.firstOrNull { it.name == this.action } ?: return null
    return ActivityEntry(id, type, entityId, action, at, summary)
}
