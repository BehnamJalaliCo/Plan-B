package com.behnamjalali.planb.core.data.platform

import com.behnamjalali.planb.core.database.PlanBDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

/**
 * Emits after writes to the tables that home-screen widgets and the watch show (tasks,
 * habits, focus sessions, events). Writes are observed through Room's invalidation tracker,
 * so every repository (and a restore) triggers it without knowing about widgets. Bursts of
 * writes are coalesced.
 */
@Singleton
class DataChangeWatcher @Inject constructor(private val db: PlanBDatabase) {
    @OptIn(FlowPreview::class)
    val changes: Flow<Unit> = db.invalidationTracker
        .createFlow(*TABLES, emitInitialState = false)
        .debounce(DEBOUNCE_MS)
        .map { }

    /**
     * Emits after writes to the tables Plan-B Pro badges and challenges (#29) are computed from,
     * coalesced over [ACHIEVEMENT_DEBOUNCE_MS] so a burst of check-ins evaluates once.
     */
    @OptIn(FlowPreview::class)
    val achievementChanges: Flow<Unit> = db.invalidationTracker
        .createFlow(*ACHIEVEMENT_TABLES, emitInitialState = false)
        .debounce(ACHIEVEMENT_DEBOUNCE_MS)
        .map { }

    companion object {
        val ACHIEVEMENT_TABLES = arrayOf("tasks", "habits", "habit_completions", "focus_sessions", "journal_entries", "notes", "mood_entries", "challenges")
        const val ACHIEVEMENT_DEBOUNCE_MS = 1_500L
        val TABLES = arrayOf("tasks", "habits", "habit_completions", "focus_sessions", "calendar_events")
        const val DEBOUNCE_MS = 400L
    }
}
