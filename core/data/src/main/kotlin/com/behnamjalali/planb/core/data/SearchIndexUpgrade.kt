package com.behnamjalali.planb.core.data

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The index stores normalized tokens, so a change to [SearchNormalizer] leaves existing
 * rows in the old form. This rebuilds the index once per normalizer version (recorded in
 * the device preferences), without touching the schema or user data.
 */
@Singleton
class SearchIndexUpgrade @Inject constructor(
    private val db: PlanBDatabase,
    private val maintenance: SearchIndexMaintenance,
    private val preferences: UserPreferencesDataSource,
) {
    /** Returns true when the index was rebuilt. */
    suspend fun rebuildIfOutdated(): Boolean {
        if (preferences.searchIndexVersion() == SearchNormalizer.VERSION) return false
        db.withTransaction { maintenance.rebuild() }
        preferences.setSearchIndexVersion(SearchNormalizer.VERSION)
        return true
    }
}
