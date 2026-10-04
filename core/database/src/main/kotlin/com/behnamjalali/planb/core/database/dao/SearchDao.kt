package com.behnamjalali.planb.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.behnamjalali.planb.core.database.entity.SearchIndexEntity

data class SearchHit(
    @ColumnInfo(name = "entity_type") val entityType: Int,
    @ColumnInfo(name = "entity_id") val entityId: Long,
)

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<SearchIndexEntity>)

    @Query("DELETE FROM search_index WHERE rowid = :rowId")
    suspend fun delete(rowId: Long)

    @Query("DELETE FROM search_index")
    suspend fun clear()

    /** [match] must be a sanitized FTS4 query built by SearchIndexer.matchQuery. */
    @Query("SELECT entity_type, entity_id FROM search_index WHERE search_index MATCH :match LIMIT :limit")
    suspend fun search(match: String, limit: Int): List<SearchHit>
}
