package com.behnamjalali.planb.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.JournalRepository
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.JournalInsights
import com.behnamjalali.planb.core.model.MoodCalendar
import com.behnamjalali.planb.core.model.MoodDay
import com.behnamjalali.planb.core.model.MoodEntry
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class MoodCalendarUi(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    /** Any date of the month shown. */
    val month: LocalDate = LocalDate.MIN,
    val days: Map<LocalDate, MoodDay> = emptyMap(),
    val insights: JournalInsights? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoodCalendarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    journal: JournalRepository,
    time: TimeProvider,
) : ViewModel() {
    private val today = time.today()
    private val month = savedState.getStateFlow(KEY_MONTH, today.toEpochDay())

    /** The grid shows up to six weeks around the month; the insights cover the last 90 days. */
    val state: StateFlow<MoodCalendarUi> = month.flatMapLatest { epochDay ->
        val anchor = LocalDate.ofEpochDay(epochDay)
        val from = minOf(anchor.minusDays(GRID_MARGIN), today.minusDays(INSIGHT_DAYS))
        val to = maxOf(anchor.plusDays(GRID_MARGIN), today)
        combine(journal.observeMoods(from, to), journal.observePageDates(from, to)) { moods, dates ->
            val recentMoods = moods.filter { it.date > today.minusDays(INSIGHT_DAYS) && it.date <= today }
            MoodCalendarUi(
                loading = false,
                today = today,
                month = anchor,
                days = MoodCalendar.days(moods, dates),
                insights = MoodCalendar.insights(recentMoods, dates.filter { it <= today }.toSet(), today),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoodCalendarUi())

    /** Shows the month of [to]. */
    fun move(to: LocalDate) {
        savedState[KEY_MONTH] = to.toEpochDay()
    }

    private companion object {
        const val KEY_MONTH = "mood_calendar_month"
        const val GRID_MARGIN = 45L
        const val INSIGHT_DAYS = 90L
    }
}

@Composable
fun MoodCalendarDestination(onBack: () -> Unit, viewModel: MoodCalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MoodCalendarScreen(state, onBack, viewModel::move)
}

/**
 * The mood calendar (Plan-B Pro #25): the month in the user's calendar (Jalali or Gregorian),
 * each day colored by its average mood, a dot for days with a journal page, and simple
 * insights for the last 90 days: average mood per weekday, averages and streaks.
 */
@Composable
fun MoodCalendarScreen(state: MoodCalendarUi, onBack: () -> Unit, onMonth: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title = stringResource(R.string.journal_calendar), onBack = onBack)
        ProGate(ProFeature.JOURNAL, teaser = { ProTeaser(ProFeature.JOURNAL, Modifier.padding(horizontal = Spacing.screen)) }) {
            if (state.loading) {
                PlannerLoadingState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item(key = "month") { MonthGridCard(state, onMonth) }
                    item(key = "legend") { Legend() }
                    item(key = "insights") { InsightsCard(state.insights) }
                }
            }
        }
    }
}

@Composable
private fun MonthGridCard(state: MoodCalendarUi, onMonth: (LocalDate) -> Unit) {
    val formatter = PlannerLocals.formatter
    val engine = formatter.engine
    val month = formatter.monthOf(state.month)
    val weeks = MonthGrid.build(engine, month, formatter.firstDayOfWeek)
    PlannerCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.calendar_previous), { onMonth(engine.firstDayOfMonth(month.plus(-1))) })
            Text(
                formatter.monthYear(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.calendar_next), { onMonth(engine.firstDayOfMonth(month.plus(1))) })
        }
        Row(Modifier.fillMaxWidth()) {
            formatter.weekdays().forEach { day ->
                Text(
                    formatter.weekdayNarrow(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {},
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell -> DayCell(cell, state.days[cell.date], state.today, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(cell: MonthGrid.Cell, day: MoodDay?, today: LocalDate, modifier: Modifier) {
    val formatter = PlannerLocals.formatter
    val level = day?.moodLevel
    val fill = level?.let { moodColor(it) }
    val dateText = formatter.fullDate(cell.date)
    val description = when {
        level != null -> stringResource(R.string.calendar_day_mood_cd, dateText, stringResource(moodLabels[level - 1]))
        day?.journaled == true -> stringResource(R.string.calendar_day_journal_cd, dateText)
        else -> stringResource(R.string.calendar_day_empty_cd, dateText)
    }
    Column(
        modifier.padding(Spacing.xxs).semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(if (cell.date == today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .background(
                    color = fill?.copy(alpha = if (cell.inMonth) 0.85f else 0.3f) ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (cell.inMonth) 1f else 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                formatter.dayNumber(cell.date),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    fill != null -> MaterialTheme.colorScheme.surface
                    cell.inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(5.dp)
                .background(if (day?.journaled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f), CircleShape),
        )
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.calendar_legend), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        moodIcons.forEachIndexed { i, icon ->
            val label = stringResource(moodLabels[i])
            Icon(icon, contentDescription = label, tint = moodColor(i + 1), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun InsightsCard(insights: JournalInsights?) {
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    PlannerCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.insights_title, numbers.format(90)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (insights == null || (insights.averageMood == null && insights.pages == 0)) {
            Text(stringResource(R.string.insights_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@PlannerCard
        }
        val max = MoodEntry.RANGE.last
        insights.averageMood?.let { Text(stringResource(R.string.insights_average_mood, numbers.format(it.toDouble()), numbers.format(max)), style = MaterialTheme.typography.bodyMedium) }
        insights.averageEnergy?.let { Text(stringResource(R.string.insights_average_energy, numbers.format(it.toDouble()), numbers.format(max)), style = MaterialTheme.typography.bodyMedium) }
        Text(pluralStringResource(R.plurals.insights_streak, insights.streak, numbers.format(insights.streak)), style = MaterialTheme.typography.bodyMedium)
        Text(pluralStringResource(R.plurals.insights_longest, insights.longestStreak, numbers.format(insights.longestStreak)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.insights_pages, numbers.format(insights.pages), numbers.format(insights.checkIns)), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.insights_weekday), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().height(120.dp).padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            formatter.weekdays().forEach { day: DayOfWeek ->
                val value = insights.moodByWeekday[day]
                val description = stringResource(
                    R.string.insights_weekday_cd,
                    formatter.weekdayName(day),
                    value?.let { numbers.format(it.toDouble()) } ?: stringResource(R.string.insights_no_data),
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraction = (value ?: 0f) / max
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction.coerceIn(0.04f, 1f) * 0.8f)
                            .background(value?.let { moodColor((it + 0.5f).toInt().coerceIn(1, 5)) } ?: MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(Radius.xs)),
                    )
                    Text(
                        formatter.weekdayNarrow(day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
