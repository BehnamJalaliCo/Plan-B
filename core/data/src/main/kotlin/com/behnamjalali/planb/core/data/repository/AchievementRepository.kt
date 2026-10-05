package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.data.wellbeing.WellbeingState
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.BadgeEntity
import com.behnamjalali.planb.core.database.entity.ChallengeEntity
import com.behnamjalali.planb.core.model.AchievementInput
import com.behnamjalali.planb.core.model.BadgeDefinition
import com.behnamjalali.planb.core.model.BadgeProgress
import com.behnamjalali.planb.core.model.BadgeRules
import com.behnamjalali.planb.core.model.Challenge
import com.behnamjalali.planb.core.model.ChallengeProgress
import com.behnamjalali.planb.core.model.ChallengeRules
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** A badge in the gallery: the user's progress, and when it was awarded (null: not yet). */
data class BadgeStatus(val progress: BadgeProgress, val earnedAt: Instant?) {
    val badge: BadgeDefinition get() = progress.badge
    val earned: Boolean get() = earnedAt != null
}

/** A badge row that was awarded, for the unlock celebration. */
data class AwardedBadge(val id: EntityId, val badge: BadgeDefinition, val earnedAt: Instant)

/**
 * Challenges and badges (Plan-B Pro #29). Progress is always computed from the data
 * ([ChallengeRules], [BadgeRules]); [evaluate] writes the results into `challenges.status` and
 * `badges`, so they are part of backups. Badges are kept once awarded (the first award's date
 * stays), even if the data behind them is deleted later.
 */
interface AchievementRepository {
    /** Every challenge with its progress, newest first. */
    fun observeChallenges(): Flow<List<ChallengeProgress>>

    /** All badges with progress; recomputed whenever a badge is awarded. */
    fun observeBadges(): Flow<List<BadgeStatus>>

    /** Badges awarded recently that were not celebrated yet, oldest first. */
    fun observeUncelebrated(): Flow<List<AwardedBadge>>

    suspend fun markCelebrated(id: EntityId)

    /**
     * Starts a [days]-day challenge on [habitId] today. A habit has at most one active
     * challenge: the existing one's id is returned instead.
     */
    suspend fun startChallenge(habitId: EntityId, days: Int): EntityId

    suspend fun abandon(id: EntityId)

    suspend fun delete(id: EntityId)

    /** Brings challenge statuses up to date and awards new badges; returns the badges awarded now. */
    suspend fun evaluate(): List<BadgeDefinition>
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineAchievementRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val time: TimeProvider,
    private val state: WellbeingState,
) : AchievementRepository {
    private val challenges get() = db.challengeDao()
    private val habits get() = db.habitDao()
    private val wellbeing get() = db.wellbeingDao()

    private val allHabits: Flow<List<Habit>> =
        combine(habits.observeHabits(false), habits.observeHabits(true)) { a, b -> (a + b).map { it.toModel() } }

    private val allAmounts: Flow<Map<EntityId, Map<LocalDate, Int>>> =
        habits.observeCompletions(0, Long.MAX_VALUE).map { rows -> rows.groupBy { it.habitId }.mapValues { (_, list) -> list.associate { it.date to it.amount } } }

    override fun observeChallenges(): Flow<List<ChallengeProgress>> =
        combine(challenges.observeAll(), allHabits, allAmounts) { list, habitList, amounts ->
            val today = time.today()
            val byId = habitList.associateBy { it.id }
            list.map { entity ->
                val challenge = entity.toModel()
                ChallengeRules.evaluate(challenge, challenge.habitId?.let(byId::get), challenge.habitId?.let(amounts::get).orEmpty(), today)
            }
        }

    override fun observeBadges(): Flow<List<BadgeStatus>> = challenges.observeBadges().map { rows ->
        val earned = rows.associate { it.key to it.earnedAt }
        BadgeRules.evaluate(input()).map { BadgeStatus(it, earned[it.badge.key]) }
    }

    override fun observeUncelebrated(): Flow<List<AwardedBadge>> = state.celebratedBadgeId.filterNotNull().flatMapLatest { after ->
        wellbeing.observeBadgesAfter(after).map { rows ->
            val recent = time.now().minus(CELEBRATE_WITHIN)
            rows.filter { it.earnedAt >= recent }.mapNotNull { row -> BadgeDefinition.fromKey(row.key)?.let { AwardedBadge(row.id, it, row.earnedAt) } }
        }
    }

    override suspend fun markCelebrated(id: EntityId) = state.setCelebratedBadgeId(id)

    override suspend fun startChallenge(habitId: EntityId, days: Int): EntityId {
        require(days in 1..MAX_DAYS) { "A challenge lasts 1..$MAX_DAYS days" }
        val now = time.now()
        return db.withTransaction {
            val habit = habits.getHabit(habitId) ?: throw IllegalArgumentException("No habit $habitId")
            wellbeing.challenges().firstOrNull { it.habitId == habitId && it.status == ChallengeStatus.ACTIVE.name }?.let { return@withTransaction it.id }
            challenges.insert(
                ChallengeEntity(
                    kind = ChallengeRules.KIND_HABIT,
                    title = habit.title,
                    targetDays = days,
                    startDate = time.today(),
                    habitId = habitId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun abandon(id: EntityId) {
        val entity = challenges.get(id) ?: return
        if (entity.status == ChallengeStatus.ACTIVE.name || entity.status == ChallengeStatus.FAILED.name) {
            challenges.update(entity.copy(status = ChallengeStatus.ABANDONED.name, updatedAt = time.now()))
        }
    }

    override suspend fun delete(id: EntityId) = challenges.delete(id)

    override suspend fun evaluate(): List<BadgeDefinition> {
        val now = time.now()
        val zone = time.zone()
        val today = time.today()
        if (state.celebratedBadgeId.first() == null) state.setCelebratedBadgeId(wellbeing.maxBadgeId())
        return db.withTransaction {
            val habitList = (habits.activeHabits() + archivedHabits()).map { it.toModel() }.associateBy { it.id }
            val amounts = amountsByHabit()
            wellbeing.challenges().forEach { entity ->
                val stored = ChallengeStatus.fromKey(entity.status)
                if (stored == ChallengeStatus.ABANDONED) return@forEach
                val progress = ChallengeRules.evaluate(entity.toModel(), entity.habitId?.let(habitList::get), entity.habitId?.let(amounts::get).orEmpty(), today)
                val completedAt = progress.completedOn?.atStartOfDay(zone)?.toInstant()
                if (progress.status != stored || completedAt != entity.completedAt) {
                    wellbeing.setChallengeStatus(entity.id, progress.status.name, completedAt?.toEpochMilli(), now.toEpochMilli())
                }
            }
            val owned = wellbeing.badges().map { it.key }.toSet()
            BadgeRules.evaluate(input()).filter { it.earned && it.badge.key !in owned }.mapNotNull { progress ->
                val earnedAt = progress.earnedOn!!.atStartOfDay(zone).toInstant()
                progress.badge.takeIf { challenges.award(BadgeEntity(key = it.key, earnedAt = earnedAt)) > 0 }
            }
        }
    }

    private suspend fun archivedHabits() = habits.observeHabits(true).first()

    private suspend fun amountsByHabit(): Map<EntityId, Map<LocalDate, Int>> =
        habits.completions(0, Long.MAX_VALUE).groupBy { it.habitId }.mapValues { (_, list) -> list.associate { it.date to it.amount } }

    /** Everything the badge rules need, read in one go. */
    private suspend fun input(): AchievementInput {
        val zone = time.zone()
        val today = time.today()
        val amounts = amountsByHabit()
        val habitList = (habits.activeHabits() + archivedHabits()).map { it.toModel() }
        val challengeList = wellbeing.challenges().map { it.toModel() }
        val byId = habitList.associateBy { it.id }
        val completed = challengeList.mapNotNull { c ->
            ChallengeRules.evaluate(c, c.habitId?.let(byId::get), c.habitId?.let(amounts::get).orEmpty(), today)
                .completedOn?.let { c.targetDays to it }
        }
        return AchievementInput(
            today = today,
            habits = habitList.map { it to amounts[it.id].orEmpty() },
            focusSessions = wellbeing.completedFocusSessions().map { it.startedAt.atZone(zone).toLocalDate() to it.actualDurationMillis },
            taskCompletions = wellbeing.taskCompletionTimes().map { it.atZone(zone).toLocalDate() },
            journalDates = wellbeing.journalDays().map(LocalDate::ofEpochDay).toSet(),
            moodDates = wellbeing.moodDays().map(LocalDate::ofEpochDay),
            completedChallenges = completed,
        )
    }

    companion object {
        const val MAX_DAYS = 366

        /** Badges earned longer ago (for example on the first evaluation of old data) are not celebrated. */
        val CELEBRATE_WITHIN: Duration = Duration.ofDays(2)
    }
}

internal fun ChallengeEntity.toModel() = Challenge(
    id = id,
    kind = kind,
    title = title,
    targetDays = targetDays,
    startDate = startDate,
    habitId = habitId,
    status = ChallengeStatus.fromKey(status),
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
