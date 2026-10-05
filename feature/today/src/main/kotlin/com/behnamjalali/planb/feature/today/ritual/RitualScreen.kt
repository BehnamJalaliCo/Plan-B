package com.behnamjalali.planb.feature.today.ritual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.RitualState
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.ui.PlannerEventCard
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.feature.today.R
import com.behnamjalali.planb.feature.today.RitualKind
import com.behnamjalali.planb.feature.today.plan.DayPlanSheet
import com.behnamjalali.planb.feature.today.plan.PlanMode

/** Full-screen, calm step flow for the morning or evening ritual (Plan-B Pro #8). */
@Composable
fun RitualDestination(
    onClose: () -> Unit,
    onOpenWorkingHours: () -> Unit,
    onMessage: (String) -> Unit = {},
    viewModel: RitualViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    val title = stringResource(if (viewModel.kind == RitualKind.MORNING) R.string.ritual_morning else R.string.ritual_evening)
    ProGate(
        ProFeature.DAILY_RITUALS,
        teaser = {
            Column(Modifier.fillMaxSize()) {
                PlannerTopBar(title, onBack = onClose)
                ProTeaser(ProFeature.DAILY_RITUALS, Modifier.padding(horizontal = Spacing.screen))
            }
        },
    ) {
        val data by viewModel.data.collectAsStateWithLifecycle()
        val step by viewModel.step.collectAsStateWithLifecycle()
        val text by viewModel.text.collectAsStateWithLifecycle()
        val formatter = PlannerLocals.formatter
        val texts = JournalTexts(
            heading = stringResource(if (viewModel.kind == RitualKind.MORNING) R.string.ritual_intention_heading else R.string.ritual_journal_heading),
            notebook = stringResource(R.string.ritual_journal_notebook),
            pageTitle = formatter.fullDate(data?.date ?: PlannerLocals.today),
        )
        var planning by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    RitualEvent.Finished -> onClose()
                    RitualEvent.Failed -> onMessage(resources.getString(R.string.ritual_failed))
                }
            }
        }
        RitualScreen(
            kind = viewModel.kind,
            title = title,
            steps = viewModel.steps,
            step = step,
            data = data,
            text = text,
            onClose = onClose,
            onBack = viewModel::back,
            onNext = viewModel::next,
            onFinish = { viewModel.finish(texts) },
            onMove = viewModel::move,
            onToggleFocus = viewModel::toggleFocus,
            onTextChange = viewModel::setText,
            onPlanDay = { planning = true },
        )
        if (planning) {
            DayPlanSheet(
                mode = PlanMode.PLAN,
                onDismiss = { planning = false },
                onApplied = {
                    planning = false
                    onMessage(resources.getString(R.string.today_plan_applied))
                },
                onFailed = { onMessage(resources.getString(R.string.ritual_failed)) },
                onOpenSettings = {
                    planning = false
                    onOpenWorkingHours()
                },
            )
        }
    }
}

@Composable
fun RitualScreen(
    kind: RitualKind,
    title: String,
    steps: List<RitualStep>,
    step: Int,
    data: RitualData?,
    text: String,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onMove: (Task, TaskMove) -> Unit,
    onToggleFocus: (EntityId) -> Unit,
    onTextChange: (String) -> Unit,
    onPlanDay: () -> Unit,
) {
    val numbers = PlannerLocals.numbers
    val current = steps[step.coerceIn(0, steps.lastIndex)]
    val last = step >= steps.lastIndex
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.ritual_close), onClose)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ritual_step_of, numbers.format(step + 1), numbers.format(steps.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PlannerProgressBar((step + 1f) / steps.size, Modifier.padding(horizontal = Spacing.screen), height = 6.dp)
        if (data == null) {
            PlannerLoadingState(Modifier.weight(1f))
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                stepContent(current, kind, data, text, onMove, onToggleFocus, onTextChange, onPlanDay)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) PlannerButton(stringResource(R.string.ritual_back), onBack, style = PlannerButtonStyle.Text)
            Spacer(Modifier.weight(1f))
            if (last) {
                PlannerButton(stringResource(R.string.ritual_finish), onFinish, icon = Icons.Rounded.Done, enabled = data != null)
            } else {
                PlannerButton(stringResource(R.string.ritual_next), onNext, icon = Icons.AutoMirrored.Rounded.ArrowForward)
            }
        }
    }
}

private fun LazyListScope.stepContent(
    step: RitualStep,
    kind: RitualKind,
    data: RitualData,
    text: String,
    onMove: (Task, TaskMove) -> Unit,
    onToggleFocus: (EntityId) -> Unit,
    onTextChange: (String) -> Unit,
    onPlanDay: () -> Unit,
) {
    when (step) {
        RitualStep.REVIEW -> {
            intro(R.string.ritual_review_title, R.string.ritual_review_hint)
            if (data.unfinished.isEmpty()) calm(R.string.ritual_review_empty)
            items(data.unfinished, key = { "u_${it.id}" }) { task ->
                MoveCard(task, listOf(TaskMove.TODAY, TaskMove.TOMORROW, TaskMove.DROP), onMove)
            }
        }
        RitualStep.TOP3 -> {
            intro(R.string.ritual_top3_title, R.string.ritual_top3_hint)
            focusList(data.today, data.focus(kind), R.string.ritual_top3_empty, onToggleFocus)
        }
        RitualStep.CALENDAR -> {
            intro(R.string.ritual_calendar_title, null)
            item(key = "free") {
                Text(
                    stringResource(R.string.ritual_calendar_free, PlannerLocals.formatter.duration(data.freeMinutes)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (data.events.isEmpty()) calm(R.string.ritual_calendar_empty)
            items(data.events, key = { "e_${it.event.id}_${it.date}" }) { PlannerEventCard(it.event) }
        }
        RitualStep.PLAN -> {
            intro(R.string.ritual_plan_title, R.string.ritual_plan_hint)
            item(key = "plan") {
                PlannerButton(stringResource(R.string.today_plan_day), onPlanDay, style = PlannerButtonStyle.Tonal, icon = Icons.Rounded.AutoMode)
            }
        }
        RitualStep.INTENTION -> {
            intro(R.string.ritual_intention_title, R.string.ritual_intention_hint)
            item(key = "intention") {
                PlannerTextField(text, onTextChange, stringResource(R.string.ritual_intention_label), singleLine = false, minLines = 2, maxLines = 4)
            }
        }
        RitualStep.DONE -> {
            intro(R.string.ritual_done_title, null)
            item(key = "celebrate") { Celebration(data.completedToday.size) }
            items(data.completedToday, key = { "d_${it.id}" }) { task ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PlanBTheme.colors.success, modifier = Modifier.size(IconSize.sm))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        RitualStep.LEFTOVERS -> {
            intro(R.string.ritual_leftovers_title, R.string.ritual_leftovers_hint)
            if (data.leftovers.isEmpty()) calm(R.string.ritual_leftovers_empty)
            items(data.leftovers, key = { "l_${it.id}" }) { task ->
                MoveCard(task, listOf(TaskMove.TOMORROW, TaskMove.NEXT_WEEK, TaskMove.DROP), onMove)
            }
        }
        RitualStep.JOURNAL -> {
            intro(R.string.ritual_journal_title, R.string.ritual_journal_hint)
            item(key = "journal") {
                PlannerTextField(text, onTextChange, stringResource(R.string.ritual_journal_label), singleLine = false, minLines = 3, maxLines = 6)
            }
        }
        RitualStep.TOMORROW -> {
            intro(R.string.ritual_tomorrow_title, R.string.ritual_top3_hint)
            focusList(data.tomorrow, data.focus(kind), R.string.ritual_tomorrow_empty, onToggleFocus)
        }
    }
}

private fun LazyListScope.intro(title: Int, hint: Int?) {
    item(key = "intro_$title") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (hint != null) {
                Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

private fun LazyListScope.calm(text: Int) {
    item(key = "calm_$text") {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.focusList(tasks: List<Task>, focus: List<EntityId>, empty: Int, onToggle: (EntityId) -> Unit) {
    item(key = "count") {
        val numbers = PlannerLocals.numbers
        Text(
            stringResource(R.string.ritual_top3_selected, numbers.format(focus.size), numbers.format(RitualState.TOP_COUNT)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (tasks.isEmpty()) calm(empty)
    items(tasks, key = { "f_${it.id}" }) { task ->
        val checked = task.id in focus
        val enabled = checked || focus.size < RitualState.TOP_COUNT
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(task.id) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveCard(task: Task, moves: List<TaskMove>, onMove: (Task, TaskMove) -> Unit) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        task.dueDate?.let {
            Text(formatter.relativeDate(it, today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            moves.forEach { move ->
                PlannerChip(
                    label = stringResource(
                        when (move) {
                            TaskMove.TODAY -> R.string.ritual_move_today
                            TaskMove.TOMORROW -> R.string.ritual_move_tomorrow
                            TaskMove.NEXT_WEEK -> R.string.ritual_move_next_week
                            TaskMove.DROP -> R.string.ritual_drop
                        },
                    ),
                    selected = false,
                    onClick = { onMove(task, move) },
                )
            }
        }
    }
}

@Composable
private fun Celebration(count: Int) {
    val tones = PlanBTheme.colors.accent(com.behnamjalali.planb.core.model.AccentColor.MINT)
    PlannerCard(Modifier.fillMaxWidth(), containerColor = tones.container, contentColor = tones.onContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Celebration, contentDescription = null, modifier = Modifier.size(IconSize.lg))
            Spacer(Modifier.width(Spacing.md))
            Text(
                if (count == 0) {
                    stringResource(R.string.ritual_done_empty)
                } else {
                    pluralStringResource(R.plurals.ritual_done_count, count, PlannerLocals.numbers.format(count))
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
