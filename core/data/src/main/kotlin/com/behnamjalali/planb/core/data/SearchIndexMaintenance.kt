package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.database.dao.BackupDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import javax.inject.Inject

/** Rebuilds the full-text index from stored records (after restore or bulk import). */
class SearchIndexMaintenance @Inject constructor(
    private val backupDao: BackupDao,
    private val searchDao: SearchDao,
) {
    /** Call inside the same transaction as the data change so the index never drifts. */
    suspend fun rebuild() {
        searchDao.clear()
        backupDao.tasks().chunked(CHUNK).forEach { list -> searchDao.upsertAll(list.map(SearchIndexer::task)) }
        searchDao.upsertAll(backupDao.projects().map(SearchIndexer::project))
        backupDao.notes().chunked(CHUNK).forEach { list -> searchDao.upsertAll(list.map(SearchIndexer::note)) }
        searchDao.upsertAll(backupDao.notebooks().map(SearchIndexer::notebook))
        searchDao.upsertAll(backupDao.habits().map(SearchIndexer::habit))
        searchDao.upsertAll(backupDao.goals().map(SearchIndexer::goal))
        searchDao.upsertAll(backupDao.events().map(SearchIndexer::event))
    }

    private companion object { const val CHUNK = 500 }
}
