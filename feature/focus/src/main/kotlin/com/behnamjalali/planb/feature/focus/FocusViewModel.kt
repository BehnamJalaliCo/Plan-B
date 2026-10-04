package com.behnamjalali.planb.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.ReminderScheduler
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) {
    val linkedTask: Task? get() = tasks.firstOrNull { it.id == (active?.linkedTaskId ?: linkedTaskId) }
}

enum class FocusMessage { Completed, Failed }

/**
 * Focus timer. The session's logical time is computed from stored timestamps
 * (FocusSession.elapsedMillis); the UI only re-reads it on each frame/tick, so
 * dropped frames or backgrounding never change the result.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val focus: FocusRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val time: TimeProvider,
) : ViewModel() {
    private val selected = savedState.getStateFlow<Int?>(KEY_MINUTES, null)
    private val linked = savedState.getStateFlow<Long?>(KEY_TASK, null)
    private val _messages = MutableSharedFlow<FocusMessage>(extraBufferCapacity = 2)
    val messages: SharedFlow<FocusMessage> = _messages

    private val today = time.today()
    private val dayStart = today.atStartOfDay(time.zone()).toInstant()
    private val dayEnd = today.plusDays(1).atStartOfDay(time.zone()).toInstant()

    val uiState: StateFlow<FocusUiState> = combine(
        listOf(
            focus.observeActive(), focus.observeHistory(30), focus.observeFocusedMillis(dayStart, dayEnd), settings.settings, selected, linked,
            tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today, limit = 50)),
        ),
    ) { v ->
        val s = v[3] as com.behnamjalali.planb.core.model.UserSettings
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun now(): Instant = time.now()

    fun selectMinutes(minutes: Int) { savedState[KEY_MINUTES] = minutes.coerceIn(1, 180) }
    fun linkTask(id: EntityId?) { savedState[KEY_TASK] = id }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _messages.tryEmit(FocusMessage.Failed) }
    }

    fun start() = launchSafely {
        val minutes = uiState.value.selectedMinutes
        val session = focus.start(minutes * 60_000L, uiState.value.linkedTaskId)
        reminders.scheduleFocusEnd(session.startedAt.plusMillis(session.plannedDurationMillis))
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
        recordOnTask(done)
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
            recordOnTask(done)
            _messages.tryEmit(FocusMessage.Completed)
        }
    }

    /** Adds the focused minutes to the linked task's actual duration. */
    private suspend fun recordOnTask(session: FocusSession) {
        val taskId = session.linkedTaskId ?: return
        val task = tasks.getTask(taskId) ?: return
        val minutes = (session.actualDurationMillis / 60_000L).toInt()
        if (minutes > 0) tasks.save(task.copy(actualMinutes = (task.actualMinutes ?: 0) + minutes))
    }

    private companion object {
        const val KEY_MINUTES = "focus_minutes"
        const val KEY_TASK = "focus_task"
    }
}
