package com.behnamjalali.planb.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.PeriodStatistics
import com.behnamjalali.planb.core.model.StatsPeriod
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.pdf.rememberPdfExport
import kotlinx.coroutines.launch

@Composable
fun YearReportDestination(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: YearReportViewModel = hiltViewModel(),
) {
    ProGate(ProFeature.REPORTS, teaser = { ReportsTeaser(stringResource(R.string.year_title), onBack) }) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() } }
        val resources = LocalResources.current
        val formatter = PlannerLocals.formatter
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val scope = rememberCoroutineScope()
        val export = rememberPdfExport(
            report = { (state as? YearUiState.Ready)?.let { ReportText.yearPdf(resources, it.year, it.stats, formatter, rtl) } },
            onResult = { ok ->
                scope.launch { snackbarHostState.showSnackbar(resources.getString(if (ok) R.string.reports_pdf_saved else R.string.reports_pdf_failed)) }
            },
        )
        YearReportScreen(
            state = state,
            onBack = onBack,
            onPage = viewModel::page,
            onExport = { (state as? YearUiState.Ready)?.let { export(resources.getString(R.string.year_file_name, formatter.numbers.format(it.year))) } },
        )
    }
}

/** "My year": a scrollable, story-like summary of one calendar year. */
@Composable
fun YearReportScreen(state: YearUiState, onBack: () -> Unit, onPage: (Int) -> Unit, onExport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            stringResource(R.string.year_title),
            onBack = onBack,
            actions = {
                if (state is YearUiState.Ready) {
                    PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.year_previous), { onPage(-1) })
                    PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.year_next), { onPage(1) }, enabled = !state.isCurrent)
                    PlannerIconButton(Icons.Rounded.PictureAsPdf, stringResource(R.string.reports_export_pdf), onExport)
                }
            },
        )
        when (state) {
            YearUiState.Loading -> PlannerLoadingState()
            YearUiState.Error -> PlannerErrorState(stringResource(R.string.reports_error))
            is YearUiState.Ready -> YearStory(state.year, state.stats)
        }
    }
}

@Composable
private fun YearStory(year: Int, s: PeriodStatistics) {
    val f = PlannerLocals.formatter
    val numbers = f.numbers
    val res = LocalResources.current
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "hero") {
            PlannerHeroSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.year_hero, numbers.format(year)),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.year_hero_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(numbers.format(s.completedTotal), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.year_tasks), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.year_active_days, numbers.format(s.activeDays)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (s.isEmpty) {
            item(key = "empty") { Text(stringResource(R.string.year_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        s.bestBucket?.let { best ->
            item(key = "best") {
                StoryCard(
                    Icons.Rounded.CalendarMonth,
                    stringResource(R.string.year_best_month),
                    stringResource(R.string.year_best_month_value, ReportText.bucketName(StatsPeriod.YEAR, s.buckets[best], f), numbers.format(s.completedPerBucket[best])),
                )
            }
        }
        if (s.longestStreak > 0) {
            item(key = "streak") {
                StoryCard(Icons.Rounded.LocalFireDepartment, stringResource(R.string.year_streak), stringResource(R.string.year_streak_value, numbers.format(s.longestStreak)))
            }
        }
        item(key = "months") {
            val title = stringResource(R.string.year_months)
            PlannerCard(Modifier.fillMaxWidth()) {
                PlannerSectionHeader(title)
                BarChart(
                    values = s.completedPerBucket,
                    labels = s.buckets.map { ReportText.axisLabel(StatsPeriod.YEAR, it, f) },
                    description = ReportText.chartDescription(res, title, s.buckets.map { ReportText.bucketName(StatsPeriod.YEAR, it, f) }, s.completedPerBucket, f),
                    highlight = s.bestBucket,
                )
            }
        }
        if (s.onTime + s.late > 0) {
            item(key = "ontime") {
                StoryCard(
                    Icons.Rounded.AutoStories,
                    stringResource(R.string.reports_on_time_title),
                    stringResource(R.string.year_on_time, numbers.percent(s.onTime.toFloat() / (s.onTime + s.late))),
                )
            }
        }
        if (s.focusMinutes > 0) {
            item(key = "focus") {
                StoryCard(Icons.Rounded.Timer, stringResource(R.string.year_focus), f.duration(s.focusMinutes))
            }
        }
        s.habits.firstOrNull()?.let { best ->
            item(key = "habits") {
                StoryCard(
                    Icons.Rounded.Repeat,
                    stringResource(R.string.year_habits),
                    stringResource(R.string.year_habit_best, best.habit.title, numbers.percent(best.rate)),
                )
            }
        }
        if (s.notesWritten > 0) {
            item(key = "notes") { StoryCard(Icons.Rounded.EditNote, stringResource(R.string.year_notes), numbers.format(s.notesWritten)) }
        }
        if (s.topTags.isNotEmpty() || s.topProjects.isNotEmpty()) {
            item(key = "top") { TopLists(s, stringResource(R.string.year_tags), stringResource(R.string.year_projects)) }
        }
        item(key = "closing") {
            Text(
                stringResource(R.string.year_closing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StoryCard(icon: ImageVector, title: String, value: String) {
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
