package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.MoodEntryEntity
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.MoodEntry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The mood and energy tracker (Plan-B Pro #30) over `mood_entries`. Its own check-ins have no
 * note (`note_id` null), several a day; the journal's one check-in per page (#25) lives in the
 * same table with its page's `note_id` and is shown here too, so editing or deleting it here
 * changes the journal page's mood as well.
 */
interface MoodRepository {
    /** Check-ins in [from, to], newest first. */
    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<MoodEntry>>

    /** Focused minutes per day (completed sessions, by their local start day) in [from, to]. */
    fun observeFocusMinutes(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    suspend fun get(id: EntityId): MoodEntry?

    /** A new check-in now (mood and energy 1..5, at least one). */
    suspend fun checkIn(mood: Int?, energy: Int?, tags: List<String> = emptyList()): EntityId

    /** Changes a check-in's mood, energy and tags; both levels null deletes it. */
    suspend fun update(id: EntityId, mood: Int?, energy: Int?, tags: List<String>)

    suspend fun delete(id: EntityId)
}

@Singleton
class OfflineMoodRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val time: TimeProvider,
) : MoodRepository {
    private val journal get() = db.journalDao()
    private val wellbeing get() = db.wellbeingDao()

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<MoodEntry>> =
        wellbeing.observeMoods(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toModel() } }

    override fun observeFocusMinutes(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> {
        val zone = time.zone()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return wellbeing.observeFocusSessions(start, end).map { rows ->
            rows.groupBy { it.startedAt.atZone(zone).toLocalDate() }.mapValues { (_, list) -> (list.sumOf { it.actualDurationMillis } / 60_000L).toInt() }
        }
    }

    override suspend fun get(id: EntityId): MoodEntry? = journal.getMood(id)?.toModel()

    override suspend fun checkIn(mood: Int?, energy: Int?, tags: List<String>): EntityId {
        validate(mood, energy)
        require(mood != null || energy != null) { "A check-in needs a mood or an energy level" }
        val now = time.now()
        val local = now.atZone(time.zone())
        return journal.insertMood(
            MoodEntryEntity(
                date = local.toLocalDate(),
                time = local.toLocalTime().withSecond(0).withNano(0),
                mood = mood,
                energy = energy,
                tags = cleanTags(tags),
                noteId = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: EntityId, mood: Int?, energy: Int?, tags: List<String>) {
        validate(mood, energy)
        val existing = journal.getMood(id) ?: return
        if (mood == null && energy == null) {
            journal.deleteMood(id)
        } else {
            journal.updateMood(existing.copy(mood = mood, energy = energy, tags = cleanTags(tags), updatedAt = time.now()))
        }
    }

    override suspend fun delete(id: EntityId) = journal.deleteMood(id)

    private fun validate(mood: Int?, energy: Int?) {
        require(mood == null || mood in MoodEntry.RANGE) { "Mood must be 1..5" }
        require(energy == null || energy in MoodEntry.RANGE) { "Energy must be 1..5" }
    }

    /** Tags are stored comma-separated: commas inside a tag become spaces. */
    private fun cleanTags(tags: List<String>): String =
        tags.map { it.replace(',', ' ').replace('،', ' ').trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().take(MAX_TAGS).joinToString(",")

    private companion object {
        const val MAX_TAGS = 10
    }
}
