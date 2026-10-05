package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Read queries for Plan-B Pro habits and focus (#28–#30): badge evaluation, challenges and the
 * mood tracker's correlations. No tables of its own; rows in the trash never count. Instants
 * are epoch milliseconds; dates are epoch days.
 */
@Dao
interface WellbeingDao {
    /** When each live task was completed (badges). */
    @Query("SELECT completed_at FROM tasks WHERE completed = 1 AND completed_at IS NOT NULL AND deleted_at IS NULL")
    suspend fun taskCompletionTimes(): List<Instant>

    /** Completed focus sessions in time order. */
    @Query("SELECT started_at, actual_duration_ms FROM focus_sessions WHERE status = 'COMPLETED' ORDER BY started_at, id")
    suspend fun completedFocusSessions(): List<FocusRow>

    /** Completed focus sessions that started in [from, to). */
    @Query(
        "SELECT started_at, actual_duration_ms FROM focus_sessions " +
            "WHERE status = 'COMPLETED' AND started_at >= :from AND started_at < :to ORDER BY started_at",
    )
    fun observeFocusSessions(from: Long, to: Long): Flow<List<FocusRow>>

    /** Days with a journal page whose note is not in the trash. */
    @Query("SELECT j.date FROM journal_entries j JOIN notes n ON n.id = j.note_id WHERE n.deleted_at IS NULL")
    suspend fun journalDays(): List<Long>

    @Query("SELECT date FROM mood_entries WHERE mood IS NOT NULL OR energy IS NOT NULL")
    suspend fun moodDays(): List<Long>

    @Query("SELECT * FROM mood_entries WHERE date >= :from AND date <= :to ORDER BY date DESC, time DESC, id DESC")
    fun observeMoods(from: Long, to: Long): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM challenges")
    suspend fun challenges(): List<ChallengeEntity>

    @Query("UPDATE challenges SET status = :status, completed_at = :completedAt, updated_at = :now WHERE id = :id")
    suspend fun setChallengeStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query("SELECT * FROM badges")
    suspend fun badges(): List<BadgeEntity>

    /** Badges awarded after [afterId] (the celebration shows each once). */
    @Query("SELECT * FROM badges WHERE id > :afterId ORDER BY id")
    fun observeBadgesAfter(afterId: Long): Flow<List<BadgeEntity>>

    @Query("SELECT COALESCE(MAX(id), 0) FROM badges")
    suspend fun maxBadgeId(): Long
}
