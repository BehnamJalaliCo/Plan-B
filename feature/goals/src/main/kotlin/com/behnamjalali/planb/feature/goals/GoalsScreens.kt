package com.behnamjalali.planb.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.AnimatedTaskCheckbox
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.GoalPace
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.DiscardChangesDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerGoalCard
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.ui.rememberOnce
import java.time.LocalDate

@Composable
fun GoalsDestination(onBack: () -> Unit, onOpenGoal: (EntityId) -> Unit, onNewGoal: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalsScreen(state, onBack, onOpenGoal, onNewGoal, viewModel::toggleArchived)
}

@Composable
fun GoalsScreen(state: GoalsUiState, onBack: (() -> Unit)?, onOpenGoal: (EntityId) -> Unit, onNewGoal: () -> Unit, onToggleArchived: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            stringResource(R.string.goals_title),
            onBack = onBack,
            actions = {
                PlannerIconButton(Icons.Rounded.Archive, stringResource(R.string.goals_archived), onToggleArchived,
                    tint = if (state.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.goals_new), onNewGoal)
            },
        )
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.goals_error))
            state.goals.isEmpty() -> PlannerEmptyState(Icons.Rounded.Flag, stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty),
                actionLabel = stringResource(R.string.goals_new), onAction = onNewGoal)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.goals, key = { it.id }) { goal -> PlannerGoalCard(goal, Modifier.animateItem(), onClick = { onOpenGoal(goal.id) }) }
            }
        }
    }
}

@Composable
fun paceLabel(status: GoalPace.Status): String = stringResource(
    when (status) {
        GoalPace.Status.DONE -> R.string.goal_pace_done
        GoalPace.Status.AHEAD -> R.string.goal_pace_ahead
        GoalPace.Status.ON_TRACK -> R.string.goal_pace_on_track
        GoalPace.Status.BEHIND -> R.string.goal_pace_behind
        GoalPace.Status.OVERDUE -> R.string.goal_pace_overdue
    },
)

/** Actual progress bar with a marker at the expected progress for today. */
@Composable
private fun PaceBar(progress: Float, expected: Float, description: String) {
    val fill = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.tertiaryContainer
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .semantics { contentDescription = description },
    ) {
        val barHeight = 10.dp.toPx()
        val top = (size.height - barHeight) / 2
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun x(fraction: Float) = if (rtl) size.width * (1 - fraction) else size.width * fraction
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), CornerRadius(barHeight / 2))
        val w = size.width * progress.coerceIn(0f, 1f)
        drawRoundRect(fill, Offset(if (rtl) size.width - w else 0f, top), Size(w, barHeight), CornerRadius(barHeight / 2))
        val mx = x(expected.coerceIn(0f, 1f))
        drawLine(marker, Offset(mx, 0f), Offset(mx, size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun GoalDetailDestination(onBack: () -> Unit, onEdit: (EntityId) -> Unit, snackbarHostState: SnackbarHostState, viewModel: GoalDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    // Deleting emits Deleted and also makes the goal missing; leave the screen only once.
    val leaveOnce = rememberOnce(onBack)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                GoalEvent.Deleted -> leaveOnce()
                GoalEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) leaveOnce() }
    GoalDetailScreen(state, onBack, { onEdit(viewModel.goalId) }, viewModel)
}

@Composable
fun GoalDetailScreen(state: GoalDetailUiState, onBack: () -> Unit, onEdit: () -> Unit, viewModel: GoalDetailViewModel?) {
    val goal = state.goal
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var milestone by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            goal?.title.orEmpty(),
            onBack = onBack,
            actions = {
                Box {
                    PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.goal_edit)) }, onClick = { menu = false; onEdit() })
                        val archived = goal?.archived == true
                        DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.goal_restore else R.string.goal_archive)) }, onClick = { menu = false; viewModel?.setArchived(!archived) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.goal_delete)) }, onClick = { menu = false; dialog = "delete" })
                    }
                }
            },
        )
        if (state.loading || goal == null) {
            PlannerLoadingState()
            return@Column
        }
        val numbers = PlannerLocals.numbers
        val formatter = PlannerLocals.formatter
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlannerHeroSurface(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerProgressRing(goal.progress, size = 92.dp, strokeWidth = 10.dp, color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(numbers.percent(goal.progress), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.padding(Spacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text("${numbers.format(goal.currentValue)} / ${numbers.format(goal.target)} ${goal.unit}".trim(), style = MaterialTheme.typography.titleLarge)
                        state.pace?.let { pace ->
                            val color = when (pace.status) {
                                GoalPace.Status.BEHIND, GoalPace.Status.OVERDUE -> MaterialTheme.colorScheme.error
                                else -> PlanBTheme.colors.success
                            }
                            Text(paceLabel(pace.status), style = MaterialTheme.typography.labelLarge, color = color)
                            if (pace.daysLeft >= 0) Text(pluralStringResource(R.plurals.goal_days_left, pace.daysLeft.toInt(), numbers.format(pace.daysLeft)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                state.pace?.let { pace ->
                    Spacer(Modifier.height(Spacing.md))
                    val expectedText = stringResource(R.string.goal_expected, numbers.percent(pace.expectedProgress))
                    PaceBar(goal.progress, pace.expectedProgress, numbers.percent(goal.progress) + metaSeparator() + expectedText)
                    Text(expectedText, style = MaterialTheme.typography.bodySmall)
                    if (pace.neededPerWeek > 0) {
                        Text(stringResource(R.string.goal_needed_per_week, numbers.format(pace.neededPerWeek), goal.unit), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val step = if (goal.target >= 50) (goal.target / 20).let { kotlin.math.round(it) }.coerceAtLeast(1.0) else 1.0
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerButton(stringResource(R.string.goal_decrement, numbers.format(step)), { viewModel?.adjustProgress(-step) },
                    style = PlannerButtonStyle.Outlined, icon = Icons.Rounded.Remove, enabled = goal.currentValue > 0)
                PlannerButton(stringResource(R.string.goal_increment, numbers.format(step)), { viewModel?.adjustProgress(step) }, icon = Icons.Rounded.Add)
            }
            PlannerButton(stringResource(R.string.goal_update_progress), { dialog = "progress" }, style = PlannerButtonStyle.Tonal)
            if (goal.description.isNotBlank()) Text(goal.description, style = MaterialTheme.typography.bodyLarge)
            goal.deadline?.let { EditorRow(Icons.Rounded.Event, stringResource(R.string.goal_editor_deadline), formatter.mediumDate(it), {}) }
            state.project?.let { EditorRow(Icons.Rounded.Folder, stringResource(R.string.goal_linked_project), it.title, {}) }
            PlannerSectionHeader(stringResource(R.string.goal_milestones))
            state.milestones.forEach { m ->
                PlannerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedTaskCheckbox(m.completed, { viewModel?.toggleMilestone(m) }, contentDescription = m.title)
                        Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.goal_milestone_delete), { viewModel?.deleteMilestone(m.id) })
                    }
                }
            }
            PlannerTextField(milestone, { milestone = it }, stringResource(R.string.goal_milestone_hint), imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { viewModel?.addMilestone(milestone); milestone = "" }))
            if (goal.notes.isNotBlank()) {
                PlannerSectionHeader(stringResource(R.string.goal_notes))
                Text(goal.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    when (dialog) {
        "delete" -> ConfirmDeleteDialog(stringResource(R.string.goal_delete), stringResource(R.string.goal_delete_confirm), { dialog = null }, {
            dialog = null
            viewModel?.delete()
        })
        "progress" -> if (goal != null) {
            var text by rememberSaveable { mutableStateOf(GoalNumbers.format(goal.currentValue)) }
            val value = GoalNumbers.parse(text)
            PlannerDialog(
                title = stringResource(R.string.goal_update_progress),
                onDismiss = { dialog = null },
                confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
                confirmEnabled = value != null,
                onConfirm = {
                    value?.let { viewModel?.setProgress(it) }
                    dialog = null
                },
            ) {
                PlannerTextField(text, { text = it }, stringResource(R.string.goal_current_value), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
    }
}

@Composable
fun GoalEditorDestination(onClose: () -> Unit, viewModel: GoalEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var discard by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val formatter = PlannerLocals.formatter
    LaunchedEffect(viewModel) {
        viewModel.saved.collect { ok -> if (ok) onClose() else snackbar.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic)) }
    }
    val requestClose = { if (viewModel.isDirty) discard = true else onClose() }
    BackHandler(onBack = requestClose)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { PlannerTopBar(stringResource(if (viewModel.isNew) R.string.goal_editor_new else R.string.goal_editor_edit), onBack = requestClose) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                PlannerButton(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save), viewModel::save, Modifier.fillMaxWidth(), enabled = form.valid)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PlannerTextField(form.title, { v -> viewModel.update { it.copy(title = v) } }, stringResource(R.string.goal_editor_title))
            PlannerTextField(form.description, { v -> viewModel.update { it.copy(description = v) } }, stringResource(R.string.goal_editor_description), singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerTextField(form.target, { v -> viewModel.update { it.copy(target = v.take(12)) } }, stringResource(R.string.goal_editor_target),
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = form.target.isNotBlank() && form.targetValue == null)
                PlannerTextField(form.current, { v -> viewModel.update { it.copy(current = v.take(12)) } }, stringResource(R.string.goal_editor_current),
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = form.currentValue == null)
            }
            if (form.target.isNotBlank() && form.targetValue == null) {
                Text(stringResource(R.string.goal_editor_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            PlannerTextField(form.unit, { v -> viewModel.update { it.copy(unit = v) } }, stringResource(R.string.goal_editor_unit))
            EditorRow(Icons.Rounded.Event, stringResource(R.string.goal_editor_deadline),
                form.deadline?.let { formatter.mediumDate(LocalDate.ofEpochDay(it)) } ?: stringResource(com.behnamjalali.planb.core.ui.R.string.ui_not_set), { picking = true })
            Box {
                EditorRow(Icons.Rounded.Folder, stringResource(R.string.goal_linked_project),
                    projects.firstOrNull { it.id == form.projectId }?.title ?: stringResource(R.string.goal_no_project), { projectMenu = true })
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.goal_no_project)) }, onClick = { viewModel.update { it.copy(projectId = null) }; projectMenu = false })
                    projects.forEach { p ->
                        DropdownMenuItem(text = { Text(p.title) }, onClick = { viewModel.update { it.copy(projectId = p.id) }; projectMenu = false })
                    }
                }
            }
            PlannerTextField(form.notes, { v -> viewModel.update { it.copy(notes = v) } }, stringResource(R.string.goal_notes), singleLine = false, minLines = 3)
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (picking) {
        PlannerDatePickerDialog(form.deadline?.let(LocalDate::ofEpochDay), { picking = false }, { d ->
            viewModel.update { it.copy(deadline = d?.toEpochDay()) }
            picking = false
        })
    }
    if (discard) DiscardChangesDialog({ discard = false }, { discard = false; onClose() })
}
