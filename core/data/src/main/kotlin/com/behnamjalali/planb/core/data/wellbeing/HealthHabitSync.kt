package com.behnamjalali.planb.core.data.wellbeing

import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.ProStatusSource
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.dao.HabitDao
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HealthHabits
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.HealthReading
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether Health Connect can be used on this device. */
enum class HealthAvailability {
    AVAILABLE,

    /** Health Connect must be installed or updated (Android 9–13). */
    NEEDS_INSTALL,

    /** Not supported on this device (Android 8 and older, or no provider at all). */
    UNSUPPORTED,
}

/**
 * Read-only access to Health Connect (Plan-B Pro #27), implemented with the Health Connect
 * client in `core:health`; tests use a fake. Only daily totals are read, never single records.
 */
interface HealthDataSource {
    fun availability(): HealthAvailability

    /** The Health Connect read permissions [metric] needs (asked for in context, one metric at a time). */
    fun permissionsFor(metric: HealthMetric): Set<String>

    /** The permissions the user granted; empty when Health Connect is unavailable. */
    suspend fun grantedPermissions(): Set<String>

    /** The total of [metric] in [from, to) in its base unit; null when it cannot be read. */
    suspend fun total(metric: HealthMetric, from: Instant, to: Instant): Long?

    companion object {
        /** No Health Connect at all (the default where none is bound). */
        val Unavailable = object : HealthDataSource {
            override fun availability() = HealthAvailability.UNSUPPORTED
            override fun permissionsFor(metric: HealthMetric): Set<String> = emptySet()
            override suspend fun grantedPermissions(): Set<String> = emptySet()
            override suspend fun total(metric: HealthMetric, from: Instant, to: Instant): Long? = null
        }
    }
}

/**
 * Checks habits off from Health Connect (Plan-B Pro #27): for each habit linked to a metric
 * whose permission is granted, the scheduled days of the last [HealthHabits.LOOKBACK_DAYS] days
 * are read and a day that reached the threshold gets enough check-ins to meet the target (see
 * [HealthHabits.delta]). Each day is checked off at most once ([WellbeingState]), so a day the
 * user unchecks stays unchecked. Runs only for Pro users, while the app is in use (Health Connect
 * reads in the background would need another permission): when the app comes to the
 * foreground and when the habits screen opens, at most every [MIN_INTERVAL] unless forced.
 */
@Singleton
class HealthHabitSync @Inject constructor(
    private val source: HealthDataSource,
    private val habitDao: HabitDao,
    private val habits: HabitRepository,
    private val state: WellbeingState,
    private val time: TimeProvider,
    private val pro: ProStatusSource,
) {
    private val mutex = Mutex()

    fun availability(): HealthAvailability = source.availability()

    fun permissionsFor(metric: HealthMetric): Set<String> = source.permissionsFor(metric)

    suspend fun hasPermission(metric: HealthMetric): Boolean =
        source.availability() == HealthAvailability.AVAILABLE && source.permissionsFor(metric).let { it.isNotEmpty() && source.grantedPermissions().containsAll(it) }

    /** Today's amount of [habit]'s metric, for the habit screen; null without access. */
    suspend fun today(habit: Habit): HealthReading? {
        val metric = habit.healthMetric ?: return null
        if (!hasPermission(metric)) return null
        val today = time.today()
        val (from, to) = HealthHabits.window(metric, today, time.zone())
        return runCatchingSafely { source.total(metric, from, to) }.getOrNull()?.let { HealthReading(metric, today, it) }
    }

    /** Runs a sync unless one ran within [MIN_INTERVAL] ([force] skips that); returns the days checked off. */
    suspend fun sync(force: Boolean = false): Int = mutex.withLock {
        if (!runCatchingSafely { pro.isPro() }.getOrDefault(false)) return 0
        if (source.availability() != HealthAvailability.AVAILABLE) return 0
        val now = time.now()
        val last = state.lastHealthSync.first()
        if (!force && last in (now.toEpochMilli() - MIN_INTERVAL.toMillis())..now.toEpochMilli()) return 0
        val linked = habitDao.habitsWithHealthMetric().map { it.toModel() }.filter { it.healthMetric != null && (it.healthThreshold ?: 0) > 0 }
        if (linked.isEmpty()) return 0
        val granted = runCatchingSafely { source.grantedPermissions() }.getOrDefault(emptySet())
        val today = time.today()
        var checked = 0
        linked.forEach { habit ->
            val metric = habit.healthMetric!!
            val needed = source.permissionsFor(metric)
            if (needed.isEmpty() || !granted.containsAll(needed)) return@forEach
            val done = state.healthChecked(habit.id)
            HealthHabits.daysToCheck(habit, today).filter { it !in done }.forEach { day ->
                if (syncDay(habit, metric, day, today)) checked++
            }
        }
        state.setLastHealthSync(now.toEpochMilli())
        checked
    }

    private suspend fun syncDay(habit: Habit, metric: HealthMetric, day: LocalDate, today: LocalDate): Boolean {
        val (from, to) = HealthHabits.window(metric, day, time.zone())
        val value = runCatchingSafely { source.total(metric, from, to) }.getOrNull() ?: return false
        if (value < (habit.healthThreshold ?: Long.MAX_VALUE)) return false
        val delta = HealthHabits.delta(habit, value, habits.amountOn(habit.id, day), alreadyChecked = false)
        if (delta > 0) habits.checkIn(habit.id, day, delta)
        // Reached (also when it was already checked by hand): never checked off again for this day.
        state.addHealthChecked(habit.id, day, keepFrom = today.minusDays(KEEP_DAYS))
        return delta > 0
    }

    companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(15)
        private const val KEEP_DAYS = 14L
    }
}
