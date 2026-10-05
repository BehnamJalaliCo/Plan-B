package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarItem
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarSource
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.CalendarMonth
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.datetime.iran.HijriDate
import com.behnamjalali.planb.core.datetime.iran.IranCalendar
import com.behnamjalali.planb.core.datetime.iran.OccasionDay
import com.behnamjalali.planb.core.model.CalendarDecorations
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * What the calendar shows. [TIMELINE] is the vertical day timeline (Plan-B Pro #7); the others
 * are the free views of [CalendarView] (the default view in Settings stays one of those).
 */
enum class CalendarMode {
    MONTH, WEEK, DAY, TIMELINE, AGENDA;

    /** The data range follows the day for the timeline. */
    val dataView: CalendarView
        get() = when (this) {
            MONTH -> CalendarView.MONTH
            WEEK -> CalendarView.WEEK
            DAY, TIMELINE -> CalendarView.DAY
            AGENDA -> CalendarView.AGENDA
        }

    companion object {
        fun of(view: CalendarView): CalendarMode = when (view) {
            CalendarView.DAY -> DAY
            CalendarView.WEEK -> WEEK
            CalendarView.MONTH -> MONTH
            CalendarView.AGENDA -> AGENDA
        }

        fun parse(value: String?): CalendarMode? = entries.firstOrNull { it.name == value }
    }
}

data class DayItems(
    val events: List<EventOccurrence> = emptyList(),
    val tasks: List<Task> = emptyList(),
    /** Read-only events of device calendars (Plan-B Pro #3). */
    val device: List<DeviceCalendarItem> = emptyList(),
) {
    val count: Int get() = events.size + tasks.size + device.size
}

data class CalendarUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val mode: CalendarMode = CalendarMode.MONTH,
    val selected: LocalDate = LocalDate.MIN,
    val calendarSystem: CalendarSystem = CalendarSystem.JALALI,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
    val rangeStart: LocalDate = LocalDate.MIN,
    val rangeEnd: LocalDate = LocalDate.MIN,
    val items: Map<LocalDate, DayItems> = emptyMap(),
    /** Iran's holidays and occasions in the range (shown only to Pro users, see [decorations]). */
    val occasions: Map<LocalDate, List<OccasionDay>> = emptyMap(),
    /** The Hijri date of [selected]. */
    val hijri: HijriDate? = null,
    val decorations: CalendarDecorations = CalendarDecorations(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val view: CalendarView get() = mode.dataView
    val month: CalendarMonth get() = CalendarEngines.of(calendarSystem).monthOf(selected)
    fun itemsOn(date: LocalDate): DayItems = items[date] ?: DayItems()
    fun occasionsOn(date: LocalDate): List<OccasionDay> = occasions[date].orEmpty()

    /** Official holiday (Pro, when shown). Fridays count in the Jalali calendar. */
    fun isOffDay(date: LocalDate): Boolean = decorations.holidays &&
        (occasionsOn(date).any { it.holiday } || (calendarSystem == CalendarSystem.JALALI && IranCalendar.isWeekend(date)))
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
    data object Imported : CalendarMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    private val device: DeviceCalendarSource,
) : ViewModel() {
    private val view = savedState.getStateFlow<String?>(KEY_VIEW, null)
    private val selectedEpoch = savedState.getStateFlow(KEY_SELECTED, time.today().toEpochDay())

    private val _messages = MutableSharedFlow<CalendarMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<CalendarMessage> = _messages

    private data class Query(
        val mode: CalendarMode,
        val selected: LocalDate,
        val system: CalendarSystem,
        val firstDay: DayOfWeek,
        val decorations: CalendarDecorations,
    )

    private val iran = IranCalendar.Default

    val uiState: StateFlow<CalendarUiState> = combine(view, selectedEpoch, settings.settings) { v, epoch, s ->
        Query(
            mode = CalendarMode.parse(v) ?: CalendarMode.of(s.defaultCalendarView),
            selected = LocalDate.ofEpochDay(epoch),
            system = s.calendarSystem,
            firstDay = s.firstDayOfWeek,
            decorations = s.calendarDecorations,
        )
    }.distinctUntilChanged().flatMapLatest { q ->
        val (from, to) = visibleRange(q.mode.dataView, q.selected, q.system, q.firstDay)
        val zone = time.zone()
        val filter = TaskFilter(
            view = TaskView.ALL,
            today = time.today(),
            dueFrom = from,
            dueTo = to,
            topLevelOnly = false,
            // Time-blocked tasks (Plan-B Pro #6) appear on the day of their block.
            scheduledFrom = from.atStartOfDay(zone).toInstant(),
            scheduledTo = to.plusDays(1).atStartOfDay(zone).toInstant(),
        )
        combine(
            events.observeOccurrences(from, to),
            tasks.observeTasks(filter),
            device.observeItems(from, to),
        ) { occ, dueTasks, deviceItems ->
            val byDateEvents = occ.groupBy { it.date }
            val byDateTasks = dueTasks.groupBy { task -> task.scheduledStart?.atZone(zone)?.toLocalDate() ?: task.dueDate!! }
            val byDateDevice = deviceItems.groupBy { it.date }
            val dates = byDateEvents.keys + byDateTasks.keys + byDateDevice.keys
            CalendarUiState(
                loading = false,
                mode = q.mode,
                selected = q.selected,
                calendarSystem = q.system,
                firstDayOfWeek = q.firstDay,
                rangeStart = from,
                rangeEnd = to,
                items = dates.associateWith { DayItems(byDateEvents[it].orEmpty(), byDateTasks[it].orEmpty(), byDateDevice[it].orEmpty()) },
                occasions = iran.occasionsBetween(from, to).groupBy { it.date },
                hijri = iran.hijri.toHijri(q.selected),
                decorations = q.decorations,
                zone = zone,
            )
        }
    }.flowOn(Dispatchers.Default)
        .catch { emit(CalendarUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setMode(value: CalendarMode) {
        savedState[KEY_VIEW] = value.name
    }

    fun setView(value: CalendarView) = setMode(CalendarMode.of(value))

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
        val currentMode = CalendarMode.parse(view.value) ?: state.mode
        val engine = CalendarEngines.of(state.calendarSystem)
        val next = when (currentMode) {
            CalendarMode.DAY, CalendarMode.TIMELINE -> selected.plusDays(delta.toLong())
            CalendarMode.WEEK -> selected.plusWeeks(delta.toLong())
            CalendarMode.MONTH -> engine.firstDayOfMonth(engine.monthOf(selected).plus(delta))
            CalendarMode.AGENDA -> selected.plusDays(AGENDA_DAYS * delta)
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

    /**
     * Undoes a completion. Reopening a recurring occurrence also removes the untouched occurrence
     * its completion created and moves the series back (see TaskRepository.setCompleted).
     */
    fun undoComplete(id: EntityId) {
        viewModelScope.launch {
            runCatchingSafely { tasks.setCompleted(id, false) }
                .onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    /**
     * Time-blocks a task (Plan-B Pro #6): [startMinute] on [date], [minutes] long (snapped to a
     * quarter hour). A task without a date is planned for that day, so it shows in task lists.
     */
    fun scheduleTask(id: EntityId, date: LocalDate, startMinute: Int, minutes: Int) {
        viewModelScope.launch {
            runCatchingSafely {
                val task = tasks.getTask(id) ?: return@runCatchingSafely
                val duration = minutes.coerceIn(TimeBlocks.MIN_MINUTES, TimeBlocks.DAY_MINUTES)
                val start = TimeBlocks.clampStart(startMinute, duration)
                val zone = time.zone()
                tasks.save(
                    task.copy(
                        scheduledStart = TimeBlocks.instantOf(date, start, zone),
                        scheduledEnd = TimeBlocks.instantOf(date, start + duration, zone),
                        dueDate = task.dueDate ?: date,
                    ),
                )
            }.onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    /** Removes a task's time block; its dates stay as they are. */
    fun unscheduleTask(id: EntityId) {
        viewModelScope.launch {
            runCatchingSafely {
                val task = tasks.getTask(id) ?: return@runCatchingSafely
                tasks.save(task.copy(scheduledStart = null, scheduledEnd = null))
            }.onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    /** Copies a device calendar event into Plan-B (Plan-B Pro #3). */
    fun importDeviceItem(item: DeviceCalendarItem) {
        viewModelScope.launch {
            runCatchingSafely { device.import(item) }
                .onSuccess { if (it != null) _messages.tryEmit(CalendarMessage.Imported) else _messages.tryEmit(CalendarMessage.Failed) }
                .onFailure { _messages.tryEmit(CalendarMessage.Failed) }
        }
    }

    /** The current minute of the day in the device's zone, for the "now" line; ticks every minute. */
    val nowMinute: StateFlow<Int> = flow {
        while (true) {
            val now = time.now().atZone(time.zone()).toLocalTime()
            emit(now.hour * 60 + now.minute)
            delay((60 - now.second) * 1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentMinute())

    private fun currentMinute(): Int = time.now().atZone(time.zone()).toLocalTime().let { it.hour * 60 + it.minute }

    private companion object {
        const val KEY_VIEW = "calendar_view"
        const val KEY_SELECTED = "calendar_selected"
    }
}
