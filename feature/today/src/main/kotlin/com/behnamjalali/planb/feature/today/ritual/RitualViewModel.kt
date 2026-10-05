package com.behnamjalali.planb.feature.today.ritual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.RitualJournalRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.DayPlanRequest
import com.behnamjalali.planb.core.model.DayPlanner
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.RitualState
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.feature.today.RitualKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The steps of each ritual, in order. */
enum class RitualStep { REVIEW, TOP3, CALENDAR, PLAN, INTENTION, DONE, LEFTOVERS, JOURNAL, TOMORROW }

fun stepsOf(kind: RitualKind): List<RitualStep> = when (kind) {
    RitualKind.MORNING -> listOf(RitualStep.REVIEW, RitualStep.TOP3, RitualStep.CALENDAR, RitualStep.PLAN, RitualStep.INTENTION)
    RitualKind.EVENING -> listOf(RitualStep.DONE, RitualStep.LEFTOVERS, RitualStep.JOURNAL, RitualStep.TOMORROW)
}

/** Where a task goes from the review or leftovers steps. [DROP] archives it (it can be restored). */
enum class TaskMove { TODAY, TOMORROW, NEXT_WEEK, DROP }

data class RitualData(
    val date: LocalDate,
    /** Open tasks planned before today (morning review). */
    val unfinished: List<Task>,
    /** Open tasks of Today's list. */
    val today: List<Task>,
    /** Open tasks planned for today or earlier (evening leftovers). */
    val leftovers: List<Task>,
    val completedToday: List<Task>,
    /** Candidates for tomorrow's top 3: open tasks due by tomorrow. */
    val tomorrow: List<Task>,
    val events: List<EventOccurrence>,
    val freeMinutes: Int,
    val rituals: RitualState,
) {
    fun focus(kind: RitualKind): List<EntityId> = rituals.focusFor(if (kind == RitualKind.MORNING) date else date.plusDays(1))
}

sealed interface RitualEvent {
    data object Finished : RitualEvent
    data object Failed : RitualEvent
}

/** Texts written into the journal page; resolved by the screen in the user's language. */
data class JournalTexts(val heading: String, val notebook: String, val pageTitle: String)

/**
 * Morning planning and evening shutdown (Plan-B Pro #8). Every change happens at the user's
 * tap; the reflection goes to the journal page of the day (see [RitualJournalRepository]).
 */
@HiltViewModel
class RitualViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    events: EventRepository,
    private val settings: SettingsRepository,
    private val journal: RitualJournalRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val kind: RitualKind = RitualKind.fromKey(savedState.get<String>("kind"))
    val steps: List<RitualStep> = stepsOf(kind)

    /** The ritual's day stays fixed while it is open, even across midnight. */
    private val date: LocalDate = savedState.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay) ?: time.today().also {
        savedState[KEY_DATE] = it.toEpochDay()
    }

    val step: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)
    val text: StateFlow<String> = savedState.getStateFlow(KEY_TEXT, "")

    private val _events = MutableSharedFlow<RitualEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<RitualEvent> = _events

    private val todayTasks = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = date))
    private val completed = tasks.observeTasks(TaskFilter(view = TaskView.COMPLETED, today = date, limit = COMPLETED_LIMIT)).map { list ->
        val zone = time.zone()
        list.filter { it.completedAt?.atZone(zone)?.toLocalDate() == date }
    }
    private val tomorrowTasks = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = date.plusDays(1)))

    val data: StateFlow<RitualData?> = combine(
        todayTasks, completed, tomorrowTasks, events.observeOccurrences(date, date), settings.settings,
    ) { today, done, tomorrow, occurrences, s ->
        RitualData(
            date = date,
            unfinished = today.filter { t -> t.dueDate?.let { it < date } == true },
            today = today,
            leftovers = today.filter { t -> t.dueDate?.let { it <= date } == true },
            completedToday = done,
            tomorrow = tomorrow,
            events = occurrences,
            freeMinutes = freeMinutes(today, occurrences, s),
            rituals = s.rituals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun freeMinutes(today: List<Task>, occurrences: List<EventOccurrence>, s: UserSettings): Int {
        val zone = time.zone()
        val busy = DayPlanner.eventRanges(occurrences, date, zone) + DayPlanner.taskRanges(today, date, zone)
        // Free time without buffers: what is really left, not what the planner would use.
        val request = DayPlanRequest(date, zone, time.now(), s.dayPlan.copy(bufferMinutes = 0), busy)
        return DayPlanner.freeTime(request).sumOf { it.minutes }.toInt()
    }

    fun next() {
        if (step.value < steps.lastIndex) savedState[KEY_STEP] = step.value + 1
    }

    fun back() {
        if (step.value > 0) savedState[KEY_STEP] = step.value - 1
    }

    fun setText(value: String) {
        savedState[KEY_TEXT] = value
    }

    fun move(task: Task, move: TaskMove) {
        viewModelScope.launch {
            runCatchingSafely {
                if (move == TaskMove.DROP) {
                    tasks.setArchived(listOf(task.id), true)
                } else {
                    val target = when (move) {
                        TaskMove.TODAY -> date
                        TaskMove.TOMORROW -> date.plusDays(1)
                        else -> nextWeekStart(settings.current().firstDayOfWeek)
                    }
                    // A time block belongs to the old day; it is cleared with the move.
                    tasks.save(
                        task.copy(
                            dueDate = target,
                            startDate = task.startDate?.takeIf { it <= target },
                            scheduledStart = null,
                            scheduledEnd = null,
                        ),
                    )
                }
            }.onFailure { _events.tryEmit(RitualEvent.Failed) }
        }
    }

    private fun nextWeekStart(firstDay: java.time.DayOfWeek): LocalDate =
        date.minusDays(Math.floorMod(date.dayOfWeek.value - firstDay.value, 7).toLong()).plusDays(7)

    /** Adds or removes [taskId] from the top 3 (today's in the morning, tomorrow's in the evening). */
    fun toggleFocus(taskId: EntityId) {
        val day = if (kind == RitualKind.MORNING) date else date.plusDays(1)
        viewModelScope.launch {
            settings.update { s ->
                val current = s.rituals.focusFor(day)
                val updated = when {
                    taskId in current -> current - taskId
                    current.size < RitualState.TOP_COUNT -> current + taskId
                    else -> current
                }
                s.copy(rituals = s.rituals.copy(focusDate = day, focusTaskIds = updated))
            }
        }
    }

    private var finishing = false

    /** Saves the intention or reflection (when written) and marks the ritual done for today. */
    fun finish(texts: JournalTexts) {
        if (finishing) return
        finishing = true
        val written = text.value.trim()
        viewModelScope.launch {
            val result = runCatchingSafely {
                if (written.isNotEmpty()) {
                    journal.append(date, texts.heading, written, texts.notebook, texts.pageTitle, "ritual_${kind.key}")
                }
                settings.update { s ->
                    s.copy(
                        rituals = when (kind) {
                            RitualKind.MORNING -> s.rituals.copy(morningDoneOn = date)
                            RitualKind.EVENING -> s.rituals.copy(eveningDoneOn = date)
                        },
                    )
                }
            }
            result.onSuccess {
                savedState[KEY_TEXT] = ""
                _events.tryEmit(RitualEvent.Finished)
            }.onFailure { _events.tryEmit(RitualEvent.Failed) }
            finishing = false
        }
    }

    private companion object {
        const val KEY_STEP = "ritual_step"
        const val KEY_TEXT = "ritual_text"
        const val KEY_DATE = "ritual_date"
        const val COMPLETED_LIMIT = 100
    }
}
