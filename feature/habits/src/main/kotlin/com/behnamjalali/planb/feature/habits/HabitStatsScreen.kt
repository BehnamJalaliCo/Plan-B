package com.behnamjalali.planb.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.datetime.CalendarMonth
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.HabitAnalytics
import com.behnamjalali.planb.core.model.HabitPeriodRate
import com.behnamjalali.planb.core.model.Streak
import com.behnamjalali.planb.core.model.TrendDirection
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.rememberOnce
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HabitStatsDestination(onBack: () -> Unit, viewModel: HabitStatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leave = rememberOnce(onBack)
    LaunchedEffect(state.missing) { if (state.missing) leave() }
    HabitStatsScreen(state, onBack, viewModel::moveYear)
}

/**
 * Advanced habit statistics (Plan-B Pro #28): streaks and totals, rates for this week, month and
 * year, the last 12 weeks and months, the weekday pattern, the 30-day trend and the whole year
 * as a heatmap of months in the user's calendar. Charts follow the reading direction and have
 * spoken summaries. Free users see a teaser; the basic numbers stay on the habit screen.
 */
@Composable
fun HabitStatsScreen(state: HabitStatsUi, onBack: () -> Unit, onMoveYear: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title = state.habit?.title ?: stringResource(R.string.habit_stats_title), onBack = onBack)
        ProGate(ProFeature.HABIT_STATS, teaser = { ProTeaser(ProFeature.HABIT_STATS, Modifier.padding(horizontal = Spacing.screen)) }) {
            val analytics = state.analytics
            if (state.loading || analytics == null || state.habit == null) {
                PlannerLoadingState()
                return@ProGate
            }
            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "summary") { SummaryTiles(state) }
                item(key = "rates") { RatesCard(analytics.week, analytics.month, analytics.year) }
                item(key = "trend") { TrendCard(state) }
                item(key = "weeks") { BarsCard(stringResource(R.string.habit_stats_weeks, PlannerLocals.numbers.format(analytics.weeks.size)), analytics.weeks, weekly = true) }
                item(key = "months") { BarsCard(stringResource(R.string.habit_stats_months, PlannerLocals.numbers.format(analytics.months.size)), analytics.months, weekly = false) }
                item(key = "weekdays") { WeekdaysCard(state) }
                item(key = "year") { YearCard(state, onMoveYear) }
            }
        }
    }
}

@Composable
private fun streakValue(streak: Streak): String {
    val n = PlannerLocals.numbers.format(streak.count)
    return pluralStringResource(if (streak.unit == Streak.Unit.WEEKS) R.plurals.habit_stats_weeks_count else R.plurals.habit_stats_days, streak.count, n)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryTiles(state: HabitStatsUi) {
    val a = state.analytics ?: return
    val numbers = PlannerLocals.numbers
    // Two per row; at large font sizes they wrap instead of squeezing.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md), maxItemsInEachRow = 2) {
        val tile = Modifier.weight(1f)
        Tile(stringResource(R.string.habit_stats_current), streakValue(a.currentStreak), tile)
        Tile(stringResource(R.string.habit_stats_best), streakValue(a.bestStreak), tile)
        Tile(stringResource(R.string.habit_stats_total), numbers.format(a.totalDoneDays), tile)
        Tile(stringResource(R.string.habit_stats_overall), a.overall?.let(numbers::percent) ?: stringResource(R.string.habit_stats_no_rate), tile)
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    PlannerCard(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatesCard(week: HabitPeriodRate, month: HabitPeriodRate, year: HabitPeriodRate) {
    val numbers = PlannerLocals.numbers
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.habit_stats_rates), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(R.string.habit_stats_week to week, R.string.habit_stats_month to month, R.string.habit_stats_year to year).forEach { (label, rate) ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rate.rate?.let(numbers::percent) ?: stringResource(R.string.habit_stats_no_rate), style = MaterialTheme.typography.titleLarge)
                    Text(
                        pluralStringResource(R.plurals.habit_stats_days_done, rate.doneDays, numbers.format(rate.doneDays)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(state: HabitStatsUi) {
    val numbers = PlannerLocals.numbers
    val trend = state.analytics?.trend
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.habit_stats_trend), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        if (trend == null) {
            Text(stringResource(R.string.habit_stats_trend_none, numbers.format(2 * HabitAnalytics.TREND_DAYS.toInt())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@PlannerCard
        }
        val points = numbers.format((abs(trend.change) * 100).roundToInt())
        val days = numbers.format(HabitAnalytics.TREND_DAYS.toInt())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                when (trend.direction) {
                    TrendDirection.UP -> Icons.AutoMirrored.Rounded.TrendingUp
                    TrendDirection.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
                    TrendDirection.STEADY -> Icons.AutoMirrored.Rounded.TrendingFlat
                },
                contentDescription = null,
                tint = when (trend.direction) {
                    TrendDirection.UP -> PlanBTheme.colors.success
                    TrendDirection.DOWN -> PlanBTheme.colors.warning
                    TrendDirection.STEADY -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                when (trend.direction) {
                    TrendDirection.UP -> stringResource(R.string.habit_stats_trend_up, points, numbers.percent(trend.recent), numbers.percent(trend.previous), days)
                    TrendDirection.DOWN -> stringResource(R.string.habit_stats_trend_down, points, numbers.percent(trend.recent), numbers.percent(trend.previous), days)
                    TrendDirection.STEADY -> stringResource(R.string.habit_stats_trend_steady, numbers.percent(trend.recent), numbers.percent(trend.previous), days)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Bars of completion rates, oldest at the reading start; a period with nothing due is an empty slot. */
@Composable
private fun BarsCard(title: String, rates: List<HabitPeriodRate>, weekly: Boolean) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val none = stringResource(R.string.habit_stats_no_rate)
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.Bottom) {
            rates.forEach { period ->
                val label = if (weekly) {
                    stringResource(R.string.habit_stats_week_of, formatter.dayMonth(period.span.start))
                } else {
                    formatter.monthYear(formatter.monthOf(period.span.start))
                }
                val description = stringResource(R.string.habit_stats_bar_cd, label, period.rate?.let(numbers::percent) ?: none)
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraction = period.rate ?: 0f
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight((fraction * 0.85f).coerceAtLeast(0.03f))
                            .background(
                                if (period.rate == null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(Radius.xs),
                            ),
                    )
                    Text(
                        if (weekly) formatter.dayNumber(period.span.start) else numbers.format(formatter.engine.toCalendarDate(period.span.start).month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdaysCard(state: HabitStatsUi) {
    val a = state.analytics ?: return
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val none = stringResource(R.string.habit_stats_no_rate)
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.habit_stats_weekdays), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        a.bestWeekday?.let { Text(stringResource(R.string.habit_stats_best_day, formatter.weekdayName(it)), style = MaterialTheme.typography.bodyMedium) }
        a.worstWeekday?.takeIf { it != a.bestWeekday }?.let {
            Text(stringResource(R.string.habit_stats_worst_day, formatter.weekdayName(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(110.dp).padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
            formatter.weekdays().forEach { day: DayOfWeek ->
                val value = a.weekdayRates[day]
                val description = stringResource(R.string.habit_stats_bar_cd, formatter.weekdayName(day), value?.let(numbers::percent) ?: none)
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(((value ?: 0f) * 0.8f).coerceAtLeast(0.03f))
                            .background(
                                when {
                                    value == null -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    day == a.bestWeekday -> PlanBTheme.colors.success
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                                RoundedCornerShape(Radius.xs),
                            ),
                    )
                    Text(formatter.weekdayNarrow(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
}

/** The calendar year as twelve small months (Jalali or Gregorian), each day shaded by how much was done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearCard(state: HabitStatsUi, onMoveYear: (Int) -> Unit) {
    val span = state.yearSpan ?: return
    val habit = state.habit ?: return
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val engine = formatter.engine
    val first = engine.monthOf(span.start)
    val tones = PlanBTheme.colors.accent(habit.color)
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.habit_stats_year_previous), { onMoveYear(-1) }, enabled = state.canGoBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.habit_stats_year_title, numbers.format(state.year)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    pluralStringResource(R.plurals.habit_stats_days_done, state.yearDone, numbers.format(state.yearDone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.habit_stats_year_next), { onMoveYear(1) }, enabled = state.canGoForward)
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            maxItemsInEachRow = 3,
        ) {
            (0 until 12).forEach { i ->
                MiniMonth(first.plus(i), state, tones.strong, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.clearAndSetSemantics {}) {
            Text(stringResource(R.string.habit_stats_legend_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0f, 0.34f, 0.67f, 1f).forEach { level -> Box(Modifier.size(10.dp).background(shade(level, tones.strong), RoundedCornerShape(2.dp))) }
            Text(stringResource(R.string.habit_stats_legend_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun shade(level: Float, strong: Color): Color =
    if (level <= 0f) MaterialTheme.colorScheme.surfaceContainerHighest else strong.copy(alpha = 0.3f + 0.7f * level.coerceIn(0f, 1f))

@Composable
private fun MiniMonth(month: CalendarMonth, state: HabitStatsUi, strong: Color, modifier: Modifier) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val habit = state.habit ?: return
    val engine = formatter.engine
    val weeks = MonthGrid.build(engine, month, formatter.firstDayOfWeek)
    val days = engine.monthLength(month.year, month.month)
    val start = engine.firstDayOfMonth(month)
    val done = (0 until days).count { (state.amounts[start.plusDays(it.toLong())] ?: 0) >= habit.target }
    val description = stringResource(R.string.habit_stats_month_cd, formatter.monthYear(month), numbers.format(done), numbers.format(days))
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(
            formatter.monthName(month.month),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Spacer(Modifier.height(2.dp))
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                week.forEach { cell ->
                    val level = if (!cell.inMonth || cell.date > state.today || cell.date < habit.startDate) null else ((state.amounts[cell.date] ?: 0).toFloat() / habit.target).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(0.5.dp)
                            .background(
                                when {
                                    !cell.inMonth -> Color.Transparent
                                    level == null -> MaterialTheme.colorScheme.surfaceContainer
                                    else -> shade(level, strong)
                                },
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(1.5.dp))
        }
    }
}
