package com.behnamjalali.planb.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.behnamjalali.planb.core.database.entity.HabitCompletionEntity
import com.behnamjalali.planb.core.database.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = :archived ORDER BY created_at, id")
    fun observeHabits(archived: Boolean): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeHabit(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE archived = 0 AND reminder_time IS NOT NULL")
    suspend fun habitsWithReminders(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE archived = 0")
    suspend fun activeHabits(): List<HabitEntity>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    /** Completions in [from, to] (epoch days, inclusive) for all habits. */
    @Query("SELECT * FROM habit_completions WHERE date >= :from AND date <= :to")
    fun observeCompletions(from: Long, to: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId ORDER BY date")
    fun observeCompletionsFor(habitId: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date >= :from AND date <= :to")
    suspend fun completions(from: Long, to: Long): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId AND date = :date")
    suspend fun completion(habitId: Long, date: Long): HabitCompletionEntity?

    @Insert
    suspend fun insertCompletion(completion: HabitCompletionEntity): Long

    @Query("UPDATE habit_completions SET amount = :amount WHERE id = :id")
    suspend fun setAmount(id: Long, amount: Int)

    @Query("DELETE FROM habit_completions WHERE id = :id")
    suspend fun deleteCompletion(id: Long)

    /** Atomically adjusts a day's amount; removes the row when it reaches zero. */
    @Transaction
    suspend fun adjust(habitId: Long, date: Long, delta: Int, now: Long) {
        val existing = completion(habitId, date)
        if (existing == null) {
            if (delta > 0) {
                insertCompletion(
                    HabitCompletionEntity(
                        habitId = habitId,
                        date = java.time.LocalDate.ofEpochDay(date),
                        amount = delta,
                        createdAt = java.time.Instant.ofEpochMilli(now),
                    ),
                )
            }
        } else {
            val amount = existing.amount + delta
            if (amount <= 0) deleteCompletion(existing.id) else setAmount(existing.id, amount)
        }
    }
}
