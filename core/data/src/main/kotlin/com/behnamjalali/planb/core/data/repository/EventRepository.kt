package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.SearchIndexer
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.EventDao
import com.behnamjalali.planb.core.database.dao.SearchDao
import com.behnamjalali.planb.core.database.entity.CalendarEventEntity
import com.behnamjalali.planb.core.datetime.RecurrenceEngine
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.SearchEntityType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface EventRepository {
    /** Occurrences (recurring events expanded) between [from] and [to], inclusive, sorted. */
    fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<EventOccurrence>>
    fun observeEvent(id: EntityId): Flow<CalendarEvent?>
    suspend fun getEvent(id: EntityId): CalendarEvent?
    suspend fun eventsWithReminders(): List<CalendarEvent>
    suspend fun occurrences(from: LocalDate, to: LocalDate): List<EventOccurrence>
    suspend fun save(event: CalendarEvent): EntityId
    suspend fun delete(id: EntityId)
}

class EventValidationException(message: String) : IllegalArgumentException(message)

@Singleton
internal class OfflineEventRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: EventDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    private val reminders: ReminderScheduler,
) : EventRepository {
    override fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<List<EventOccurrence>> =
        dao.observeCandidates(from.toEpochDay(), to.toEpochDay()).map { expand(it, from, to) }

    override suspend fun occurrences(from: LocalDate, to: LocalDate): List<EventOccurrence> =
        expand(dao.candidates(from.toEpochDay(), to.toEpochDay()), from, to)

    private fun expand(rows: List<CalendarEventEntity>, from: LocalDate, to: LocalDate): List<EventOccurrence> =
        rows.flatMap { entity ->
            val event = entity.toModel()
            val rule = event.recurrence
            if (rule == null) {
                listOf(EventOccurrence(event, event.date))
            } else {
                RecurrenceEngine.occurrencesBetween(rule, event.date, from, to).map { EventOccurrence(event, it) }
            }
        }.sortedWith(compareBy<EventOccurrence> { it.date }.thenBy { !it.event.allDay }.thenBy { it.event.startTime })

    override fun observeEvent(id: EntityId) = dao.observeEvent(id).map { it?.toModel() }
    override suspend fun getEvent(id: EntityId) = dao.getEvent(id)?.toModel()
    override suspend fun eventsWithReminders() = dao.eventsWithReminders().map { it.toModel() }

    override suspend fun save(event: CalendarEvent): EntityId {
        if (event.title.isBlank()) throw EventValidationException("Event title must not be blank")
        if (!event.allDay && event.startTime != null && event.endTime != null && event.endTime!! < event.startTime!!) {
            throw EventValidationException("Event end must not be before start")
        }
        val now = time.now()
        val id = db.withTransaction {
            val existing = if (event.id != NEW_ID) dao.getEvent(event.id) else null
            val entity = event.copy(createdAt = existing?.createdAt ?: now, updatedAt = now).toEntity()
            val id = if (existing == null) dao.insert(entity.copy(id = 0)) else entity.id.also { dao.update(entity) }
            searchDao.upsert(SearchIndexer.event(entity.copy(id = id)))
            id
        }
        reminders.syncEvent(id)
        return id
    }

    override suspend fun delete(id: EntityId) {
        db.withTransaction {
            searchDao.delete(SearchIndexer.rowId(SearchEntityType.EVENT, id))
            dao.delete(id)
        }
        reminders.cancelEvent(id)
    }
}
