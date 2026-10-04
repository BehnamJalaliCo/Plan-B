package com.behnamjalali.planb.core.model

import java.time.Instant

enum class FocusStatus { RUNNING, PAUSED, COMPLETED, CANCELLED }

/**
 * A focus session. Elapsed time is derived from timestamps, never from frame
 * counts: elapsed = accumulatedMillis + (now - runningSince) while RUNNING.
 */
data class FocusSession(
    val id: EntityId = NEW_ID,
    val linkedTaskId: EntityId? = null,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val plannedDurationMillis: Long,
    val actualDurationMillis: Long = 0,
    val status: FocusStatus = FocusStatus.RUNNING,
    val runningSince: Instant? = startedAt,
    val accumulatedMillis: Long = 0,
    /** Ambient sound played during the session (Focus Pro). */
    val soundId: String? = null,
    /** Strict mode: Do Not Disturb while the session runs (Focus Pro). */
    val strict: Boolean = false,
) {
    fun elapsedMillis(now: Instant): Long {
        val running = if (status == FocusStatus.RUNNING && runningSince != null) {
            (now.toEpochMilli() - runningSince.toEpochMilli()).coerceAtLeast(0)
        } else {
            0
        }
        return when (status) {
            FocusStatus.COMPLETED, FocusStatus.CANCELLED -> actualDurationMillis
            else -> accumulatedMillis + running
        }
    }

    fun remainingMillis(now: Instant): Long = (plannedDurationMillis - elapsedMillis(now)).coerceAtLeast(0)

    val isActive: Boolean get() = status == FocusStatus.RUNNING || status == FocusStatus.PAUSED
}
