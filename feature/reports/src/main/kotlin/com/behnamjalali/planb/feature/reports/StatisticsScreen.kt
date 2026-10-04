package com.behnamjalali.planb.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerSegmentedControl
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.PeriodStatistics
import com.behnamjalali.planb.core.model.StatsPeriod
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.pdf.rememberPdfExport
import kotlinx.coroutines.launch

@Composable
fun StatisticsDestination(
    onBack: () -> Unit,
    onOpenYear: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    ProGate(ProFeature.REPORTS, teaser = { ReportsTeaser(stringResource(R.string.reports_title), onBack) }) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val owner = LocalLifecycleOwner.current
        // Recomputed whenever the screen resumes, so it always reflects the latest data.
        LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() } }
        val resources = LocalResources.current
        val formatter = PlannerLocals.formatter
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val scope = rememberCoroutineScope()
        val export = rememberPdfExport(
            report = {
                (state as? StatsUiState.Ready)?.let { ReportText.statisticsPdf(resources, it.period, it.stats, formatter, rtl) }
            },
            onResult = { ok ->
                scope.launch { snackbarHostState.showSnackbar(resources.getString(if (ok) R.string.reports_pdf_saved else R.string.reports_pdf_failed)) }
            },
        )
        StatisticsScreen(
            state = state,
            period = viewModel.period,
            onBack = onBack,
            onSelectPeriod = viewModel::selectPeriod,
            onPage = viewModel::page,
            onOpenYear = onOpenYear,
            onExport = {
                (state as? StatsUiState.Ready)?.let {
                    export(resources.getString(R.string.reports_file_name, ReportText.periodLabel(it.period, it.stats.span, formatter)))
                }
            },
        )
    }
}

/** What non-Pro users see: the screen's title and a calm explanation, never a pop-up. */
@Composable
internal fun ReportsTeaser(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title, onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.reports_teaser), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProTeaser(ProFeature.REPORTS)
        }
    }
}

@Composable
fun StatisticsScreen(
    state: StatsUiState,
    period: StatsPeriod,
    onBack: () -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onPage: (Int) -> Unit,
    onOpenYear: () -> Unit,
    onExport: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            stringResource(R.string.reports_title),
            onBack = onBack,
            actions = {
                if (state is StatsUiState.Ready) {
                    PlannerIconButton(Icons.Rounded.PictureAsPdf, stringResource(R.string.reports_export_pdf), onExport)
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "period") {
                PlannerSegmentedControl(
                    options = StatsPeriod.entries,
                    selected = period,
                    onSelect = onSelectPeriod,
                    label = { periodName(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state) {
                StatsUiState.Loading -> item(key = "loading") { PlannerLoadingState() }
                StatsUiState.Error -> item(key = "error") { PlannerErrorState(stringResource(R.string.reports_error)) }
                is StatsUiState.Ready -> {
                    val s = state.stats
                    item(key = "range") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ReportText.periodLabel(state.period, s.span, formatter),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.reports_previous), { onPage(-1) })
                            PlannerIconButton(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                stringResource(R.string.reports_next),
                                { onPage(1) },
                                enabled = !state.isCurrent,
                            )
                        }
                    }
                    statisticsItems(state.period, s, onOpenYear)
                }
            }
        }
    }
}

@Composable
internal fun periodName(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.WEEK -> R.string.reports_period_week
        StatsPeriod.MONTH -> R.string.reports_period_month
        StatsPeriod.YEAR -> R.string.reports_period_year
    },
)

private fun androidx.compose.foundation.lazy.LazyListScope.statisticsItems(period: StatsPeriod, s: PeriodStatistics, onOpenYear: () -> Unit) {
    item(key = "tiles") { SummaryTiles(s) }
    if (s.isEmpty) {
        item(key = "empty") {
            PlannerEmptyState(Icons.Rounded.QueryStats, stringResource(R.string.reports_none_yet), stringResource(R.string.reports_nothing))
        }
    }
    item(key = "completed") {
        val f = PlannerLocals.formatter
        val res = LocalResources.current
        val title = stringResource(R.string.reports_completed_chart)
        PlannerCard(Modifier.fillMaxWidth()) {
            PlannerSectionHeader(title, trailing = f.numbers.format(s.completedTotal))
            BarChart(
                values = s.completedPerBucket,
                labels = s.buckets.map { ReportText.axisLabel(period, it, f) },
                description = ReportText.chartDescription(res, title, s.buckets.map { ReportText.bucketName(period, it, f) }, s.completedPerBucket, f),
                highlight = s.bestBucket,
            )
        }
    }
    item(key = "ontime") {
        val f = PlannerLocals.formatter
        PlannerCard(Modifier.fillMaxWidth()) {
            PlannerSectionHeader(stringResource(R.string.reports_on_time_title))
            if (s.onTime + s.late == 0) {
                Text(stringResource(R.string.reports_no_due), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SplitBar(
                    first = s.onTime,
                    second = s.late,
                    firstLabel = "${stringResource(R.string.reports_on_time)} ${f.numbers.format(s.onTime)}",
                    secondLabel = "${stringResource(R.string.reports_late)} ${f.numbers.format(s.late)}",
                    description = stringResource(R.string.reports_on_time_description, f.numbers.format(s.onTime), f.numbers.format(s.late)),
                )
            }
        }
    }
    item(key = "rhythm") { RhythmCard(s) }
    if (s.focusMinutes > 0) {
        item(key = "focus") {
            val f = PlannerLocals.formatter
            val res = LocalResources.current
            val title = stringResource(R.string.reports_focus_chart)
            PlannerCard(Modifier.fillMaxWidth()) {
                PlannerSectionHeader(title, trailing = f.duration(s.focusMinutes))
                BarChart(
                    values = s.focusMinutesPerBucket,
                    labels = s.buckets.map { ReportText.axisLabel(period, it, f) },
                    description = ReportText.chartDescription(res, title, s.buckets.map { ReportText.bucketName(period, it, f) }, s.focusMinutesPerBucket, f),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    if (s.habits.isNotEmpty()) {
        item(key = "habits_h") {
            PlannerSectionHeader(stringResource(R.string.reports_habits), trailing = s.habitSuccessRate?.let { PlannerLocals.numbers.percent(it) })
        }
        items(s.habits, key = { "h${it.habit.id}" }) { h ->
            val numbers = PlannerLocals.numbers
            val tones = PlanBTheme.colors.accent(h.habit.color)
            Column {
                Text(
                    stringResource(R.string.reports_habit_line, h.habit.title, numbers.format(h.doneDays), numbers.format(h.bestStreak)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerProgressBar(h.rate, Modifier.weight(1f), color = tones.strong, trackColor = tones.container)
                    Text(numbers.percent(h.rate), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
    if (s.projects.isNotEmpty()) {
        item(key = "projects_h") { PlannerSectionHeader(stringResource(R.string.reports_projects)) }
        items(s.projects, key = { "p${it.project.id}" }) { p ->
            val numbers = PlannerLocals.numbers
            val tones = PlanBTheme.colors.accent(p.project.color)
            Column {
                Text(p.project.title, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerProgressBar(p.progress, Modifier.weight(1f), color = tones.strong, trackColor = tones.container)
                    Text(numbers.percent(p.progress), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
    if (s.topTags.isNotEmpty() || s.topProjects.isNotEmpty()) {
        item(key = "top") { TopLists(s) }
    }
    item(key = "year") {
        SettingsRow(
            stringResource(R.string.reports_open_year),
            icon = Icons.Rounded.AutoAwesome,
            subtitle = stringResource(R.string.reports_open_year_sub),
            onClick = onOpenYear,
        )
    }
}

@Composable
private fun SummaryTiles(s: PeriodStatistics) {
    val f = PlannerLocals.formatter
    val res = LocalResources.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tile(stringResource(R.string.reports_completed), f.numbers.format(s.completedTotal), null, Modifier.weight(1f))
            Tile(
                stringResource(R.string.reports_completion_rate),
                ReportText.completionRate(res, s, f),
                if (s.plannedTotal == 0) stringResource(R.string.reports_no_planned)
                else stringResource(R.string.reports_completion_rate_detail, f.numbers.format(s.plannedDone), f.numbers.format(s.plannedTotal)),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Tile(stringResource(R.string.reports_focus), f.duration(s.focusMinutes), stringResource(R.string.reports_focus_sessions, f.numbers.format(s.focusSessions)), Modifier.weight(1f))
            Tile(stringResource(R.string.reports_notes), f.numbers.format(s.notesWritten), null, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun Tile(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    PlannerCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun RhythmCard(s: PeriodStatistics) {
    val f = PlannerLocals.formatter
    val res = LocalResources.current
    val week = ReportText.weekOrder(f)
    val weekValues = week.map { s.completedPerWeekday[it.value - 1] }
    val dayTitle = stringResource(R.string.reports_busiest_day)
    val hourTitle = stringResource(R.string.reports_busiest_hour)
    PlannerCard(Modifier.fillMaxWidth()) {
        PlannerSectionHeader(dayTitle, trailing = ReportText.busiestDay(res, s, f))
        BarChart(
            values = weekValues,
            labels = week.map(f::weekdayNarrow),
            description = ReportText.chartDescription(res, dayTitle, week.map(f::weekdayName), weekValues, f),
            highlight = s.busiestWeekday?.let { week.indexOf(it) },
            height = 88.dp,
        )
        PlannerSectionHeader(hourTitle, trailing = ReportText.busiestHour(res, s, f))
        BarChart(
            values = s.completedPerHour,
            labels = (0 until 24).map { h -> if (h % 6 == 0) f.numbers.format(h) else null },
            description = ReportText.chartDescription(res, hourTitle, (0 until 24).map { ReportText.hourLabel(it, f) }, s.completedPerHour, f),
            highlight = s.busiestHour,
            height = 72.dp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopLists(s: PeriodStatistics, tagsTitle: String = stringResource(R.string.reports_top_tags), projectsTitle: String = stringResource(R.string.reports_top_projects)) {
    val numbers = PlannerLocals.numbers
    PlannerCard(Modifier.fillMaxWidth()) {
        if (s.topTags.isNotEmpty()) {
            PlannerSectionHeader(tagsTitle)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                s.topTags.forEach { t ->
                    val tones = PlanBTheme.colors.accent(t.tag.color)
                    PlannerPill(stringResource(R.string.reports_count_line, t.tag.name, numbers.format(t.count)), container = tones.container, content = tones.onContainer)
                }
            }
        }
        if (s.topProjects.isNotEmpty()) {
            PlannerSectionHeader(projectsTitle)
            s.topProjects.forEach { p ->
                Text(stringResource(R.string.reports_count_line, p.project.title, numbers.format(p.count)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
