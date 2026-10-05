package com.behnamjalali.planb.feature.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.MoodRepository
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.data.wellbeing.HealthHabitSync
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.MoodEntry
import com.behnamjalali.planb.core.model.MoodInsights
import com.behnamjalali.planb.core.model.MoodTrackerInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * The mood and energy tracker (Plan-B Pro #30). [checkIn] opens the check-in sheet right away
 * (from Today or Habits), with [mood] (1..5) already chosen when given.
 */
@Serializable
data class MoodTrackerRoute(val checkIn: Boolean = false, val mood: Int = 0)

/** The check-in sheet: a new check-in ([editingId] null) or a change to an existing one. */
@Serializable
data class MoodDraft(val editingId: Long? = null, val mood: Int? = null, val energy: Int? = null, val tags: String = "")

data class MoodTrackerUi(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    /** Check-ins of the last [MoodTrackerViewModel.HISTORY_DAYS] days, newest first. */
    val history: List<MoodEntry> = emptyList(),
    val insights: MoodTrackerInsights? = null,
    /** Health Connect for comparing with sleep: available, and whether sleep may be read. */
    val healthAvailability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val sleepAccess: Boolean = false,
    val draft: MoodDraft? = null,
) {
    val todayEntries: List<MoodEntry> get() = history.filter { it.date == today }
}

sealed interface MoodEvent {
    data object Saved : MoodEvent
    data class Deleted(val entry: MoodEntry) : MoodEvent
    data object Failed : MoodEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoodTrackerViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val moods: MoodRepository,
    habits: HabitRepository,
    private val health: HealthHabitSync,
    time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<MoodTrackerRoute>() }.getOrDefault(MoodTrackerRoute())

    init {
        // Opened for a check-in (Today, Habits): show the sheet once, not again after rotation.
        if (route.checkIn && !savedState.contains(KEY_CONSUMED)) {
            savedState[KEY_CONSUMED] = true
            savedState[KEY_DRAFT] = encode(MoodDraft(mood = route.mood.takeIf { it in MoodEntry.RANGE }))
        }
    }

    private val draft = savedState.getStateFlow<String?>(KEY_DRAFT, null).map { it?.let(::decode) }
    private val healthRefresh = MutableStateFlow(0)
    private val today = time.todayFlow()

    private val window = today.flatMapLatest { day ->
        val from = day.minusDays(MoodInsights.WINDOW_DAYS - 1)
        combine(
            moods.observeEntries(from, day),
            moods.observeFocusMinutes(from, day),
            habits.observeHabits(from, day).map { list -> list.map { it.habit to it.amounts } },
        ) { entries, focus, habitList -> Window(day, entries, focus, habitList) }
    }

    /** Sleep is read only for days with a check-in (at most a month), and only with access. */
    private val sleep = combine(window.map { w -> w.entries.map { it.date }.toSet() }.distinctUntilChanged(), healthRefresh) { days, _ -> days }
        .flatMapLatest { days ->
            flow {
                val access = runCatchingSafely { health.hasPermission(HealthMetric.SLEEP_MINUTES) }.getOrDefault(false)
                val totals = if (access) runCatchingSafely { health.dailyTotals(HealthMetric.SLEEP_MINUTES, days) }.getOrDefault(emptyMap()) else emptyMap()
                emit(access to totals)
            }
        }

    val state: StateFlow<MoodTrackerUi> = combine(window, sleep, draft) { w, (access, sleepMinutes), sheet ->
        MoodTrackerUi(
            loading = false,
            today = w.today,
            history = w.entries.filter { it.date > w.today.minusDays(HISTORY_DAYS) },
            insights = MoodInsights.analyze(w.entries, w.today, w.habits, w.focus, sleepMinutes),
            healthAvailability = health.availability(),
            sleepAccess = access,
            draft = sheet,
        )
    }.catch { emit(MoodTrackerUi(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoodTrackerUi())

    private val _events = Channel<MoodEvent>(Channel.BUFFERED)
    val events: Flow<MoodEvent> = _events.receiveAsFlow()

    fun openCheckIn(mood: Int? = null) {
        savedState[KEY_DRAFT] = encode(MoodDraft(mood = mood))
    }

    fun edit(entry: MoodEntry) {
        savedState[KEY_DRAFT] = encode(MoodDraft(editingId = entry.id, mood = entry.mood, energy = entry.energy, tags = entry.tags.joinToString(", ")))
    }

    fun updateDraft(transform: (MoodDraft) -> MoodDraft) {
        val current = savedState.get<String>(KEY_DRAFT)?.let(::decode) ?: return
        savedState[KEY_DRAFT] = encode(transform(current))
    }

    fun dismissDraft() {
        savedState[KEY_DRAFT] = null
    }

    /** Saves the sheet: a new check-in or the edited one. */
    fun saveDraft() {
        val current = savedState.get<String>(KEY_DRAFT)?.let(::decode) ?: return
        if (current.mood == null && current.energy == null) return
        val tags = current.tags.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
        launch {
            val id = current.editingId
            if (id == null) moods.checkIn(current.mood, current.energy, tags) else moods.update(id, current.mood, current.energy, tags)
            savedState[KEY_DRAFT] = null
            _events.trySend(MoodEvent.Saved)
        }
    }

    fun delete(entry: MoodEntry) = launch {
        moods.delete(entry.id)
        _events.trySend(MoodEvent.Deleted(entry))
    }

    /** Puts a deleted check-in back (snackbar "Undo"); it gets a new id and the current time is kept from the original. */
    fun undoDelete(entry: MoodEntry) = launch { moods.restore(entry) }

    /** The Health Connect permission for sleep (asked in context from the correlations card). */
    fun sleepPermissions(): Set<String> = health.permissionsFor(HealthMetric.SLEEP_MINUTES)

    fun refreshHealth() {
        healthRefresh.value++
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.trySend(MoodEvent.Failed) }
    }

    private class Window(
        val today: LocalDate,
        val entries: List<MoodEntry>,
        val focus: Map<LocalDate, Int>,
        val habits: List<Pair<com.behnamjalali.planb.core.model.Habit, Map<LocalDate, Int>>>,
    )

    private fun encode(draft: MoodDraft): String = json.encodeToString(MoodDraft.serializer(), draft)
    private fun decode(text: String): MoodDraft? = runCatching { json.decodeFromString(MoodDraft.serializer(), text) }.getOrNull()

    companion object {
        const val HISTORY_DAYS = 30L
        private const val KEY_DRAFT = "mood_draft"
        private const val KEY_CONSUMED = "mood_route_consumed"
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
