package com.behnamjalali.planb.feature.today.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.feature.today.R

/** The "Plan my day" / "Replan" preview (Plan-B Pro #5). Opened only by the user's tap. */
@Composable
fun DayPlanSheet(
    mode: PlanMode,
    onDismiss: () -> Unit,
    onApplied: (Int) -> Unit,
    onFailed: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DayPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, mode) { viewModel.load(mode) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DayPlanEvent.Applied -> onApplied(event.count)
                DayPlanEvent.Failed -> onFailed()
            }
        }
    }
    PlannerBottomSheet(onDismiss = onDismiss) {
        DayPlanContent(state, onToggle = viewModel::toggle, onAccept = viewModel::accept, onCancel = onDismiss, onOpenSettings = onOpenSettings)
    }
}

@Composable
fun DayPlanContent(
    state: DayPlanUiState,
    onToggle: (EntityId) -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val s = state.settings
    Text(
        stringResource(if (state.mode == PlanMode.PLAN) R.string.plan_title else R.string.plan_replan_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                R.string.plan_hours,
                formatter.timeRange(s.workStart, s.workEnd),
                if (s.bufferMinutes == 0) stringResource(R.string.plan_no_buffer) else formatter.duration(s.bufferMinutes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PlannerButton(stringResource(R.string.plan_change_hours), onOpenSettings, style = PlannerButtonStyle.Text)
    }
    if (state.loading) {
        PlannerLoadingState(Modifier.fillMaxWidth().height(160.dp))
        return
    }
    Column(
        Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when {
            state.nothingToDo -> Message(stringResource(if (state.mode == PlanMode.PLAN) R.string.plan_empty else R.string.plan_replan_empty))
            state.proposals.isEmpty() -> Message(stringResource(R.string.plan_no_room))
            else -> {
                Message(stringResource(R.string.plan_preview_hint))
                state.proposals.forEach { p ->
                    ProposalRow(p, checked = p.task.id in state.selected, onToggle = { onToggle(p.task.id) })
                }
            }
        }
        if (state.notFit.isNotEmpty()) LeftOut(stringResource(R.string.plan_not_fit_title), state.notFit)
        if (state.blocked.isNotEmpty()) LeftOut(stringResource(R.string.plan_blocked_title), state.blocked)
    }
    Spacer(Modifier.height(Spacing.lg))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        PlannerButton(stringResource(R.string.plan_cancel), onCancel, style = PlannerButtonStyle.Text)
        if (state.proposals.isNotEmpty()) {
            PlannerButton(
                stringResource(R.string.plan_accept, PlannerLocals.numbers.format(state.selected.size)),
                onAccept,
                enabled = state.selected.isNotEmpty() && !state.saving,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
}

@Composable
private fun ProposalRow(proposal: Proposal, checked: Boolean, onToggle: () -> Unit) {
    val formatter = PlannerLocals.formatter
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(proposal.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                formatter.timeRange(proposal.start, proposal.end),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (proposal.moved) PlannerPill(stringResource(R.string.plan_moved))
    }
}

@Composable
private fun LeftOut(title: String, tasks: List<Task>) {
    Spacer(Modifier.height(Spacing.sm))
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    tasks.forEach { task ->
        Text(
            task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xxs),
        )
    }
}
