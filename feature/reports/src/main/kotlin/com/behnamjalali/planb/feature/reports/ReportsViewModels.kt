package com.behnamjalali.planb.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.StatisticsRepository
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.StatsPeriods
import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.PeriodStatistics
import com.behnamjalali.planb.core.model.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** More → Statistics (Plan-B Pro #31). */
@Serializable data object StatisticsRoute

/** The yearly "my year" report; [year] in the user's calendar, null for the current year. */
@Serializable data class YearReportRoute(val year: Int? = null)

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Error : StatsUiState

    /** [isCurrent]: the period contains today (there is no "next" period to show). */
    data class Ready(val period: StatsPeriod, val stats: PeriodStatistics, val isCurrent: Boolean) : StatsUiState
}

/**
 * Loads statistics for the selected period. The period and the offset from the current period
 * are kept in [SavedStateHandle], so they survive process death.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val statistics: StatisticsRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    val period: StatsPeriod get() = savedState.get<String>(KEY_PERIOD)?.let { name -> StatsPeriod.entries.firstOrNull { it.name == name } } ?: StatsPeriod.WEEK
    private var offset: Int
        get() = savedState[KEY_OFFSET] ?: 0
        set(value) { savedState[KEY_OFFSET] = value }

    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = settings.current()
            val engine = CalendarEngines.of(s.calendarSystem)
            val current = StatsPeriods.spanOf(period, time.today(), engine, s.firstDayOfWeek)
            val span = if (offset == 0) current else StatsPeriods.shift(period, current, offset, engine, s.firstDayOfWeek)
            runCatchingSafely { statistics.statistics(span, StatsPeriods.buckets(period, span, engine), s.firstDayOfWeek) }
                .onSuccess { _state.value = StatsUiState.Ready(period, it, offset == 0) }
                .onFailure { _state.value = StatsUiState.Error }
        }
    }

    fun selectPeriod(value: StatsPeriod) {
        if (value == period) return
        savedState[KEY_PERIOD] = value.name
        offset = 0
        refresh()
    }

    fun page(delta: Int) {
        offset = (offset + delta).coerceAtMost(0)
        refresh()
    }

    private companion object {
        const val KEY_PERIOD = "stats_period"
        const val KEY_OFFSET = "stats_offset"
    }
}

sealed interface YearUiState {
    data object Loading : YearUiState
    data object Error : YearUiState
    data class Ready(val year: Int, val stats: PeriodStatistics, val isCurrent: Boolean) : YearUiState
}

/** The "my year" report for one calendar year (Jalali or Gregorian, by the settings). */
@HiltViewModel
class YearReportViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val statistics: StatisticsRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<YearUiState>(YearUiState.Loading)
    val state: StateFlow<YearUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** The requested year, or null for the current one. */
    private var year: Int?
        get() = savedState.get<Int>(KEY_YEAR)?.takeIf { it > 0 }
        set(value) { savedState[KEY_YEAR] = value ?: 0 }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val s = settings.current()
            val engine = CalendarEngines.of(s.calendarSystem)
            val currentYear = engine.toCalendarDate(time.today()).year
            val shown = (year ?: currentYear).coerceAtMost(currentYear)
            val span: DateSpan = StatsPeriods.yearSpan(shown, engine)
            runCatchingSafely { statistics.statistics(span, StatsPeriods.buckets(StatsPeriod.YEAR, span, engine), s.firstDayOfWeek) }
                .onSuccess { _state.value = YearUiState.Ready(shown, it, shown == currentYear) }
                .onFailure { _state.value = YearUiState.Error }
        }
    }

    fun page(delta: Int) {
        val current = (state.value as? YearUiState.Ready)?.year ?: return
        year = current + delta
        refresh()
    }

    private companion object {
        // The navigation argument of YearReportRoute.
        const val KEY_YEAR = "year"
    }
}
