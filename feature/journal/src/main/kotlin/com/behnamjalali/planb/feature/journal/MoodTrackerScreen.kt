package com.behnamjalali.planb.feature.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.health.HealthConnectPermissions
import com.behnamjalali.planb.core.model.CorrelationStrength
import com.behnamjalali.planb.core.model.DayPart
import com.behnamjalali.planb.core.model.MoodCorrelation
import com.behnamjalali.planb.core.model.MoodEntry
import com.behnamjalali.planb.core.model.MoodFactor
import com.behnamjalali.planb.core.model.MoodInsights
import com.behnamjalali.planb.core.model.MoodTrackerInsights
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.metaSeparator
import java.time.LocalDate

/** What the mood tracker screen does; see [MoodTrackerViewModel]. */
data class MoodTrackerActions(
    val onBack: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onCheckIn: () -> Unit = {},
    val onEdit: (MoodEntry) -> Unit = {},
    val onDelete: (MoodEntry) -> Unit = {},
    val onDraft: ((MoodDraft) -> MoodDraft) -> Unit = {},
    val onSaveDraft: () -> Unit = {},
    val onDismissDraft: () -> Unit = {},
    val onAllowSleep: () -> Unit = {},
)

@Composable
fun MoodTrackerDestination(
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: MoodTrackerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val sleepRequest = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { viewModel.refreshHealth() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MoodEvent.Saved -> snackbarHostState.showSnackbar(resources.getString(R.string.mood_saved))
                is MoodEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(resources.getString(R.string.mood_deleted), resources.getString(R.string.mood_undo), duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.entry)
                }
                MoodEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    MoodTrackerScreen(
        state,
        MoodTrackerActions(
            onBack = onBack,
            onOpenCalendar = onOpenCalendar,
            onCheckIn = { viewModel.openCheckIn() },
            onEdit = viewModel::edit,
            onDelete = viewModel::delete,
            onDraft = viewModel::updateDraft,
            onSaveDraft = viewModel::saveDraft,
            onDismissDraft = viewModel::dismissDraft,
            onAllowSleep = { runCatching { sleepRequest.launch(viewModel.sleepPermissions()) } },
        ),
    )
}

/**
 * The mood and energy tracker (Plan-B Pro #30): quick check-ins (several a day), today's
 * check-ins, the last 30 days as a chart, mood by energy and by time of day, what goes with the
 * mood (habits completed, focus minutes, sleep from Health Connect when allowed) and the
 * history. The journal's own check-ins (#25) are part of it. Without Pro, existing check-ins
 * stay readable under a teaser; new ones need Pro.
 */
@Composable
fun MoodTrackerScreen(state: MoodTrackerUi, actions: MoodTrackerActions) {
    val isPro = LocalProAccess.current.isPro
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.mood_title),
            onBack = actions.onBack,
            actions = { PlannerIconButton(Icons.Rounded.CalendarMonth, stringResource(R.string.mood_open_calendar), actions.onOpenCalendar) },
        )
        if (state.loading) {
            PlannerLoadingState()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!isPro) {
                item(key = "teaser") { ProTeaser(ProFeature.MOOD_TRACKER) }
                if (state.history.isEmpty()) return@LazyColumn
            }
            item(key = "today") { TodayCard(state, isPro, actions) }
            state.insights?.let { insights ->
                item(key = "overview") { OverviewCard(insights) }
                item(key = "trend") { TrendCard(insights, state.today) }
                item(key = "energy") { MoodByCard(insights) }
                item(key = "patterns") { PatternsCard(state, insights, isPro, actions) }
            }
            item(key = "history_h") {
                PlannerSectionHeader(stringResource(R.string.mood_history, PlannerLocals.numbers.format(MoodTrackerViewModel.HISTORY_DAYS.toInt())))
            }
            if (state.history.isEmpty()) {
                item(key = "history_empty") { Text(stringResource(R.string.mood_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.history, key = { "m${it.id}" }) { entry -> EntryRow(entry, state.today, isPro, actions, showDate = true) }
        }
    }
    val draft = state.draft
    if (draft != null && isPro) CheckInSheet(draft, actions)
}

@Composable
private fun TodayCard(state: MoodTrackerUi, isPro: Boolean, actions: MoodTrackerActions) {
    PlannerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mood_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (isPro) PlannerButton(stringResource(R.string.mood_check_in), actions.onCheckIn, icon = Icons.Rounded.Add)
        }
        Spacer(Modifier.height(Spacing.sm))
        val entries = state.todayEntries
        if (entries.isEmpty()) {
            Text(stringResource(R.string.mood_today_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { EntryRow(it, state.today, isPro, actions, showDate = false) }
        }
    }
}

/** One check-in: time (and day), mood and energy with their icons, tags, and a menu. */
@Composable
private fun EntryRow(entry: MoodEntry, today: LocalDate, isPro: Boolean, actions: MoodTrackerActions, showDate: Boolean) {
    val formatter = PlannerLocals.formatter
    var menu by remember { mutableStateOf(false) }
    val mood = entry.mood?.takeIf { it in MoodEntry.RANGE }
    val energy = entry.energy?.takeIf { it in MoodEntry.RANGE }
    val separator = metaSeparator()
    val whenText = listOfNotNull(
        formatter.relativeDate(entry.date, today).takeIf { showDate },
        entry.time?.let(formatter::time),
    ).joinToString(separator)
    val levels = listOfNotNull(
        mood?.let { stringResource(R.string.journal_level_cd, stringResource(R.string.journal_mood), stringResource(moodLabels[it - 1])) },
        energy?.let { stringResource(R.string.journal_level_cd, stringResource(R.string.journal_energy), stringResource(energyLabels[it - 1])) },
    )
    val tags = entry.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }
    val parts = levels + listOfNotNull(tags)
    val description = stringResource(R.string.mood_entry_cd, whenText, parts.joinToString(separator))
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs).semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (mood != null) Icon(moodIcons[mood - 1], contentDescription = null, tint = moodColor(mood), modifier = Modifier.size(28.dp))
        if (energy != null) Icon(energyIcons[energy - 1], contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                levels.joinToString(separator),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(whenText.takeIf { it.isNotEmpty() }, tags).joinToString(separator),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.noteId != null) PlannerPill(stringResource(R.string.mood_from_journal))
        if (isPro) {
            Box {
                PlannerIconButton(Icons.Rounded.MoreVert, stringResource(R.string.mood_entry_menu), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.mood_edit)) }, onClick = { menu = false; actions.onEdit(entry) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mood_delete)) }, onClick = { menu = false; actions.onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(insights: MoodTrackerInsights) {
    val numbers = PlannerLocals.numbers
    val max = numbers.format(MoodEntry.RANGE.last)
    val none = stringResource(R.string.mood_no_data)
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.mood_overview, numbers.format(MoodInsights.WINDOW_DAYS.toInt())),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(pluralStringResource(R.plurals.mood_checkins, insights.checkIns, numbers.format(insights.checkIns)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        insights.averageMood?.let { Text(stringResource(R.string.mood_average, numbers.format(it.toDouble()), max), style = MaterialTheme.typography.bodyMedium) }
        insights.averageEnergy?.let { Text(stringResource(R.string.mood_average_energy, numbers.format(it.toDouble()), max), style = MaterialTheme.typography.bodyMedium) }
        if (insights.lastWeek != null || insights.weekBefore != null) {
            Text(
                stringResource(
                    R.string.mood_week_compare,
                    insights.lastWeek?.let { numbers.format(it.toDouble()) } ?: none,
                    insights.weekBefore?.let { numbers.format(it.toDouble()) } ?: none,
                    numbers.format(7),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Daily average mood and energy of the last 30 days, oldest at the reading start. */
@Composable
private fun TrendCard(insights: MoodTrackerInsights, today: LocalDate) {
    val numbers = PlannerLocals.numbers
    val days = 30L
    val from = today.minusDays(days - 1)
    val points = insights.daily.filter { it.date >= from }
    val moodColor = MaterialTheme.colorScheme.primary
    val energyColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val none = stringResource(R.string.mood_no_data)
    val moods = points.mapNotNull { it.mood }
    val energies = points.mapNotNull { it.energy }
    val description = stringResource(
        R.string.mood_trend_cd,
        moods.takeIf { it.isNotEmpty() }?.let { numbers.format(it.average()) } ?: none,
        energies.takeIf { it.isNotEmpty() }?.let { numbers.format(it.average()) } ?: none,
        numbers.format(points.size),
        numbers.format(days.toInt()),
    )
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mood_trend, numbers.format(days.toInt())), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        Canvas(Modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics { contentDescription = description }) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            fun x(date: LocalDate): Float {
                val f = java.time.temporal.ChronoUnit.DAYS.between(from, date).toFloat() / (days - 1)
                return (if (rtl) 1f - f else f) * size.width
            }
            fun y(value: Float): Float = size.height - (value - 1f) / 4f * size.height * 0.9f - size.height * 0.05f
            (1..5).forEach { level -> drawLine(grid, Offset(0f, y(level.toFloat())), Offset(size.width, y(level.toFloat())), strokeWidth = 1f) }
            listOf(points.mapNotNull { p -> p.mood?.let { p.date to it } } to moodColor, points.mapNotNull { p -> p.energy?.let { p.date to it } } to energyColor).forEach { (series, color) ->
                if (series.isEmpty()) return@forEach
                val path = Path()
                series.forEachIndexed { i, (date, value) ->
                    val point = Offset(x(date), y(value))
                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    drawCircle(color, radius = 3.dp.toPx(), center = point)
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.clearAndSetSemantics {}) {
            LegendDot(stringResource(R.string.mood_legend_mood), moodColor)
            LegendDot(stringResource(R.string.mood_legend_energy), energyColor)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mood by energy level and by time of day, as bars. */
@Composable
private fun MoodByCard(insights: MoodTrackerInsights) {
    val none = stringResource(R.string.mood_no_data)
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mood_vs_energy), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Bars(MoodEntry.RANGE.map { level -> stringResource(energyLabels[level - 1]) to insights.moodByEnergy[level] }, none)
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.mood_by_day_part), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Bars(
            DayPart.entries.map { part ->
                stringResource(
                    when (part) {
                        DayPart.MORNING -> R.string.mood_part_morning
                        DayPart.AFTERNOON -> R.string.mood_part_afternoon
                        DayPart.EVENING -> R.string.mood_part_evening
                        DayPart.NIGHT -> R.string.mood_part_night
                    },
                ) to insights.moodByDayPart[part]
            },
            none,
        )
    }
}

@Composable
private fun Bars(values: List<Pair<String, Float?>>, none: String) {
    val numbers = PlannerLocals.numbers
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        values.forEach { (label, value) ->
            val description = stringResource(R.string.journal_level_cd, label, value?.let { numbers.format(it.toDouble()) } ?: none)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(0.5f).height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(Radius.xs))) {
                    if (value != null) {
                        Box(
                            Modifier.fillMaxWidth(value / MoodEntry.RANGE.last).fillMaxHeight()
                                .background(moodColor((value + 0.5f).toInt().coerceIn(1, 5)), RoundedCornerShape(Radius.xs)),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(value?.let { numbers.format(it.toDouble()) } ?: "–", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternsCard(state: MoodTrackerUi, insights: MoodTrackerInsights, isPro: Boolean, actions: MoodTrackerActions) {
    val numbers = PlannerLocals.numbers
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mood_patterns), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.mood_patterns_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        if (insights.correlations.isEmpty() && insights.habitEffects.isEmpty()) {
            Text(stringResource(R.string.mood_patterns_none, numbers.format(MoodInsights.MIN_DAYS)), style = MaterialTheme.typography.bodyMedium)
        }
        insights.correlations.forEach { CorrelationLine(it) }
        insights.habitEffects.forEach { effect ->
            Text(
                stringResource(R.string.mood_habit_effect, effect.title, numbers.format(effect.doneMood.toDouble()), numbers.format(effect.notDoneMood.toDouble())),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.xxs),
            )
        }
        if (isPro && !state.sleepAccess && state.healthAvailability == HealthAvailability.AVAILABLE) {
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.mood_sleep_add), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.mood_sleep_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlannerButton(stringResource(R.string.mood_sleep_allow), actions.onAllowSleep, style = PlannerButtonStyle.Tonal)
        }
    }
}

@Composable
private fun CorrelationLine(c: MoodCorrelation) {
    val numbers = PlannerLocals.numbers
    val factor = stringResource(
        when (c.factor) {
            MoodFactor.HABITS -> R.string.mood_factor_habits
            MoodFactor.FOCUS -> R.string.mood_factor_focus
            MoodFactor.SLEEP -> R.string.mood_factor_sleep
        },
    )
    val strength = when (c.strength) {
        CorrelationStrength.NONE -> ""
        CorrelationStrength.WEAK -> stringResource(R.string.mood_strength_weak)
        CorrelationStrength.MODERATE -> stringResource(R.string.mood_strength_moderate)
        CorrelationStrength.STRONG -> stringResource(R.string.mood_strength_strong)
    }
    val text = stringResource(
        when {
            c.strength == CorrelationStrength.NONE -> R.string.mood_pattern_none
            c.positive -> R.string.mood_pattern_positive
            else -> R.string.mood_pattern_negative
        },
        factor,
        strength,
        numbers.format(c.days),
    )
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.xxs))
}

/** The quick check-in: mood, energy (either is enough) and optional tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInSheet(draft: MoodDraft, actions: MoodTrackerActions) {
    PlannerBottomSheet(onDismiss = actions.onDismissDraft) {
        Text(
            stringResource(if (draft.editingId == null) R.string.mood_check_in_title else R.string.mood_edit_title),
            style = MaterialTheme.typography.titleLarge,
        )
        LevelPicker(stringResource(R.string.journal_mood), draft.mood, moodIcons, moodLabels, { moodColor(it) }) { v -> actions.onDraft { it.copy(mood = v) } }
        LevelPicker(stringResource(R.string.journal_energy), draft.energy, energyIcons, energyLabels, { MaterialTheme.colorScheme.tertiary }) { v -> actions.onDraft { it.copy(energy = v) } }
        Spacer(Modifier.height(Spacing.sm))
        PlannerTextField(draft.tags, { v -> actions.onDraft { it.copy(tags = v.take(200)) } }, stringResource(R.string.mood_tags))
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(
            stringResource(R.string.mood_save),
            actions.onSaveDraft,
            Modifier.fillMaxWidth(),
            enabled = draft.mood != null || draft.energy != null,
        )
        Spacer(Modifier.height(Spacing.md))
    }
}
