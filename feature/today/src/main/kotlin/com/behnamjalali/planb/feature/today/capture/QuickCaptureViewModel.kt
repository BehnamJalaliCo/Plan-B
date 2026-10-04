package com.behnamjalali.planb.feature.today.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class CaptureType { TASK, NOTE, EVENT, HABIT, PROJECT }

/** Result of a capture, so the UI can confirm and offer "Open". */
sealed interface CaptureEvent {
    data class Saved(val type: CaptureType, val id: EntityId) : CaptureEvent
    data object Failed : CaptureEvent
}

/**
 * Draft state lives in [SavedStateHandle] so text survives rotation and
 * process recreation while the sheet is open.
 */
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val eventRepository: EventRepository,
    private val habits: HabitRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val type: StateFlow<CaptureType> = savedState.getStateFlow(KEY_TYPE, CaptureType.TASK)
    val title: StateFlow<String> = savedState.getStateFlow(KEY_TITLE, "")
    val body: StateFlow<String> = savedState.getStateFlow(KEY_BODY, "")
    val dateEpoch: StateFlow<Long?> = savedState.getStateFlow(KEY_DATE, time.today().toEpochDay())
    val timeSeconds: StateFlow<Int?> = savedState.getStateFlow(KEY_TIME, null)
    val highPriority: StateFlow<Boolean> = savedState.getStateFlow(KEY_PRIORITY, false)

    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<CaptureEvent> = _events

    val today: LocalDate get() = time.today()

    fun setType(value: CaptureType) {
        savedState[KEY_TYPE] = value
        if (value == CaptureType.EVENT && dateEpoch.value == null) savedState[KEY_DATE] = today.toEpochDay()
    }
    fun setTitle(value: String) { savedState[KEY_TITLE] = value }
    fun setBody(value: String) { savedState[KEY_BODY] = value }
    fun setDate(value: LocalDate?) { savedState[KEY_DATE] = value?.toEpochDay() }
    fun setTime(value: LocalTime?) { savedState[KEY_TIME] = value?.toSecondOfDay() }
    fun setHighPriority(value: Boolean) { savedState[KEY_PRIORITY] = value }

    val canSave: Boolean get() = title.value.isNotBlank() || (type.value == CaptureType.NOTE && body.value.isNotBlank())

    fun save(defaultNotebookTitle: String) {
        if (!canSave) return
        val text = title.value.trim()
        val date = dateEpoch.value?.let(LocalDate::ofEpochDay)
        val at = timeSeconds.value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
        val kind = type.value
        viewModelScope.launch {
            val result = runCatchingSafely {
                when (kind) {
                    CaptureType.TASK -> {
                        val reminder = if (at != null) settings.current().defaultReminderMinutes else null
                        tasks.save(
                            Task(
                                title = text,
                                dueDate = date,
                                dueTime = if (date != null) at else null,
                                reminderOffsetMinutes = reminder,
                                priority = if (highPriority.value) Priority.HIGH else Priority.NONE,
                            ),
                        )
                    }
                    CaptureType.NOTE -> {
                        val notebook = notes.ensureDefaultNotebook(defaultNotebookTitle)
                        val blocks = body.value.lines().filter { it.isNotBlank() }
                            .map { NoteBlock(UUID.randomUUID().toString(), text = it) }
                        notes.saveNote(Note(notebookId = notebook, title = text, document = NoteDocument(blocks = blocks)))
                    }
                    CaptureType.EVENT -> eventRepository.save(
                        CalendarEvent(
                            title = text,
                            date = date ?: today,
                            startTime = at,
                            endTime = at?.plusHours(1)?.takeIf { it > at },
                            allDay = at == null,
                        ),
                    )
                    CaptureType.HABIT -> habits.save(Habit(title = text, startDate = today))
                    CaptureType.PROJECT -> projects.save(Project(title = text))
                }
            }
            result.onSuccess { id ->
                clear()
                _events.tryEmit(CaptureEvent.Saved(kind, id))
            }.onFailure { _events.tryEmit(CaptureEvent.Failed) }
        }
    }

    fun clear() {
        savedState[KEY_TITLE] = ""
        savedState[KEY_BODY] = ""
        savedState[KEY_TIME] = null
        savedState[KEY_PRIORITY] = false
        savedState[KEY_DATE] = time.today().toEpochDay()
    }

    private companion object {
        const val KEY_TYPE = "capture_type"
        const val KEY_TITLE = "capture_title"
        const val KEY_BODY = "capture_body"
        const val KEY_DATE = "capture_date"
        const val KEY_TIME = "capture_time"
        const val KEY_PRIORITY = "capture_priority"
    }
}
