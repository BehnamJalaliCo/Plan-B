package com.behnamjalali.planb.feature.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.speech.VoiceInputButton
import com.behnamjalali.planb.core.speech.appendDictation
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser

@Composable
fun AssistantDestination(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.notesToPick.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val numbers = PlannerLocals.numbers
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AssistantEvent.PlanApplied -> {
                    val result = snackbarHostState.showSnackbar(
                        resources.getString(R.string.assistant_plan_applied, numbers.format(event.count)),
                        actionLabel = resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoPlan()
                }
                AssistantEvent.PlanUndone -> snackbarHostState.showSnackbar(resources.getString(R.string.assistant_plan_undone))
                AssistantEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PlannerTopBar(
            title = stringResource(R.string.assistant_title),
            onBack = onBack,
            actions = {
                if (state.messages.isNotEmpty()) PlannerIconButton(Icons.Rounded.DeleteSweep, stringResource(R.string.assistant_clear), viewModel::clearChat)
                PlannerIconButton(Icons.Rounded.Settings, stringResource(R.string.ai_settings_title), onOpenSettings)
            },
        )
        ProGate(ProFeature.AI_ASSISTANT, teaser = { Column(Modifier.padding(horizontal = Spacing.screen)) { ProTeaser(ProFeature.AI_ASSISTANT) } }) {
            when (state.ready) {
                null -> PlannerLoadingState()
                false -> PlannerEmptyState(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.assistant_setup_title),
                    message = stringResource(R.string.assistant_setup_message),
                    actionLabel = stringResource(R.string.assistant_open_settings),
                    onAction = onOpenSettings,
                )
                true -> AssistantScreen(
                    state = state,
                    notes = notes,
                    actions = AssistantActions(
                        onInput = viewModel::setInput,
                        onSend = viewModel::send,
                        onStop = viewModel::stop,
                        onContext = viewModel::setContext,
                        onPlan = viewModel::plan,
                        onTogglePlan = viewModel::togglePlanItem,
                        onAcceptPlan = viewModel::acceptPlan,
                        onDismissPlan = viewModel::dismissPlan,
                    ),
                    voiceInput = {
                        VoiceInputButton(onResult = { viewModel.setInput(appendDictation(viewModel.state.value.input, it)) }, key = "assistant")
                    },
                )
            }
        }
    }
}

data class AssistantActions(
    val onInput: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onContext: (ContextKind, NoteRef?) -> Unit = { _, _ -> },
    val onPlan: (Boolean) -> Unit = {},
    val onTogglePlan: (EntityId) -> Unit = {},
    val onAcceptPlan: () -> Unit = {},
    val onDismissPlan: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    notes: List<NoteRef>,
    actions: AssistantActions,
    voiceInput: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var pickingNote by rememberSaveable { mutableStateOf(false) }
    var showContext by rememberSaveable { mutableStateOf(false) }
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "plan_actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlannerButton(stringResource(R.string.assistant_plan_day), { actions.onPlan(false) }, style = PlannerButtonStyle.Tonal, icon = Icons.Rounded.Today, enabled = state.plan?.loading != true)
                    PlannerButton(stringResource(R.string.assistant_plan_week), { actions.onPlan(true) }, style = PlannerButtonStyle.Tonal, icon = Icons.Rounded.CalendarViewWeek, enabled = state.plan?.loading != true)
                }
            }
            state.plan?.let { plan -> item(key = "plan") { PlanCard(plan, actions) } }
            item(key = "context") {
                ContextSection(state, expanded = showContext, onExpand = { showContext = !showContext }, onContext = actions.onContext, onPickNote = { pickingNote = true })
            }
            if (state.messages.isEmpty()) {
                item(key = "suggestions") { Suggestions(actions.onInput) }
            }
            items(state.messages, key = { it.id }) { MessageBubble(it) }
        }
        InputBar(state, actions, voiceInput)
    }

    if (pickingNote) {
        NotePicker(notes, state.note, onDismiss = { pickingNote = false }) { note ->
            pickingNote = false
            actions.onContext(ContextKind.NOTE, note)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextSection(state: AssistantUiState, expanded: Boolean, onExpand: () -> Unit, onContext: (ContextKind, NoteRef?) -> Unit, onPickNote: () -> Unit) {
    val numbers = PlannerLocals.numbers
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlannerSectionHeader(stringResource(R.string.assistant_context))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PlannerChip(stringResource(R.string.assistant_context_none), state.context == ContextKind.NONE, { onContext(ContextKind.NONE, null) }, icon = Icons.Rounded.Block)
            PlannerChip(stringResource(R.string.assistant_context_today), state.context == ContextKind.TODAY, { onContext(ContextKind.TODAY, null) }, icon = Icons.Rounded.Today)
            PlannerChip(stringResource(R.string.assistant_context_week), state.context == ContextKind.WEEK, { onContext(ContextKind.WEEK, null) }, icon = Icons.Rounded.CalendarViewWeek)
            PlannerChip(
                state.note?.takeIf { state.context == ContextKind.NOTE }?.title?.ifBlank { null } ?: stringResource(R.string.assistant_context_note),
                state.context == ContextKind.NOTE,
                onPickNote,
                icon = Icons.AutoMirrored.Rounded.Notes,
            )
        }
        if (state.context != ContextKind.NONE) {
            PlannerSurface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.assistant_context_show), onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.assistant_context_sent), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.assistant_sent_size, numbers.format(state.contextText.length), numbers.format(state.contextTokens)) +
                                if (state.truncated) " " + stringResource(R.string.assistant_truncated) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
                if (expanded) {
                    Spacer(Modifier.heightIn(min = Spacing.sm))
                    SelectionContainer {
                        Text(
                            state.contextText.take(PREVIEW_CHARS) + if (state.contextText.length > PREVIEW_CHARS) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (state.host.isNotEmpty()) {
            Text(
                stringResource(R.string.assistant_sent_to, state.host),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onInput: (String) -> Unit) {
    val prompts = listOf(R.string.assistant_suggest_focus, R.string.assistant_suggest_wait, R.string.assistant_suggest_summary).map { stringResource(it) }
    Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.assistant_try), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            prompts.forEach { p -> PlannerChip(p, selected = false, onClick = { onInput(p) }) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.fromUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                when {
                    message.text.isNotEmpty() -> SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge) }
                    message.streaming -> Text(stringResource(R.string.assistant_thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                message.error?.let {
                    Text(stringResource(it.messageRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanPreview, actions: AssistantActions) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val numbers = PlannerLocals.numbers
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(if (plan.week) R.string.assistant_plan_week_title else R.string.assistant_plan_day_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.assistant_plan_preview_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.heightIn(min = Spacing.sm))
        when {
            plan.loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.assistant_planning), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
            }
            plan.error != null -> Text(stringResource(plan.error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            plan.nothingToPlan -> Text(stringResource(R.string.assistant_plan_nothing), style = MaterialTheme.typography.bodyMedium)
            plan.proposals.isEmpty() -> Text(stringResource(R.string.assistant_plan_none_fit), style = MaterialTheme.typography.bodyMedium)
            else -> plan.proposals.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(value = p.task.id in plan.selected, role = Role.Checkbox) { actions.onTogglePlan(p.task.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = p.task.id in plan.selected, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(p.task.title, style = MaterialTheme.typography.bodyLarge)
                        val whenText = (if (plan.week) formatter.shortDate(p.date, today) + " · " else "") + formatter.timeRange(p.start, p.end)
                        Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        p.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        if (!plan.loading && plan.skipped > 0) {
            Text(
                stringResource(R.string.assistant_plan_skipped, numbers.format(plan.skipped)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Spacer(Modifier.heightIn(min = Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (plan.proposals.isNotEmpty() && !plan.loading) {
                PlannerButton(
                    stringResource(R.string.assistant_plan_accept, numbers.format(plan.selected.size)),
                    actions.onAcceptPlan,
                    enabled = plan.selected.isNotEmpty() && !plan.saving,
                )
            }
            PlannerButton(stringResource(R.string.assistant_plan_discard), actions.onDismissPlan, style = PlannerButtonStyle.Text)
        }
    }
}

@Composable
private fun InputBar(state: AssistantUiState, actions: AssistantActions, voiceInput: (@Composable () -> Unit)?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                PlannerTextField(
                    value = state.input,
                    onValueChange = actions.onInput,
                    label = stringResource(R.string.assistant_ask),
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
                    trailingContent = voiceInput,
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            if (state.streaming) {
                PlannerIconButton(Icons.Rounded.Stop, stringResource(R.string.assistant_stop), actions.onStop, tint = MaterialTheme.colorScheme.primary)
            } else {
                PlannerIconButton(
                    Icons.AutoMirrored.Rounded.Send,
                    stringResource(R.string.assistant_send),
                    actions.onSend,
                    enabled = state.input.isNotBlank(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun NotePicker(notes: List<NoteRef>, current: NoteRef?, onDismiss: () -> Unit, onPick: (NoteRef) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(current?.id) }
    PlannerDialog(
        title = stringResource(R.string.assistant_pick_note),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.assistant_use_note),
        onConfirm = { notes.firstOrNull { it.id == chosen }?.let(onPick) },
        confirmEnabled = chosen != null,
    ) {
        if (notes.isEmpty()) {
            Text(stringResource(R.string.assistant_no_notes), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(notes, key = { it.id }) { note ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = chosen == note.id, role = Role.RadioButton) { chosen = note.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == note.id, onClick = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(note.title.ifBlank { stringResource(R.string.assistant_untitled) }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private const val PREVIEW_CHARS = 1_500
