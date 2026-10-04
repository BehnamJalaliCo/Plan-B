package com.behnamjalali.planb.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.HabitWithHistory
import com.behnamjalali.planb.core.data.repository.NoteRepository
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.DashboardConfig
import com.behnamjalali.planb.core.model.DashboardSection
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.Streak
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

data class HabitToday(val habit: HabitWithHistory, val amount: Int, val streak: Streak)

/** A row on the Today timeline: either a timed task or an event. */
sealed interface TimelineItem {
    val time: LocalTime?

    data class TaskItem(val task: Task) : TimelineItem {
        override val time: LocalTime? get() = task.dueTime
    }

    data class EventItem(val occurrence: EventOccurrence) : TimelineItem {
        override val time: LocalTime? get() = occurrence.event.startTime
    }
}

data class TodayData(
    val date: LocalDate,
    val greeting: Greeting,
    val dashboard: DashboardConfig,
    val todayTasks: List<Task>,
    val completedToday: Int,
    val upcoming: List<Task>,
    val events: List<EventOccurrence>,
    val habits: List<HabitToday>,
    val activeFocus: FocusSession?,
    val focusMinutesToday: Int,
    val projects: List<ProjectSummary>,
    val notes: List<Note>,
) {
    val overdueCount: Int get() = todayTasks.count { it.isOverdue(date) }
    val dueTodayCount: Int get() = todayTasks.count { it.dueDate == date }
    val totalForProgress: Int get() = todayTasks.size + completedToday
    val progress: Float get() = if (totalForProgress == 0) 0f else completedToday.toFloat() / totalForProgress
    val habitsDone: Int get() = habits.count { it.amount >= it.habit.habit.target }

    val timeline: List<TimelineItem>
        get() = (todayTasks.filter { it.dueDate == date && it.dueTime != null }.map { TimelineItem.TaskItem(it) } +
            events.map { TimelineItem.EventItem(it) })
            .sortedWith(compareBy<TimelineItem> { it.time != null }.thenBy { it.time })
}

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Success(val data: TodayData) : TodayUiState
    data object Error : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val events: EventRepository,
    private val habits: HabitRepository,
    private val focus: FocusRepository,
    private val projects: ProjectRepository,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val _messages = MutableSharedFlow<TodayMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TodayMessage> = _messages

    /** Test hook: overrides the wait between greeting checks (normally until the next minute). */
    internal var greetingTickMillis: Long? = null

    /** The greeting follows the clock, not data changes, so it never goes stale while the screen is open. */
    private val greeting: Flow<Greeting> = flow {
        while (true) {
            val now = time.localNow()
            emit(greetingFor(now.toLocalTime()))
            val untilNextMinute = 60_000L - (now.second * 1000L + now.nano / 1_000_000L)
            delay(greetingTickMillis ?: untilNextMinute.coerceIn(1_000L, 60_000L))
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<TodayUiState> = time.todayFlow().flatMapLatest { today -> dayFlow(today) }
        .combine(greeting) { data, greeting -> data.copy(greeting = greeting) }
        .map<TodayData, TodayUiState> { TodayUiState.Success(it) }
        .catch { emit(TodayUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    private fun dayFlow(today: LocalDate) = run {
        val zone = time.zone()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val todayTasks = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today))
        val upcoming = tasks.observeTasks(
            TaskFilter(view = TaskView.UPCOMING, today = today, upcomingUntil = today.plusDays(7), limit = 5),
        )
        val completed = tasks.observeCompletedCount(dayStart, dayEnd)
        val dayEvents = events.observeOccurrences(today, today)
        val habitFlow = habits.observeHabits(today.minusDays(400), today)
        val focusFlow = combine(focus.observeActive(), focus.observeFocusedMillis(dayStart, dayEnd)) { a, ms -> a to ms }
        val projectFlow = projects.observeProjects(archived = false)
        val noteFlow = combine(notes.observePinnedOrFavorite(4), notes.observeRecent(4)) { pinned, recent ->
            (pinned + recent).distinctBy { it.id }.take(4)
        }
        combine(
            listOf(settings.settings, todayTasks, upcoming, completed, dayEvents, habitFlow, focusFlow, projectFlow, noteFlow),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val s = values[0] as com.behnamjalali.planb.core.model.UserSettings
            @Suppress("UNCHECKED_CAST")
            val habitList = values[5] as List<HabitWithHistory>
            @Suppress("UNCHECKED_CAST")
            val (active, focusedMs) = values[6] as Pair<FocusSession?, Long>
            @Suppress("UNCHECKED_CAST")
            TodayData(
                date = today,
                greeting = greetingFor(time.localNow().toLocalTime()),
                dashboard = s.dashboard,
                todayTasks = values[1] as List<Task>,
                completedToday = values[3] as Int,
                upcoming = values[2] as List<Task>,
                events = values[4] as List<EventOccurrence>,
                habits = habitList
                    .filter { HabitStats.isScheduled(it.habit, today) || it.amounts.containsKey(today) }
                    .map { h ->
                        HabitToday(h, h.amounts[today] ?: 0, HabitStats.currentStreak(h.habit, h.amounts, today, s.firstDayOfWeek))
                    },
                activeFocus = active,
                focusMinutesToday = (focusedMs / 60_000L).toInt(),
                projects = (values[7] as List<ProjectSummary>)
                    .filter { it.project.status == ProjectStatus.ACTIVE }
                    .sortedWith(compareBy(nullsLast()) { it.project.dueDate })
                    .take(3),
                notes = values[8] as List<Note>,
            )
        }
    }

    fun setTaskCompleted(id: EntityId, completed: Boolean) {
        viewModelScope.launch {
            runCatching { tasks.setCompleted(id, completed) }
                .onFailure { _messages.tryEmit(TodayMessage.ActionFailed) }
        }
    }

    fun checkInHabit(habitId: EntityId, done: Boolean) {
        viewModelScope.launch {
            val today = time.today()
            runCatching { habits.checkIn(habitId, today, if (done) -1 else 1) }
                .onFailure { _messages.tryEmit(TodayMessage.ActionFailed) }
        }
    }

    fun updateDashboard(config: DashboardConfig) {
        viewModelScope.launch { settings.update { it.copy(dashboard = config) } }
    }

    fun moveSection(section: DashboardSection, delta: Int) {
        viewModelScope.launch {
            settings.update { s ->
                val order = s.dashboard.order.toMutableList()
                val index = order.indexOf(section)
                val target = (index + delta).coerceIn(0, order.lastIndex)
                if (index >= 0 && target != index) {
                    order.removeAt(index)
                    order.add(target, section)
                }
                s.copy(dashboard = s.dashboard.copy(order = order))
            }
        }
    }

    fun setSectionVisible(section: DashboardSection, visible: Boolean) {
        viewModelScope.launch {
            settings.update { s ->
                val hidden = if (visible) s.dashboard.hidden - section else s.dashboard.hidden + section
                s.copy(dashboard = s.dashboard.copy(hidden = hidden))
            }
        }
    }

    companion object {
        fun greetingFor(time: LocalTime): Greeting = when (time.hour) {
            in 5..11 -> Greeting.MORNING
            in 12..16 -> Greeting.AFTERNOON
            in 17..20 -> Greeting.EVENING
            else -> Greeting.NIGHT
        }
    }
}

enum class TodayMessage { ActionFailed }
