package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.RecurrenceRule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EventForm(
    val id: Long = NEW_ID,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val date: Long = 0,
    val allDay: Boolean = true,
    val start: Int? = null,
    val end: Int? = null,
    val reminder: Int? = null,
    val recurrence: String? = null,
    val color: String = AccentColor.POWDER_BLUE.key,
    val createdAt: Long = 0,
) {
    val localDate: LocalDate get() = LocalDate.ofEpochDay(date)
    val startTime: LocalTime? get() = start?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    val endTime: LocalTime? get() = end?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    val rule: RecurrenceRule? get() = RecurrenceRule.decode(recurrence)
    val timeError: Boolean get() = !allDay && start != null && end != null && end < start
    val valid: Boolean get() = title.isNotBlank() && !timeError

    fun toEvent() = CalendarEvent(
        id = id,
        title = title.trim(),
        description = description,
        date = localDate,
        startTime = if (allDay) null else startTime,
        endTime = if (allDay) null else endTime,
        allDay = allDay || start == null,
        reminderOffsetMinutes = reminder,
        recurrence = rule,
        color = AccentColor.fromKey(color),
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    companion object {
        fun from(e: CalendarEvent) = EventForm(
            id = e.id,
            title = e.title,
            description = e.description,
            notes = e.notes,
            date = e.date.toEpochDay(),
            allDay = e.allDay,
            start = e.startTime?.toSecondOfDay(),
            end = e.endTime?.toSecondOfDay(),
            reminder = e.reminderOffsetMinutes,
            recurrence = e.recurrence?.encode(),
            color = e.color.key,
            createdAt = e.createdAt.toEpochMilli(),
        )
    }
}

enum class EventEditorEvent { Saved, Deleted, Failed, NotFound }

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val events: EventRepository,
    settings: SettingsRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<EventEditorRoute>() }.getOrDefault(EventEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    // Must be read before getStateFlow(), which stores its default value in the handle.
    private val needsLoad = !savedState.contains(KEY_FORM)
    private var original: EventForm? = savedState.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString(EventForm.serializer(), it) }

    val form: StateFlow<EventForm> = savedState.getStateFlow(KEY_FORM, "")
        .map { if (it.isBlank()) EventForm(date = time.today().toEpochDay()) else json.decodeFromString(EventForm.serializer(), it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EventForm(date = time.today().toEpochDay()))

    val calendarSystem: StateFlow<CalendarSystem> = settings.settings.map { it.calendarSystem }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSystem.JALALI)

    private val _events = MutableSharedFlow<EventEditorEvent>(extraBufferCapacity = 2)
    val editorEvents: SharedFlow<EventEditorEvent> = _events
    val isNew: Boolean get() = route.eventId == NEW_ID
    val isDirty: Boolean get() = original != null && original != form.value

    /** A saved repeating event: edits and deletes apply to every occurrence (there are no per-date exceptions). */
    val isSeries: Boolean get() = !isNew && original?.recurrence != null

    /** True from the first Save until it fails; a second tap must not insert the event twice. */
    private var saving = false

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (route.eventId != NEW_ID) {
                    val event = events.getEvent(route.eventId) ?: run {
                        _events.tryEmit(EventEditorEvent.NotFound)
                        return@launch
                    }
                    EventForm.from(event)
                } else {
                    EventForm(date = route.dateEpochDay ?: time.today().toEpochDay())
                }
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(EventForm.serializer(), initial)
                write(initial)
            }
        }
    }

    private fun write(form: EventForm) {
        savedState[KEY_FORM] = json.encodeToString(EventForm.serializer(), form)
    }

    fun update(transform: (EventForm) -> EventForm) = write(transform(form.value))

    fun setStart(time: LocalTime?) = update { f ->
        val start = time?.toSecondOfDay()
        val end = when {
            start == null -> f.end
            f.end == null || f.end < start -> (start + 3600).coerceAtMost(86_399)
            else -> f.end
        }
        f.copy(start = start, end = end, allDay = start == null)
    }

    fun save() {
        val current = form.value
        if (saving || !current.valid) return
        saving = true
        viewModelScope.launch {
            runCatchingSafely { events.save(current.toEvent()) }
                .onSuccess {
                    original = form.value
                    _events.tryEmit(EventEditorEvent.Saved)
                }
                .onFailure {
                    saving = false
                    _events.tryEmit(EventEditorEvent.Failed)
                }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            runCatchingSafely { events.delete(route.eventId) }
                .onSuccess { _events.tryEmit(EventEditorEvent.Deleted) }
                .onFailure { _events.tryEmit(EventEditorEvent.Failed) }
        }
    }

    private companion object {
        const val KEY_FORM = "event_form"
        const val KEY_ORIGINAL = "event_form_original"
    }
}
