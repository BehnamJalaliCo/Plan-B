package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    /**
     * Candidates for a date range: single events inside the range plus every
     * recurring event that started on or before the range end (expanded in code).
     */
    @Query(
        "SELECT * FROM calendar_events WHERE (recurrence IS NULL AND date >= :from AND date <= :to) " +
            "OR (recurrence IS NOT NULL AND date <= :to) ORDER BY date, all_day DESC, start_time",
    )
    fun observeCandidates(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query(
        "SELECT * FROM calendar_events WHERE (recurrence IS NULL AND date >= :from AND date <= :to) " +
            "OR (recurrence IS NOT NULL AND date <= :to)",
    )
    suspend fun candidates(from: Long, to: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    fun observeEvent(id: Long): Flow<CalendarEventEntity?>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getEvent(id: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE reminder_offset_minutes IS NOT NULL")
    suspend fun eventsWithReminders(): List<CalendarEventEntity>

    @Insert
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: Long)
}
