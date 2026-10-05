package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.toEntity
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.dao.FocusDao
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Side effects of a focus session outside the database (Plan-B Pro #26: the ambient sound and
 * strict mode's Do Not Disturb). The repository calls it after every change of the active
 * session, whoever made it (the screen, a tile, the widget, the end alarm), with the session as
 * it now is (null when none is active); the platform implementation also runs it at app start,
 * so a change interrupted by process death is caught up.
 */
fun interface FocusSessionEffects {
    suspend fun onSessionChanged(active: FocusSession?)
}

/**
 * Focus timer state machine persisted in Room. All transitions are computed
 * from wall-clock timestamps stored with the session, so a session survives
 * process death and its elapsed time never depends on UI frame timing.
 */
interface FocusRepository {
    fun observeActive(): Flow<FocusSession?>
    fun observeHistory(limit: Int = 50): Flow<List<FocusSession>>
    fun observeFocusedMillis(from: Instant, to: Instant): Flow<Long>

    /** The running or paused session, if any. */
    suspend fun getActive(): FocusSession?
    /** Starts a session; [soundId] and [strict] are Focus Pro options (#26) stored with it. */
    suspend fun start(plannedMillis: Long, linkedTaskId: EntityId?, soundId: String? = null, strict: Boolean = false): FocusSession
    suspend fun pause(): FocusSession?
    suspend fun resume(): FocusSession?
    suspend fun finish(): FocusSession?
    suspend fun cancel(): FocusSession?

    /** Completes the active session if its planned time has fully elapsed. */
    suspend fun completeIfElapsed(): FocusSession?

    /** Changes the active session's ambient sound (null = silence). */
    suspend fun setSound(soundId: String?): FocusSession?

    /** Turns strict mode of the active session on or off. */
    suspend fun setStrict(strict: Boolean): FocusSession?
}

@Singleton
class OfflineFocusRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: FocusDao,
    private val time: TimeProvider,
    /** Sound and Do Not Disturb (Plan-B Pro #26); null in tests that don't need them. */
    private val effects: FocusSessionEffects? = null,
) : FocusRepository {
    override fun observeActive() = dao.observeActive().map { it?.toModel() }
    override fun observeHistory(limit: Int) = dao.observeHistory(limit).map { l -> l.map { it.toModel() } }
    override fun observeFocusedMillis(from: Instant, to: Instant) =
        dao.observeFocusedMillis(from.toEpochMilli(), to.toEpochMilli())

    override suspend fun getActive(): FocusSession? = dao.getActive()?.toModel()

    override suspend fun start(plannedMillis: Long, linkedTaskId: EntityId?, soundId: String?, strict: Boolean): FocusSession {
        require(plannedMillis > 0) { "Planned duration must be positive" }
        val now = time.now()
        val started = db.withTransaction {
            // Only one active session: an unfinished previous one is cancelled, keeping its elapsed time.
            dao.getActive()?.toModel()?.let { dao.update(it.end(FocusStatus.CANCELLED, now).toEntity()) }
            val session = FocusSession(
                linkedTaskId = linkedTaskId,
                startedAt = now,
                plannedDurationMillis = plannedMillis,
                status = FocusStatus.RUNNING,
                runningSince = now,
                soundId = soundId,
                strict = strict,
            )
            session.copy(id = dao.insert(session.toEntity()))
        }
        notify(started)
        return started
    }

    override suspend fun pause(): FocusSession? = transition { s, now ->
        if (s.status != FocusStatus.RUNNING) s else s.copy(
            status = FocusStatus.PAUSED,
            accumulatedMillis = s.elapsedMillis(now),
            runningSince = null,
        )
    }

    override suspend fun resume(): FocusSession? = transition { s, now ->
        if (s.status != FocusStatus.PAUSED) s else s.copy(status = FocusStatus.RUNNING, runningSince = now)
    }

    override suspend fun finish(): FocusSession? = transition { s, now -> s.end(FocusStatus.COMPLETED, now) }

    override suspend fun cancel(): FocusSession? = transition { s, now -> s.end(FocusStatus.CANCELLED, now) }

    override suspend fun completeIfElapsed(): FocusSession? = transition { s, now ->
        if (s.status == FocusStatus.RUNNING && s.remainingMillis(now) == 0L) s.end(FocusStatus.COMPLETED, now) else s
    }

    override suspend fun setSound(soundId: String?): FocusSession? = transition { s, _ -> s.copy(soundId = soundId) }

    override suspend fun setStrict(strict: Boolean): FocusSession? = transition { s, _ -> s.copy(strict = strict) }

    private suspend fun transition(block: (FocusSession, Instant) -> FocusSession): FocusSession? {
        var changed = false
        val result = db.withTransaction {
            val active = dao.getActive()?.toModel() ?: return@withTransaction null
            val updated = block(active, time.now())
            if (updated != active) {
                dao.update(updated.toEntity())
                changed = true
            }
            updated
        }
        if (changed) notify(result)
        return result
    }

    /** Effects never fail a session change: the session in the database is what counts. */
    private suspend fun notify(session: FocusSession?) {
        val hook = effects ?: return
        runCatchingSafely { hook.onSessionChanged(session?.takeIf { it.isActive }) }
    }

    private fun FocusSession.end(status: FocusStatus, now: Instant): FocusSession {
        val elapsed = elapsedMillis(now).coerceAtMost(plannedDurationMillis)
        return copy(
            status = status,
            endedAt = now,
            actualDurationMillis = elapsed,
            accumulatedMillis = elapsed,
            runningSince = null,
        )
    }
}

/**
 * Adds a completed session's whole minutes to its linked task's actual time. Used by every
 * path that completes a session (the screen and the background end alarm).
 */
suspend fun TaskRepository.recordFocusSession(session: FocusSession) {
    if (session.status != FocusStatus.COMPLETED) return
    val taskId = session.linkedTaskId ?: return
    addActualMinutes(taskId, (session.actualDurationMillis / 60_000L).toInt())
}
