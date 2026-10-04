package com.behnamjalali.planb.core.data.repository

import androidx.room.withTransaction
import com.behnamjalali.planb.core.common.TimeProvider
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
 * Focus timer state machine persisted in Room. All transitions are computed
 * from wall-clock timestamps stored with the session, so a session survives
 * process death and its elapsed time never depends on UI frame timing.
 */
interface FocusRepository {
    fun observeActive(): Flow<FocusSession?>
    fun observeHistory(limit: Int = 50): Flow<List<FocusSession>>
    fun observeFocusedMillis(from: Instant, to: Instant): Flow<Long>
    suspend fun start(plannedMillis: Long, linkedTaskId: EntityId?): FocusSession
    suspend fun pause(): FocusSession?
    suspend fun resume(): FocusSession?
    suspend fun finish(): FocusSession?
    suspend fun cancel(): FocusSession?

    /** Completes the active session if its planned time has fully elapsed. */
    suspend fun completeIfElapsed(): FocusSession?
}

@Singleton
class OfflineFocusRepository @Inject constructor(
    private val db: PlanBDatabase,
    private val dao: FocusDao,
    private val time: TimeProvider,
) : FocusRepository {
    override fun observeActive() = dao.observeActive().map { it?.toModel() }
    override fun observeHistory(limit: Int) = dao.observeHistory(limit).map { l -> l.map { it.toModel() } }
    override fun observeFocusedMillis(from: Instant, to: Instant) =
        dao.observeFocusedMillis(from.toEpochMilli(), to.toEpochMilli())

    override suspend fun start(plannedMillis: Long, linkedTaskId: EntityId?): FocusSession {
        require(plannedMillis > 0) { "Planned duration must be positive" }
        val now = time.now()
        return db.withTransaction {
            // Only one active session: an unfinished previous one is cancelled, keeping its elapsed time.
            dao.getActive()?.toModel()?.let { dao.update(it.end(FocusStatus.CANCELLED, now).toEntity()) }
            val session = FocusSession(
                linkedTaskId = linkedTaskId,
                startedAt = now,
                plannedDurationMillis = plannedMillis,
                status = FocusStatus.RUNNING,
                runningSince = now,
            )
            session.copy(id = dao.insert(session.toEntity()))
        }
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

    private suspend fun transition(block: (FocusSession, Instant) -> FocusSession): FocusSession? =
        db.withTransaction {
            val active = dao.getActive()?.toModel() ?: return@withTransaction null
            val updated = block(active, time.now())
            if (updated != active) dao.update(updated.toEntity())
            updated
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
