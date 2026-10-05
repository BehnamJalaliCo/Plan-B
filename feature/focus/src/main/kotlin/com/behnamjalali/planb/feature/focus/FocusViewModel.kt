package com.behnamjalali.planb.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.data.repository.recordFocusSession
import com.behnamjalali.planb.core.focus.FocusProControls
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusCycle
import com.behnamjalali.planb.core.model.FocusProSettings
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

@Serializable data object FocusRoute

data class FocusUiState(
    val loading: Boolean = true,
    val active: FocusSession? = null,
    val history: List<FocusSession> = emptyList(),
    val focusedTodayMinutes: Int = 0,
    val pomodoroMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val selectedMinutes: Int = 25,
    val linkedTaskId: EntityId? = null,
    val tasks: List<Task> = emptyList(),
    /** Focus Pro preferences (#26): default sound, volume, strict mode, daily goal, long breaks. */
    val focusPro: FocusProSettings = FocusProSettings(),
    /** Sessions completed today (for the long-break cycle). */
    val completedToday: Int = 0,
    /** Whether Plan-B may change Do Not Disturb (strict mode). */
    val dndAccess: Boolean = false,
) {
    val linkedTask: Task? get() = tasks.firstOrNull { it.id == (active?.linkedTaskId ?: linkedTaskId) }

    /** The sound shown as chosen: the running session's own, otherwise the default for new ones. */
    val sound: AmbientSound? get() = if (active != null) AmbientSound.fromId(active.soundId) else focusPro.sound

    /** Strict mode as shown: the session's own while one is active. */
    val strict: Boolean get() = active?.strict ?: focusPro.strict

    /** The break after the latest completed session, with long breaks every few sessions. */
    val cycle: FocusCycle get() = FocusCycle.after(completedToday, focusPro.longBreakEvery, breakMinutes, focusPro.longBreakMinutes)
}

enum class FocusMessage { Completed, Failed }

/**
 * Focus timer. The session's logical time is computed from stored timestamps
 * (FocusSession.elapsedMillis); the UI only re-reads it on each frame/tick, so
 * dropped frames or backgrounding never change the result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val focus: FocusRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val time: TimeProvider,
    /** Focus Pro's sound and Do Not Disturb (#26). */
    private val controls: FocusProControls,
) : ViewModel() {
    private val selected = savedState.getStateFlow<Int?>(KEY_MINUTES, null)
    private val linked = savedState.getStateFlow<Long?>(KEY_TASK, null)

    // A channel keeps an event sent while the screen is not collecting (e.g. mid-rotation).
    private val _messages = Channel<FocusMessage>(Channel.BUFFERED)
    val messages: Flow<FocusMessage> = _messages.receiveAsFlow()

    private val dndAccess = MutableStateFlow(controls.hasDndAccess())

    /** Held while a start is being written, so a double tap cannot start two sessions. */
    private val starting = Mutex()

    /** The current day; moves on at midnight so "focused today" starts again from zero. */
    private val today = time.todayFlow().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val focusedToday = today.flatMapLatest { day ->
        val zone = time.zone()
        focus.observeFocusedMillis(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
    }

    val uiState: StateFlow<FocusUiState> = combine(
        listOf(
            focus.observeActive(), focus.observeHistory(30), focusedToday, settings.settings, selected, linked,
            today.flatMapLatest { day -> tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = day, limit = 50)) },
            today, dndAccess,
        ),
    ) { v ->
        val s = v[3] as com.behnamjalali.planb.core.model.UserSettings
        val day = v[7] as java.time.LocalDate
        val zone = time.zone()
        @Suppress("UNCHECKED_CAST")
        val history = v[1] as List<FocusSession>
        @Suppress("UNCHECKED_CAST")
        FocusUiState(
            loading = false,
            active = v[0] as FocusSession?,
            history = v[1] as List<FocusSession>,
            focusedTodayMinutes = ((v[2] as Long) / 60_000L).toInt(),
            pomodoroMinutes = s.focusMinutes,
            breakMinutes = s.shortBreakMinutes,
            selectedMinutes = (v[4] as Int?) ?: s.focusMinutes,
            linkedTaskId = v[5] as Long?,
            tasks = v[6] as List<Task>,
            focusPro = s.focusPro,
            completedToday = history.count { it.status == FocusStatus.COMPLETED && it.startedAt.atZone(zone).toLocalDate() == day },
            dndAccess = v[8] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun now(): Instant = time.now()

    fun selectMinutes(minutes: Int) { savedState[KEY_MINUTES] = minutes.coerceIn(1, 180) }
    fun linkTask(id: EntityId?) { savedState[KEY_TASK] = id }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _messages.trySend(FocusMessage.Failed) }
    }

    /**
     * Ignored while another start is in progress or a session is already running or paused.
     * With Plan-B Pro ([pro]) the session gets the default sound and strict mode (#26).
     */
    fun start(pro: Boolean = false) = launchSafely {
        if (!starting.tryLock()) return@launchSafely
        try {
            if (focus.getActive() != null) return@launchSafely
            val minutes = uiState.value.selectedMinutes
            val options = settings.current().focusPro
            controls.preview(null, 0)
            val session = focus.start(
                minutes * 60_000L,
                uiState.value.linkedTaskId,
                soundId = options.sound?.id?.takeIf { pro },
                strict = pro && options.strict,
            )
            reminders.scheduleFocusEnd(session.startedAt.plusMillis(session.plannedDurationMillis))
        } finally {
            starting.unlock()
        }
    }

    fun pause() = launchSafely {
        focus.pause()
        reminders.cancelFocusEnd()
    }

    fun resume() = launchSafely {
        val session = focus.resume() ?: return@launchSafely
        reminders.scheduleFocusEnd(time.now().plusMillis(session.remainingMillis(time.now())))
    }

    fun finish() = launchSafely {
        val done = focus.finish() ?: return@launchSafely
        reminders.cancelFocusEnd()
        tasks.recordFocusSession(done)
    }

    fun cancel() = launchSafely {
        focus.cancel()
        reminders.cancelFocusEnd()
    }

    /** Called by the UI when the countdown reaches zero while visible. */
    fun onElapsed() = launchSafely {
        val done = focus.completeIfElapsed() ?: return@launchSafely
        if (done.status == FocusStatus.COMPLETED) {
            reminders.cancelFocusEnd()
            tasks.recordFocusSession(done)
            _messages.trySend(FocusMessage.Completed)
        }
    }

    // region Focus Pro (#26); the screen only offers these to Pro users.
    /** The default sound for new sessions, and the running session's sound. Previews it when idle. */
    fun setSound(sound: AmbientSound?) = launchSafely {
        settings.update { it.copy(focusPro = it.focusPro.copy(sound = sound)) }
        val active = focus.getActive()
        if (active != null) {
            focus.setSound(sound?.id)
        } else {
            controls.preview(sound, settings.current().focusPro.volume)
        }
    }

    fun setVolume(volume: Int) = launchSafely {
        settings.update { it.copy(focusPro = it.focusPro.copy(volume = volume.coerceIn(0, 100))) }
    }

    /** Strict mode for new sessions and the running one; Do Not Disturb follows when access is granted. */
    fun setStrict(strict: Boolean) = launchSafely {
        settings.update { it.copy(focusPro = it.focusPro.copy(strict = strict)) }
        focus.getActive()?.let { focus.setStrict(strict) }
    }

    fun setDailyGoal(minutes: Int) = launchSafely {
        settings.update { it.copy(focusPro = it.focusPro.copy(dailyGoalMinutes = minutes.coerceIn(0, FocusProSettings.MAX_DAILY_GOAL))) }
    }

    fun setLongBreak(every: Int, minutes: Int) = launchSafely {
        settings.update { it.copy(focusPro = it.focusPro.copy(longBreakEvery = every.coerceIn(1, 12), longBreakMinutes = minutes.coerceIn(1, 90))) }
    }

    /** The screen came back (maybe from the Do Not Disturb settings): re-check access and apply it. */
    fun onResume() = launchSafely {
        dndAccess.value = controls.hasDndAccess()
        controls.refresh()
    }

    fun dndAccessIntent() = controls.dndAccessIntent()

    override fun onCleared() {
        controls.preview(null, 0)
    }
    // endregion

    private companion object {
        const val KEY_MINUTES = "focus_minutes"
        const val KEY_TASK = "focus_task"
    }
}
