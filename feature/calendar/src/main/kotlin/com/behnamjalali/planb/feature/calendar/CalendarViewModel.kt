package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.CalendarMonth
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayItems(val events: List<EventOccurrence> = emptyList(), val tasks: List<Task> = emptyList()) {
    val count: Int get() = events.size + tasks.size
}

data class CalendarUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val view: CalendarView = CalendarView.MONTH,
    val selected: LocalDate = LocalDate.MIN,
    val calendarSystem: CalendarSystem = CalendarSystem.JALALI,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
    val rangeStart: LocalDate = LocalDate.MIN,
    val rangeEnd: LocalDate = LocalDate.MIN,
    val items: Map<LocalDate, DayItems> = emptyMap(),
) {
    val month: CalendarMonth get() = CalendarEngines.of(calendarSystem).monthOf(selected)
    fun itemsOn(date: LocalDate): DayItems = items[date] ?: DayItems()
}

/** Visible date range for a view. Month views include adjacent-month days of the grid. */
internal fun visibleRange(view: CalendarView, selected: LocalDate, system: CalendarSystem, firstDay: DayOfWeek): Pair<LocalDate, LocalDate> =
    when (view) {
        CalendarView.DAY -> selected to selected
        CalendarView.WEEK -> MonthGrid.weekStart(selected, firstDay).let { it to it.plusDays(6) }
        CalendarView.MONTH -> {
            val engine = CalendarEngines.of(system)
            val grid = MonthGrid.build(engine, engine.monthOf(selected), firstDay)
            grid.first().first().date to grid.last().last().date
        }
        CalendarView.AGENDA -> selected to selected.plusDays(AGENDA_DAYS - 1)
    }

internal const val AGENDA_DAYS = 30L

sealed interface CalendarMessage {
    data object Failed : CalendarMessage
    data class Completed(val taskId: EntityId, val nextOccurrenceId: EntityId?) : CalendarMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val view = savedState.getStateFlow<String?>(KEY_VIEW, null)
    private val selectedEpoch = savedState.getStateFlow(KEY_SELECTED, time.today().toEpochDay())

    private val _messages = MutableSharedFlow<CalendarMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<CalendarMessage> = _messages

    private data class Query(val view: CalendarView, val selected: LocalDate, val system: CalendarSystem, val firstDay: DayOfWeek)

    val uiState: StateFlow<CalendarUiState> = combine(view, selectedEpoch, settings.settings) { v, epoch, s ->
        Query(
            view = v?.let { runCatching { CalendarView.valueOf(it) }.getOrNull() } ?: s.defaultCalendarView,
            selected = LocalDate.ofEpochDay(epoch),
            system = s.calendarSystem,
            firstDay = s.firstDayOfWeek,
        )
    }.distinctUntilChanged().flatMapLatest { q ->
        val (from, to) = visibleRange(q.view, q.selected, q.system, q.firstDay)
        combine(
            events.observeOccurrences(from, to),
            tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = time.today(), dueFrom = from, dueTo = to, topLevelOnly = false)),
        ) { occ, dueTasks ->
            val byDateEvents = occ.groupBy { it.date }
            val byDateTasks = dueTasks.groupBy { it.dueDate!! }
            val dates = byDateEvents.keys + byDateTasks.keys
            CalendarUiState(
                loading = false,
                view = q.view,
                selected = q.selected,
                calendarSystem = q.system,
                firstDayOfWeek = q.firstDay,
                rangeStart = from,
                rangeEnd = to,
                items = dates.associateWith { DayItems(byDateEvents[it].orEmpty(), byDateTasks[it].orEmpty()) },
            )
        }
    }.catch { emit(CalendarUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setView(value: CalendarView) {
        savedState[KEY_VIEW] = value.name
    }

    fun select(date: LocalDate) {
        savedState[KEY_SELECTED] = date.toEpochDay()
    }

    fun goToToday() = select(time.today())

    /**
     * Moves by one page of the current view in the user's calendar system. The anchor date and the
     * view come from the saved state, which updates synchronously; [uiState] lags behind its
     * database query, so two quick taps would otherwise both page from the same date.
     */
    fun page(delta: Int) {
        val state = uiState.value
        val selected = LocalDate.ofEpochDay(selectedEpoch.value)
        val currentView = view.value?.let { runCatching { CalendarView.valueOf(it) }.getOrNull() } ?: state.view
        val engine = CalendarEngines.of(state.calendarSystem)
        val next = when (currentView) {
            CalendarView.DAY -> selected.plusDays(delta.toLong())
            CalendarView.WEEK -> selected.plusWeeks(delta.toLong())
            CalendarView.MONTH -> engine.firstDayOfMonth(engine.monthOf(selected).plus(delta))
            CalendarView.AGENDA -> selected.plusDays(AGENDA_DAYS * delta)
        }
        select(next)
    }

    fun setTaskCompleted(id: EntityId, completed: Boolean) {
        viewModelScope.launch {
            runCatchingSafely { tasks.setCompleted(id, completed) }
                .onSuccess { next -> if (completed) _messages.tryEmit(CalendarMessage.Completed(id, next)) }
                .onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    /** Undoes a completion; also removes the occurrence a recurring completion created. */
    fun undoComplete(id: EntityId, nextOccurrenceId: EntityId?) {
        viewModelScope.launch {
            runCatchingSafely {
                val next = nextOccurrenceId?.let { tasks.getTask(it) }
                if (nextOccurrenceId != null) tasks.delete(listOf(nextOccurrenceId))
                // Reopen first, so restoring the series below saves an open task, not a done one.
                tasks.setCompleted(id, false)
                val reopened = tasks.getTask(id)
                if (reopened != null && next != null) {
                    tasks.save(reopened.copy(recurrence = next.recurrence, recurrenceAnchor = next.recurrenceAnchor))
                }
            }.onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    private companion object {
        const val KEY_VIEW = "calendar_view"
        const val KEY_SELECTED = "calendar_selected"
    }
}
