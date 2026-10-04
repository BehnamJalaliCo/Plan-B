package com.behnamjalali.planb.feature.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.HabitWithHistory
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.NEW_ID
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Streak
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data object HabitsRoute
@Serializable data class HabitDetailRoute(val habitId: Long)
@Serializable data class HabitEditorRoute(val habitId: Long = 0)

data class HabitRow(val item: HabitWithHistory, val today: LocalDate, val streak: Streak) {
    val habit: Habit get() = item.habit
    val todayAmount: Int get() = item.amounts[today] ?: 0
    val scheduledToday: Boolean get() = HabitStats.isScheduled(habit, today)
}

data class HabitsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val showArchived: Boolean = false,
    val habits: List<HabitRow> = emptyList(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
)

private const val HISTORY_DAYS = 400L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val habits: HabitRepository,
    settings: SettingsRepository,
    time: TimeProvider,
) : ViewModel() {
    private val archived = savedState.getStateFlow(KEY_ARCHIVED, false)
    private val _failed = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val failed: SharedFlow<Unit> = _failed

    val uiState: StateFlow<HabitsUiState> = combine(archived, time.todayFlow(), settings.settings) { a, today, s -> Triple(a, today, s.firstDayOfWeek) }
        .flatMapLatest { (showArchived, today, firstDay) ->
            habits.observeHabits(today.minusDays(HISTORY_DAYS), today, showArchived).map { list ->
                HabitsUiState(
                    loading = false,
                    showArchived = showArchived,
                    habits = list.map { HabitRow(it, today, HabitStats.currentStreak(it.habit, it.amounts, today, firstDay)) },
                    firstDayOfWeek = firstDay,
                )
            }
        }.catch { emit(HabitsUiState(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggleArchived() { savedState[KEY_ARCHIVED] = !archived.value }

    fun checkIn(row: HabitRow) {
        val done = row.todayAmount >= row.habit.target
        viewModelScope.launch {
            // Tapping a completed habit undoes today's check-ins entirely.
            runCatchingSafely { habits.checkIn(row.habit.id, row.today, if (done) -row.todayAmount else 1) }
                .onFailure { _failed.tryEmit(Unit) }
        }
    }

    private companion object {
        const val KEY_ARCHIVED = "habits_archived"
    }
}

data class HabitDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val item: HabitWithHistory? = null,
    val today: LocalDate = LocalDate.MIN,
    val streak: Streak = Streak(0, Streak.Unit.DAYS),
    val bestStreak: Int = 0,
    val rate30: Float = 0f,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
)

enum class HabitEvent { Deleted, Failed }

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val habits: HabitRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val habitId = savedState.toRoute<HabitDetailRoute>().habitId
    private val _events = MutableSharedFlow<HabitEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<HabitEvent> = _events

    val uiState: StateFlow<HabitDetailUiState> = combine(habits.observeHabit(habitId), time.todayFlow(), settings.settings) { item, today, s ->
        if (item == null) {
            HabitDetailUiState(loading = false, missing = true)
        } else {
            HabitDetailUiState(
                loading = false,
                item = item,
                today = today,
                streak = HabitStats.currentStreak(item.habit, item.amounts, today, s.firstDayOfWeek),
                bestStreak = HabitStats.bestStreakDays(item.habit, item.amounts, today),
                rate30 = HabitStats.completionRate(item.habit, item.amounts, maxOf(item.habit.startDate, today.minusDays(29)), today),
                firstDayOfWeek = s.firstDayOfWeek,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState())

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(HabitEvent.Failed) }
    }

    fun adjust(delta: Int) = launchSafely { habits.checkIn(habitId, time.today(), delta) }
    fun setArchived(archived: Boolean) = launchSafely { habits.setArchived(habitId, archived) }
    fun delete() = launchSafely {
        habits.delete(habitId)
        _events.tryEmit(HabitEvent.Deleted)
    }
}

enum class ScheduleKind { DAILY, DAYS, WEEKLY, INTERVAL }

@Serializable
data class HabitForm(
    val id: Long = NEW_ID,
    val title: String = "",
    val icon: String = PlannerIcon.STAR.key,
    val color: String = AccentColor.MINT.key,
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val days: Set<Int> = emptySet(),
    val timesPerWeek: String = "3",
    val interval: String = "2",
    val target: String = "1",
    val unit: String = "",
    val reminder: Int? = null,
    val startDate: Long = 0,
    val createdAt: Long = 0,
    val archived: Boolean = false,
) {
    private fun number(text: String): Int? = Digits.toLatin(text.trim()).toIntOrNull()?.takeIf { it in 1..999 }
    val targetValid get() = number(target) != null
    val timesValid get() = kind != ScheduleKind.WEEKLY || number(timesPerWeek)?.let { it in 1..7 } == true
    val intervalValid get() = kind != ScheduleKind.INTERVAL || number(interval) != null
    val daysValid get() = kind != ScheduleKind.DAYS || days.isNotEmpty()
    val valid get() = title.isNotBlank() && targetValid && timesValid && intervalValid && daysValid

    fun schedule(): HabitSchedule = when (kind) {
        ScheduleKind.DAILY -> HabitSchedule.Daily
        ScheduleKind.DAYS -> HabitSchedule.SelectedDays(days.map { DayOfWeek.of(it) }.toSet())
        ScheduleKind.WEEKLY -> HabitSchedule.TimesPerWeek(number(timesPerWeek) ?: 3)
        ScheduleKind.INTERVAL -> HabitSchedule.EveryNDays(number(interval) ?: 2)
    }

    fun toHabit() = Habit(
        id = id, title = title.trim(), icon = PlannerIcon.fromKey(icon), color = AccentColor.fromKey(color), schedule = schedule(),
        target = number(target) ?: 1, unit = unit.trim(), reminderTime = reminder?.let { LocalTime.ofSecondOfDay(it.toLong()) },
        startDate = LocalDate.ofEpochDay(startDate), createdAt = Instant.ofEpochMilli(createdAt), archived = archived,
    )

    companion object {
        fun from(h: Habit) = HabitForm(
            id = h.id, title = h.title, icon = h.icon.key, color = h.color.key,
            kind = when (h.schedule) {
                HabitSchedule.Daily -> ScheduleKind.DAILY
                is HabitSchedule.SelectedDays -> ScheduleKind.DAYS
                is HabitSchedule.TimesPerWeek -> ScheduleKind.WEEKLY
                is HabitSchedule.EveryNDays -> ScheduleKind.INTERVAL
            },
            days = (h.schedule as? HabitSchedule.SelectedDays)?.days?.map { it.value }?.toSet() ?: emptySet(),
            timesPerWeek = ((h.schedule as? HabitSchedule.TimesPerWeek)?.times ?: 3).toString(),
            interval = ((h.schedule as? HabitSchedule.EveryNDays)?.interval ?: 2).toString(),
            target = h.target.toString(), unit = h.unit, reminder = h.reminderTime?.toSecondOfDay(),
            startDate = h.startDate.toEpochDay(), createdAt = h.createdAt.toEpochMilli(), archived = h.archived,
        )
    }
}

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val habits: HabitRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<HabitEditorRoute>() }.getOrDefault(HabitEditorRoute())
    private val json = Json { ignoreUnknownKeys = true }
    // Must be read before getStateFlow(), which stores its default value in the handle.
    private val needsLoad = !savedState.contains(KEY_FORM)
    private val today = time.today()
    private var original: HabitForm? = savedState.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString(HabitForm.serializer(), it) }
    val form: StateFlow<HabitForm> = savedState.getStateFlow(KEY_FORM, "")
        .map { if (it.isBlank()) HabitForm(startDate = today.toEpochDay()) else json.decodeFromString(HabitForm.serializer(), it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HabitForm(startDate = today.toEpochDay()))
    private val _saved = MutableSharedFlow<Boolean>(extraBufferCapacity = 2)
    val saved: SharedFlow<Boolean> = _saved
    val isNew get() = route.habitId == NEW_ID
    val isDirty get() = original != null && original != form.value

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (isNew) HabitForm(startDate = today.toEpochDay()) else habits.getHabit(route.habitId)?.let(HabitForm::from) ?: HabitForm(startDate = today.toEpochDay())
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(HabitForm.serializer(), initial)
                savedState[KEY_FORM] = json.encodeToString(HabitForm.serializer(), initial)
            }
        }
    }

    fun update(transform: (HabitForm) -> HabitForm) {
        savedState[KEY_FORM] = json.encodeToString(HabitForm.serializer(), transform(form.value))
    }

    fun save() {
        if (!form.value.valid) return
        viewModelScope.launch {
            runCatchingSafely { habits.save(form.value.toHabit()) }
                .onSuccess {
                    original = form.value
                    _saved.tryEmit(true)
                }
                .onFailure { _saved.tryEmit(false) }
        }
    }

    private companion object {
        const val KEY_FORM = "habit_form"
        const val KEY_ORIGINAL = "habit_form_original"
    }
}
