package com.behnamjalali.planb.feature.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import android.widget.Toast
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.pdf.rememberPdfExport
import com.behnamjalali.planb.core.ui.rememberProGuard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.ReviewRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.WeeklyReview
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTaskCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ReviewRoute

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data class Ready(val review: WeeklyReview, val isCurrentWeek: Boolean) : ReviewUiState
    data object Error : ReviewUiState
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val reviews: ReviewRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** Week offset from the current week (0 = this week). */
    private var offset: Int
        get() = savedState[KEY] ?: 0
        set(value) { savedState[KEY] = value }

    fun refresh() = viewModelScope.launch {
        val firstDay = settings.current().firstDayOfWeek
        val currentStart = MonthGrid.weekStart(time.today(), firstDay)
        val start = currentStart.plusWeeks(offset.toLong())
        runCatchingSafely { reviews.weeklyReview(start) }
            .onSuccess { _state.value = ReviewUiState.Ready(it, offset == 0) }
            .onFailure { _state.value = ReviewUiState.Error }
    }

    /** Completes or reopens a listed task (as everywhere else) and recomputes the review. */
    fun setTaskCompleted(id: Long, completed: Boolean) = viewModelScope.launch {
        runCatchingSafely { tasks.setCompleted(id, completed) }
        refresh()
    }

    fun page(delta: Int) {
        offset = (offset + delta).coerceAtMost(1)
        refresh()
    }

    private companion object { const val KEY = "review_week_offset" }
}

@Composable
fun ReviewDestination(onBack: () -> Unit, onOpenTask: (Long) -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // Recomputed whenever the screen resumes so it reflects the latest data.
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() } }
    // PDF export of the review (Plan-B Pro #31); saved where the user chooses (no permission).
    val context = LocalContext.current
    val resources = LocalResources.current
    val formatter = PlannerLocals.formatter
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val guard = rememberProGuard()
    val export = rememberPdfExport(
        report = { (state as? ReviewUiState.Ready)?.let { ReviewPdf.build(resources, it.review, formatter, rtl) } },
        onResult = { ok -> Toast.makeText(context, if (ok) R.string.review_pdf_saved else R.string.review_pdf_failed, Toast.LENGTH_SHORT).show() },
    )
    ReviewScreen(state, onBack, viewModel::page, onOpenTask, viewModel::setTaskCompleted) {
        (state as? ReviewUiState.Ready)?.let { ready ->
            guard.run(ProFeature.REPORTS) { export(resources.getString(R.string.review_file_name, ReviewPdf.weekRange(ready.review, formatter))) }
        }
    }
}

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onBack: () -> Unit,
    onPage: (Int) -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit = { _, _ -> },
    onExportPdf: () -> Unit = {},
) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val today = PlannerLocals.today
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.review_title), onBack = onBack)
        when (state) {
            ReviewUiState.Loading -> PlannerLoadingState()
            ReviewUiState.Error -> PlannerErrorState(stringResource(R.string.review_error))
            is ReviewUiState.Ready -> {
                val r = state.review
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item(key = "week") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${formatter.shortDate(r.weekStart, today)} – ${formatter.shortDate(r.weekEnd, today)}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.review_previous_week), { onPage(-1) })
                            PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.review_next_week), { onPage(1) })
                        }
                    }
                    item(key = "summary") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Stat(stringResource(R.string.review_completed), numbers.format(r.completedTasks), Modifier.weight(1f))
                            Stat(stringResource(R.string.review_focus), formatter.duration(r.focusMinutes), Modifier.weight(1f))
                            Stat(stringResource(R.string.review_notes), numbers.format(r.notesCreated), Modifier.weight(1f))
                        }
                    }
                    item(key = "chart") {
                        PlannerCard(Modifier.fillMaxWidth()) {
                            PlannerSectionHeader(stringResource(R.string.review_completed))
                            val days = (0L until 7L).map { r.weekStart.plusDays(it) }
                            val description = days.zip(r.completedPerDay).joinToString(", ") { (d, c) -> "${formatter.weekdayShort(d.dayOfWeek)} ${numbers.format(c)}" }
                            DayBars(r.completedPerDay, stringResource(R.string.review_completed_chart, description))
                            Row(Modifier.fillMaxWidth()) {
                                days.zip(r.completedPerDay).forEach { (d, c) ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(numbers.format(c), style = MaterialTheme.typography.labelMedium)
                                        Text(formatter.weekdayNarrow(d.dayOfWeek), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "missed_h") { PlannerSectionHeader(stringResource(R.string.review_missed), trailing = numbers.format(r.missedTasks.size)) }
                    if (r.missedTasks.isEmpty()) item(key = "missed_e") { Text(stringResource(R.string.review_missed_none)) }
                    items(r.missedTasks.take(10), key = { "m${it.id}" }) { t -> PlannerTaskCard(t, { done -> onToggleTask(t.id, done) }, onClick = { onOpenTask(t.id) }) }
                    item(key = "habits_h") {
                        PlannerSectionHeader(stringResource(R.string.review_habits), trailing = if (r.habits.isEmpty()) null else numbers.percent(r.habitCompletionRate))
                    }
                    if (r.habits.isEmpty()) item(key = "habits_e") { Text(stringResource(R.string.review_habits_none)) }
                    items(r.habits, key = { "h${it.habit.id}" }) { h ->
                        val tones = PlanBTheme.colors.accent(h.habit.color)
                        Column {
                            Text(stringResource(R.string.review_habit_rate, h.habit.title, numbers.format(h.doneDays)), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlannerProgressBar(h.rate, Modifier.weight(1f), color = tones.strong, trackColor = tones.container)
                                Text(numbers.percent(h.rate), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                    }
                    item(key = "projects_h") { PlannerSectionHeader(stringResource(R.string.review_projects)) }
                    if (r.projects.isEmpty()) item(key = "projects_e") { Text(stringResource(R.string.review_projects_none)) }
                    items(r.projects, key = { "p${it.project.id}" }) { p ->
                        val tones = PlanBTheme.colors.accent(p.project.color)
                        Column {
                            Text(p.project.title, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlannerProgressBar(p.progress, Modifier.weight(1f), color = tones.strong, trackColor = tones.container)
                                Text(numbers.percent(p.progress), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                    }
                    item(key = "goals_h") { PlannerSectionHeader(stringResource(R.string.review_goals)) }
                    if (r.goals.isEmpty()) item(key = "goals_e") { Text(stringResource(R.string.review_goals_none)) }
                    items(r.goals, key = { "g${it.id}" }) { g ->
                        Column {
                            Text(g.title, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlannerProgressBar(g.progress, Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                                Text(numbers.percent(g.progress), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.sm))
                            }
                        }
                    }
                    item(key = "next_h") { PlannerSectionHeader(stringResource(R.string.review_next)) }
                    if (r.nextWeekPriorities.isEmpty()) item(key = "next_e") { Text(stringResource(R.string.review_next_none)) }
                    items(r.nextWeekPriorities, key = { "n${it.id}" }) { t -> PlannerTaskCard(t, { done -> onToggleTask(t.id, done) }, onClick = { onOpenTask(t.id) }) }
                    item(key = "export") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            PlannerButton(stringResource(R.string.review_export_pdf), onExportPdf, style = PlannerButtonStyle.Tonal, icon = Icons.Rounded.PictureAsPdf)
                            if (!LocalProAccess.current.isPro) ProBadge()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    PlannerCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Seven bars; values are also printed below, so meaning never depends on color. */
@Composable
private fun DayBars(values: List<Int>, description: String) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
        val slot = size.width / values.size
        val barWidth = slot * 0.5f
        val rtl = layoutDirection == LayoutDirection.Rtl
        values.forEachIndexed { i, v ->
            val index = if (rtl) values.lastIndex - i else i
            val left = index * slot + (slot - barWidth) / 2
            drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2))
            val h = size.height * v / max
            if (h > 0) drawRoundRect(color, Offset(left, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2))
        }
    }
}
