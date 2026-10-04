package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.SearchEntityType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A habit plus its per-day amounts for the requested window. */
data class HabitWithHistory(val habit: Habit, val amounts: Map<LocalDate, Int>)

interface HabitRepository {
    fun observeHabits(from: LocalDate, to: LocalDate, archived: Boolean = false): Flow<List<HabitWithHistory>>
    fun observeHabit(id: EntityId): Flow<HabitWithHistory?>
    suspend fun getHabit(id: EntityId): Habit?
    suspend fun habitsWithReminders(): List<Habit>
    suspend fun amountOn(habitId: EntityId, date: LocalDate): Int
    suspend fun save(habit: Habit): EntityId
    suspend fun checkIn(habitId: EntityId, date: LocalDate, delta: Int = 1)
    suspend fun setArchived(id: EntityId, archived: Boolean)
    suspend fun delete(id: EntityId)
}

@Singleton
internal class OfflineHabitRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: HabitDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    private val reminders: ReminderScheduler,
) : HabitRepository {
    override fun observeHabits(from: LocalDate, to: LocalDate, archived: Boolean): Flow<List<HabitWithHistory>> =
        combine(dao.observeHabits(archived), dao.observeCompletions(from.toEpochDay(), to.toEpochDay())) { habits, completions ->
            val byHabit = completions.groupBy { it.habitId }
            habits.map { h ->
                HabitWithHistory(h.toModel(), byHabit[h.id].orEmpty().associate { it.date to it.amount })
            }
        }

    override fun observeHabit(id: EntityId): Flow<HabitWithHistory?> =
        combine(dao.observeHabit(id), dao.observeCompletionsFor(id)) { habit, completions ->
            habit?.let { HabitWithHistory(it.toModel(), completions.associate { c -> c.date to c.amount }) }
        }

    override suspend fun getHabit(id: EntityId) = dao.getHabit(id)?.toModel()
    override suspend fun habitsWithReminders() = dao.habitsWithReminders().map { it.toModel() }
    override suspend fun amountOn(habitId: EntityId, date: LocalDate) = dao.completion(habitId, date.toEpochDay())?.amount ?: 0

    override suspend fun save(habit: Habit): EntityId {
        require(habit.title.isNotBlank()) { "Habit title must not be blank" }
        val now = time.now()
        val id = db.withTransaction {
            val existing = if (habit.id != NEW_ID) dao.getHabit(habit.id) else null
            val entity = habit.copy(createdAt = existing?.createdAt ?: now, updatedAt = now).toEntity()
            val id = if (existing == null) dao.insert(entity.copy(id = 0)) else entity.id.also { dao.update(entity) }
            searchDao.upsert(SearchIndexer.habit(entity.copy(id = id)))
            id
        }
        reminders.syncHabit(id)
        return id
    }

    override suspend fun checkIn(habitId: EntityId, date: LocalDate, delta: Int) {
        dao.adjust(habitId, date.toEpochDay(), delta, time.now().toEpochMilli())
    }

    override suspend fun setArchived(id: EntityId, archived: Boolean) {
        val entity = dao.getHabit(id) ?: return
        dao.update(entity.copy(archived = archived, updatedAt = time.now()))
        reminders.syncHabit(id)
    }

    override suspend fun delete(id: EntityId) {
        db.withTransaction {
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.HABIT, id))
            dao.delete(id)
        }
        reminders.cancelHabit(id)
    }
}
