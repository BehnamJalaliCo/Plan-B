package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.behnamjalali.planb.core.database.entity.NoteDraftEntity

@Dao
interface NoteDraftDao {
    @Query("SELECT * FROM note_drafts WHERE note_id = :noteId")
    suspend fun get(noteId: Long): NoteDraftEntity?

    @Upsert
    suspend fun upsert(draft: NoteDraftEntity)

    @Query("DELETE FROM note_drafts WHERE note_id = :noteId")
    suspend fun delete(noteId: Long)

    /** Removes the draft only if it was not replaced by a newer one meanwhile. */
    @Query("DELETE FROM note_drafts WHERE note_id = :noteId AND updated_at <= :savedAt")
    suspend fun deleteIfNotNewer(noteId: Long, savedAt: Long)
}
