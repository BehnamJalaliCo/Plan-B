package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    suspend fun getActive(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE status IN ('COMPLETED', 'CANCELLED') ORDER BY started_at DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<FocusSessionEntity>>

    @Query(
        "SELECT COALESCE(SUM(actual_duration_ms), 0) FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND started_at >= :from AND started_at < :to",
    )
    fun observeFocusedMillis(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(actual_duration_ms), 0) FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND started_at >= :from AND started_at < :to",
    )
    suspend fun focusedMillis(from: Long, to: Long): Long

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
