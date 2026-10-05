package com.behnamjalali.planb.feature.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.AchievementRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.HabitWithHistory
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.data.wellbeing.HealthHabitSync
import com.behnamjalali.planb.core.model.ChallengeProgress
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.HealthHabits
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.HealthReading
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
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
    /** Plan-B Pro #27: habits linked to Health Connect are checked off when the list opens. */
    private val healthSync: HealthHabitSync,
) : ViewModel() {
    private val archived = savedState.getStateFlow(KEY_ARCHIVED, false)
    private val _failed = Channel<Unit>(Channel.BUFFERED)
    val failed: Flow<Unit> = _failed.receiveAsFlow()

    init {
        // Only for Pro users with linked habits and access; throttled.
        viewModelScope.launch { runCatchingSafely { healthSync.sync() } }
    }

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
                .onFailure { _failed.trySend(Unit) }
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
    /** The habit's running challenge (Plan-B Pro #29). */
    val challenge: ChallengeProgress? = null,
    /** Today's amount from Health Connect (Plan-B Pro #27), when linked and allowed. */
    val health: HealthReading? = null,
)

enum class HabitEvent { Deleted, Failed, ChallengeStarted }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val habits: HabitRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
    private val achievements: AchievementRepository,
    private val healthSync: HealthHabitSync,
) : ViewModel() {
    val habitId = savedState.toRoute<HabitDetailRoute>().habitId
    private val _events = Channel<HabitEvent>(Channel.BUFFERED)
    val events: Flow<HabitEvent> = _events.receiveAsFlow()

    /** Bumped to read Health Connect again ("Sync now"). */
    private val healthRefresh = MutableStateFlow(0)

    /** The habit's challenge that is still running (or failed and not given up yet). */
    private val challenge = achievements.observeChallenges().map { list ->
        list.firstOrNull { it.challenge.habitId == habitId && it.challenge.status == ChallengeStatus.ACTIVE }
    }

    private val health = combine(habits.observeHabit(habitId).map { it?.habit }, healthRefresh) { habit, _ -> habit }.flatMapLatest { habit ->
        flow { emit(habit?.takeIf { it.healthMetric != null }?.let { runCatchingSafely { healthSync.today(it) }.getOrNull() }) }
    }

    val uiState: StateFlow<HabitDetailUiState> = combine(habits.observeHabit(habitId), time.todayFlow(), settings.settings, challenge, health) { item, today, s, running, reading ->
        if (item == null) {
            HabitDetailUiState(loading = false, missing = true)
        } else {
            HabitDetailUiState(
                loading = false,
                item = item,
                today = today,
                streak = HabitStats.currentStreak(item.habit, item.amounts, today, s.firstDayOfWeek),
                bestStreak = HabitStats.bestStreakDays(item.habit, item.amounts, today, s.firstDayOfWeek),
                rate30 = HabitStats.completionRate(item.habit, item.amounts, maxOf(item.habit.startDate, today.minusDays(29)), today, today),
                firstDayOfWeek = s.firstDayOfWeek,
                challenge = running,
                health = reading,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState())

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.trySend(HabitEvent.Failed) }
    }

    /** Starts a [days]-day challenge on this habit today (Plan-B Pro #29). */
    fun startChallenge(days: Int) = launchSafely {
        achievements.startChallenge(habitId, days)
        _events.trySend(HabitEvent.ChallengeStarted)
    }

    /** Reads Health Connect now and checks off days that reached the goal (Plan-B Pro #27). */
    fun syncHealth() = launchSafely {
        healthSync.sync(force = true)
        healthRefresh.value++
    }

    fun adjust(delta: Int) = launchSafely { habits.checkIn(habitId, time.today(), delta) }
    fun setArchived(archived: Boolean) = launchSafely { habits.setArchived(habitId, archived) }
    fun delete() = launchSafely {
        habits.delete(habitId)
        _events.trySend(HabitEvent.Deleted)
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
    /** Plan-B Pro #27: the Health Connect metric ([HealthMetric] name) that checks the habit off. */
    val healthMetric: String? = null,
    /** The metric's daily goal as typed, in [HealthUnits] display units. */
    val healthThreshold: String = "",
) {
    private fun number(text: String): Int? = Digits.toLatin(text.trim()).toIntOrNull()?.takeIf { it in 1..999 }
    val metric: HealthMetric? get() = HealthMetric.fromKey(healthMetric)
    val thresholdValue: Long? get() = metric?.let { HealthUnits.parse(it, healthThreshold) }
    val thresholdValid: Boolean get() = metric.let { m -> m == null || thresholdValue?.let { it in HealthHabits.range(m) } == true }
    val targetValid get() = number(target) != null
    val timesValid get() = kind != ScheduleKind.WEEKLY || number(timesPerWeek)?.let { it in 1..7 } == true
    val intervalValid get() = kind != ScheduleKind.INTERVAL || number(interval) != null
    val daysValid get() = kind != ScheduleKind.DAYS || days.isNotEmpty()
    val valid get() = title.isNotBlank() && targetValid && timesValid && intervalValid && daysValid && thresholdValid

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
        healthMetric = metric, healthThreshold = metric?.let { thresholdValue },
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
            healthMetric = h.healthMetric?.name,
            healthThreshold = h.healthMetric?.let { m -> h.healthThreshold?.let { HealthUnits.format(m, it) } }.orEmpty(),
        )
    }
}

/** Health Connect as the habit editor shows it (Plan-B Pro #27). */
data class HealthEditorState(val availability: HealthAvailability = HealthAvailability.UNSUPPORTED, val granted: Boolean = false)

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val habits: HabitRepository,
    time: TimeProvider,
    private val healthSync: HealthHabitSync,
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
    private val _saved = Channel<Boolean>(Channel.BUFFERED)
    val saved: Flow<Boolean> = _saved.receiveAsFlow()

    private val _health = MutableStateFlow(HealthEditorState(healthSync.availability()))
    val health: StateFlow<HealthEditorState> = _health

    /** The Health Connect permissions the chosen metric needs (for the permission request). */
    fun healthPermissions(): Set<String> = currentForm().metric?.let(healthSync::permissionsFor).orEmpty()

    /** Re-reads availability and access (after the permission screen, or coming back from Health Connect). */
    fun refreshHealth() {
        viewModelScope.launch {
            val metric = currentForm().metric
            _health.value = HealthEditorState(
                availability = healthSync.availability(),
                granted = metric != null && runCatchingSafely { healthSync.hasPermission(metric) }.getOrDefault(false),
            )
        }
    }

    /** Links the habit to [metric] (null unlinks), with that metric's usual goal when none is typed. */
    fun setHealthMetric(metric: HealthMetric?) {
        update { form ->
            val keep = form.metric == metric && form.healthThreshold.isNotBlank()
            form.copy(
                healthMetric = metric?.name,
                healthThreshold = when {
                    metric == null -> ""
                    keep -> form.healthThreshold
                    else -> HealthUnits.format(metric, HealthHabits.defaultThreshold(metric))
                },
            )
        }
        refreshHealth()
    }
    val isNew get() = route.habitId == NEW_ID
    val isDirty get() = original != null && original != form.value

    init {
        if (needsLoad) {
            viewModelScope.launch {
                val initial = if (isNew) HabitForm(startDate = today.toEpochDay()) else habits.getHabit(route.habitId)?.let(HabitForm::from) ?: HabitForm(startDate = today.toEpochDay())
                original = initial
                savedState[KEY_ORIGINAL] = json.encodeToString(HabitForm.serializer(), initial)
                savedState[KEY_FORM] = json.encodeToString(HabitForm.serializer(), initial)
                refreshHealth()
            }
        } else {
            refreshHealth()
        }
    }

    /** Applies [transform] to the latest form (the saved state, not the [form] flow, which may lag a quick second edit). */
    fun update(transform: (HabitForm) -> HabitForm) {
        savedState[KEY_FORM] = json.encodeToString(HabitForm.serializer(), transform(currentForm()))
    }

    private fun currentForm(): HabitForm =
        savedState.get<String>(KEY_FORM)?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(HabitForm.serializer(), it) } ?: form.value

    fun save() {
        if (!form.value.valid) return
        viewModelScope.launch {
            runCatchingSafely { habits.save(form.value.toHabit()) }
                .onSuccess {
                    original = form.value
                    _saved.trySend(true)
                }
                .onFailure { _saved.trySend(false) }
        }
    }

    private companion object {
        const val KEY_FORM = "habit_form"
        const val KEY_ORIGINAL = "habit_form_original"
    }
}

/**
 * Health Connect goals in the units people think in (Plan-B Pro #27): steps, hours of sleep,
 * millilitres of water, minutes of exercise and kilometres; stored in the metric's base unit.
 */
object HealthUnits {
    fun format(metric: HealthMetric, value: Long): String = when (metric) {
        HealthMetric.SLEEP_MINUTES -> decimal(value / 60.0)
        HealthMetric.DISTANCE_METERS -> decimal(value / 1000.0)
        else -> value.toString()
    }

    /** Parses typed text (Persian or Latin digits, "." or the Persian decimal separator). */
    fun parse(metric: HealthMetric, text: String): Long? {
        val clean = Digits.toLatin(text.trim()).replace('\u066B', '.').replace('/', '.').replace(',', '.')
        val number = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        return when (metric) {
            HealthMetric.SLEEP_MINUTES -> Math.round(number * 60)
            HealthMetric.DISTANCE_METERS -> Math.round(number * 1000)
            else -> if (number % 1.0 == 0.0) number.toLong() else null
        }
    }

    private fun decimal(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
