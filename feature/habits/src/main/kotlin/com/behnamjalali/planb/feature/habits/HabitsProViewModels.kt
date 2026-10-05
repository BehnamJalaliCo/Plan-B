package com.behnamjalali.planb.feature.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.AchievementRepository
import com.behnamjalali.planb.core.data.repository.AwardedBadge
import com.behnamjalali.planb.core.data.repository.BadgeStatus
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.StatsPeriods
import com.behnamjalali.planb.core.model.ChallengeProgress
import com.behnamjalali.planb.core.model.ChallengeRules
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitAnalytics
import com.behnamjalali.planb.core.model.HabitAnalyticsResult
import com.behnamjalali.planb.core.model.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Advanced statistics of one habit (Plan-B Pro #28). */
@Serializable
data class HabitStatsRoute(val habitId: Long)

/** Challenges and the badge gallery (Plan-B Pro #29); [badges] opens the gallery tab. */
@Serializable
data class ChallengesRoute(val badges: Boolean = false)

data class HabitStatsUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val habit: Habit? = null,
    val amounts: Map<LocalDate, Int> = emptyMap(),
    val today: LocalDate = LocalDate.MIN,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
    val analytics: HabitAnalyticsResult? = null,
    /** The calendar year the heatmap shows (Jalali or Gregorian number) and its days. */
    val year: Int = 0,
    val yearSpan: DateSpan? = null,
    val yearDone: Int = 0,
    val canGoForward: Boolean = false,
    val canGoBack: Boolean = false,
)

/**
 * Advanced habit statistics (Plan-B Pro #28): streaks, completion rates for this week, month and
 * year and the last 12 weeks and months (in the user's calendar), the weekday pattern, the trend
 * and a heatmap of a whole calendar year (browse back to the year the habit started).
 */
@HiltViewModel
class HabitStatsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    habits: HabitRepository,
    settings: SettingsRepository,
    time: TimeProvider,
) : ViewModel() {
    val habitId = savedState.toRoute<HabitStatsRoute>().habitId
    private val yearOffset = savedState.getStateFlow(KEY_YEAR, 0)

    val state: StateFlow<HabitStatsUi> = combine(habits.observeHabit(habitId), settings.settings, time.todayFlow(), yearOffset) { item, s, today, offset ->
        if (item == null) return@combine HabitStatsUi(loading = false, missing = true)
        val engine = CalendarEngines.of(s.calendarSystem)
        val week = StatsPeriods.spanOf(StatsPeriod.WEEK, today, engine, s.firstDayOfWeek)
        val month = StatsPeriods.spanOf(StatsPeriod.MONTH, today, engine, s.firstDayOfWeek)
        val year = StatsPeriods.spanOf(StatsPeriod.YEAR, today, engine, s.firstDayOfWeek)
        val habit = item.habit
        val shown = if (offset == 0) year else StatsPeriods.shift(StatsPeriod.YEAR, year, -offset, engine, s.firstDayOfWeek)
        HabitStatsUi(
            loading = false,
            habit = habit,
            amounts = item.amounts,
            today = today,
            firstDayOfWeek = s.firstDayOfWeek,
            analytics = HabitAnalytics.analyze(
                habit, item.amounts, today, s.firstDayOfWeek, week, month, year,
                weeks = (WEEKS - 1 downTo 0).map { StatsPeriods.shift(StatsPeriod.WEEK, week, -it, engine, s.firstDayOfWeek) },
                months = (MONTHS - 1 downTo 0).map { StatsPeriods.shift(StatsPeriod.MONTH, month, -it, engine, s.firstDayOfWeek) },
            ),
            year = engine.toCalendarDate(shown.start).year,
            yearSpan = shown,
            yearDone = item.amounts.count { (date, amount) -> date in shown && amount >= habit.target },
            canGoForward = offset > 0,
            canGoBack = shown.start > minOf(habit.startDate, item.amounts.keys.minOrNull() ?: habit.startDate),
        )
    }.catch { emit(HabitStatsUi(loading = false, missing = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitStatsUi())

    /** Shows the year [delta] years later (negative: earlier); never past the current year. */
    fun moveYear(delta: Int) {
        savedState[KEY_YEAR] = (yearOffset.value - delta).coerceAtLeast(0)
    }

    private companion object {
        const val KEY_YEAR = "habit_stats_year_offset"
        const val WEEKS = 12
        const val MONTHS = 12
    }
}

data class ChallengesUi(
    val loading: Boolean = true,
    val active: List<ChallengeProgress> = emptyList(),
    val finished: List<ChallengeProgress> = emptyList(),
    val badges: List<BadgeStatus> = emptyList(),
    /** Habits a new challenge can be started on (active habits without a running challenge). */
    val habits: List<Habit> = emptyList(),
) {
    val earned: Int get() = badges.count { it.earned }
}

sealed interface ChallengesEvent {
    data object Started : ChallengesEvent
    data object Failed : ChallengesEvent
}

/** Challenges and badges (Plan-B Pro #29). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val achievements: AchievementRepository,
    habits: HabitRepository,
    time: TimeProvider,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<ChallengesRoute>() }.getOrDefault(ChallengesRoute())

    /** 0: challenges, 1: badges; kept across rotation. */
    val tab: StateFlow<Int> = savedState.getStateFlow(KEY_TAB, if (route.badges) 1 else 0)

    private val _events = Channel<ChallengesEvent>(Channel.BUFFERED)
    val events: Flow<ChallengesEvent> = _events.receiveAsFlow()

    val state: StateFlow<ChallengesUi> = combine(
        achievements.observeChallenges(),
        achievements.observeBadges(),
        habits.observeHabits(time.today(), time.today()).map { list -> list.map { it.habit } },
    ) { challenges, badges, habitList ->
        val running = challenges.filter { it.challenge.status == ChallengeStatus.ACTIVE && it.status != ChallengeStatus.ABANDONED }
        val busy = running.mapNotNull { it.challenge.habitId }.toSet()
        ChallengesUi(
            loading = false,
            active = running.sortedBy { it.challenge.endDate },
            finished = (challenges - running.toSet()).sortedByDescending { it.challenge.startDate },
            badges = badges,
            habits = habitList.filter { it.id !in busy },
        )
    }.catch { emit(ChallengesUi(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChallengesUi())

    fun selectTab(tab: Int) {
        savedState[KEY_TAB] = tab
    }

    /** Brings statuses and badges up to date; only for Pro users (the screen passes [pro]). */
    fun refresh(pro: Boolean) {
        if (pro) launchSafely { achievements.evaluate() }
    }

    fun start(habitId: EntityId, days: Int) = launchSafely {
        require(days in ChallengeRules.LENGTHS)
        achievements.startChallenge(habitId, days)
        _events.trySend(ChallengesEvent.Started)
    }

    fun abandon(id: EntityId) = launchSafely { achievements.abandon(id) }

    fun delete(id: EntityId) = launchSafely { achievements.delete(id) }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.trySend(ChallengesEvent.Failed) }
    }

    private companion object {
        const val KEY_TAB = "challenges_tab"
    }
}

/**
 * The badge unlock moment (Plan-B Pro #29): badges awarded in the last days that were not
 * celebrated yet, one at a time. Shown by [BadgeCelebrationHost] over any screen.
 */
@HiltViewModel
class BadgeCelebrationViewModel @Inject constructor(private val achievements: AchievementRepository) : ViewModel() {
    val pending: StateFlow<List<AwardedBadge>> = achievements.observeUncelebrated()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Marks [badge] (and every earlier one) as celebrated. */
    fun dismiss(badge: AwardedBadge) {
        viewModelScope.launch { runCatchingSafely { achievements.markCelebrated(badge.id) } }
    }
}
