package com.behnamjalali.planb.feature.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.ai.AssistantLanguage
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerSurface
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.AssistantAction
import com.behnamjalali.planb.core.ui.AssistantOutcome
import com.behnamjalali.planb.core.ui.AssistantRequest
import com.behnamjalali.planb.core.ui.AssistantSource
import com.behnamjalali.planb.core.ui.PlannerLocals
import kotlinx.coroutines.launch

/** Hilt entry for [AiActionContent]: the sheet the editors open through `LocalAssistant`. */
@Composable
fun AiActionSheet(
    request: AssistantRequest,
    onDismiss: () -> Unit,
    onApply: (AssistantOutcome) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AiActionViewModel = hiltViewModel(key = "ai_action_${request.source}"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    // Translation goes to the other app language.
    val persian = LocalConfiguration.current.locales[0].language == "fa"
    val translateTo = if (persian) AssistantLanguage.ENGLISH else AssistantLanguage.PERSIAN
    PlannerBottomSheet(onDismiss = {
        viewModel.reset()
        onDismiss()
    }) {
        AiActionContent(
            request = request,
            state = state.copy(ready = ready),
            translateTo = translateTo,
            onRun = { viewModel.run(it, request, if (it == AssistantAction.TRANSLATE) translateTo else null) },
            onStop = viewModel::stop,
            onBack = viewModel::reset,
            onToggle = viewModel::toggle,
            onApply = { outcome ->
                viewModel.reset()
                onApply(outcome)
                onDismiss()
            },
            onCreateTasks = { viewModel.createTasks(request.projectId) },
            onUndoTasks = viewModel::undoCreatedTasks,
            selectedItems = viewModel::selectedItems,
            onOpenSettings = {
                onDismiss()
                onOpenSettings()
            },
        )
    }
}

/** The sheet's content, free of Hilt so it can be previewed and captured. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiActionContent(
    request: AssistantRequest,
    state: AiActionState,
    translateTo: AssistantLanguage,
    onRun: (AssistantAction) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onToggle: (Int) -> Unit,
    onApply: (AssistantOutcome) -> Unit,
    onCreateTasks: () -> Unit,
    onUndoTasks: () -> Unit,
    selectedItems: () -> List<String>,
    onOpenSettings: () -> Unit,
) {
    val numbers = PlannerLocals.numbers
    val action = state.action
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (action != null) PlannerIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.assistant_back_to_actions), onBack)
        Text(
            if (action == null) stringResource(com.behnamjalali.planb.core.ui.R.string.ui_ai_assistant) else actionLabel(action, translateTo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
    val what = stringResource(
        when {
            request.isSelection -> R.string.assistant_sends_selection
            request.source == AssistantSource.TASK -> R.string.assistant_sends_task
            else -> R.string.assistant_sends_note
        },
    )
    Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.md))

    if (state.ready == false) {
        Text(stringResource(R.string.assistant_setup_needed), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(stringResource(R.string.assistant_open_settings), onOpenSettings, icon = Icons.Rounded.Settings)
        return
    }

    if (action == null) {
        request.actions.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = Role.Button) { onRun(a) }
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(actionIcon(a), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(actionLabel(a, translateTo), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    Text(
        stringResource(
            R.string.assistant_sent_size,
            numbers.format(state.sentChars),
            numbers.format(state.sentTokens),
        ) + if (state.truncated) " " + stringResource(R.string.assistant_truncated) else "",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.sm))

    if (state.running) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.sm))
    }

    val error = state.error
    if (error != null) {
        Text(stringResource(error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(stringResource(R.string.assistant_try_again), { onRun(action) }, style = PlannerButtonStyle.Tonal)
        return
    }

    if (!action.isList) {
        if (state.output.isNotEmpty() || state.running) {
            PlannerSurface(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                SelectionContainer {
                    Text(
                        state.output.ifEmpty { stringResource(R.string.assistant_thinking) },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (state.running) {
            PlannerButton(stringResource(R.string.assistant_stop), onStop, style = PlannerButtonStyle.Outlined, icon = Icons.Rounded.Stop)
            return
        }
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val replaceable = request.isSelection && (action == AssistantAction.REWRITE || action == AssistantAction.TRANSLATE)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (replaceable) PlannerButton(stringResource(R.string.assistant_replace), { onApply(AssistantOutcome.ReplaceSelection(state.output)) })
            PlannerButton(
                stringResource(if (request.isSelection) R.string.assistant_insert_below else R.string.assistant_insert_end),
                { onApply(AssistantOutcome.InsertText(state.output)) },
                style = if (replaceable) PlannerButtonStyle.Tonal else PlannerButtonStyle.Primary,
            )
            PlannerButton(
                stringResource(R.string.assistant_copy),
                { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("", state.output))) } },
                style = PlannerButtonStyle.Text,
                icon = Icons.Rounded.ContentCopy,
            )
        }
        return
    }

    if (state.running) {
        Text(stringResource(R.string.assistant_thinking), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(stringResource(R.string.assistant_stop), onStop, style = PlannerButtonStyle.Outlined, icon = Icons.Rounded.Stop)
        return
    }
    if (state.items.isEmpty()) {
        Text(stringResource(R.string.assistant_nothing_found), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(stringResource(R.string.assistant_try_again), { onRun(action) }, style = PlannerButtonStyle.Tonal)
        return
    }
    if (action == AssistantAction.SUGGEST_TITLES) {
        var chosen by remember(state.items) { mutableStateOf<Int?>(null) }
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            state.items.forEachIndexed { i, title ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = chosen == i, role = Role.RadioButton) { chosen = i },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == i, onClick = null)
                    Spacer(Modifier.padding(start = Spacing.sm))
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PlannerButton(
            stringResource(R.string.assistant_use_title),
            { chosen?.let { onApply(AssistantOutcome.SetTitle(state.items[it])) } },
            enabled = chosen != null,
            icon = Icons.Rounded.Title,
        )
        return
    }

    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        state.items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = i in state.selected, role = Role.Checkbox) { onToggle(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = i in state.selected, onCheckedChange = null)
                Spacer(Modifier.padding(start = Spacing.sm))
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Spacer(Modifier.height(Spacing.md))
    val count = numbers.format(state.selected.size)
    when (action) {
        AssistantAction.BREAK_DOWN -> PlannerButton(
            stringResource(R.string.assistant_add_subtasks, count),
            { onApply(AssistantOutcome.AddSubtasks(selectedItems())) },
            enabled = state.selected.isNotEmpty(),
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
        )
        else -> {
            if (state.createdTasks.isNotEmpty()) {
                Text(stringResource(R.string.assistant_tasks_added, numbers.format(state.createdTasks.size)), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
                PlannerButton(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_undo), onUndoTasks, style = PlannerButtonStyle.Outlined)
                return
            }
            if (state.tasksUndone) {
                Text(stringResource(R.string.assistant_tasks_removed), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
            }
            if (state.saveFailed) {
                Text(stringResource(com.behnamjalali.planb.core.ui.R.string.ui_error_generic), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(Spacing.sm))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerButton(
                    stringResource(R.string.assistant_add_tasks, count),
                    onCreateTasks,
                    enabled = state.selected.isNotEmpty(),
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                )
                if (request.source == AssistantSource.NOTE) {
                    PlannerButton(
                        stringResource(R.string.assistant_add_checklist),
                        { onApply(AssistantOutcome.AddChecklist(selectedItems())) },
                        enabled = state.selected.isNotEmpty(),
                        style = PlannerButtonStyle.Tonal,
                    )
                }
            }
        }
    }
}

@Composable
internal fun actionLabel(action: AssistantAction, translateTo: AssistantLanguage): String = stringResource(
    when (action) {
        AssistantAction.SUMMARIZE -> R.string.assistant_action_summarize
        AssistantAction.REWRITE -> R.string.assistant_action_rewrite
        AssistantAction.TRANSLATE -> if (translateTo == AssistantLanguage.ENGLISH) R.string.assistant_action_translate_en else R.string.assistant_action_translate_fa
        AssistantAction.CONTINUE -> R.string.assistant_action_continue
        AssistantAction.EXTRACT_TASKS -> R.string.assistant_action_extract
        AssistantAction.SUGGEST_TITLES -> R.string.assistant_action_titles
        AssistantAction.BREAK_DOWN -> R.string.assistant_action_break_down
    },
)

private fun actionIcon(action: AssistantAction): ImageVector = when (action) {
    AssistantAction.SUMMARIZE -> Icons.AutoMirrored.Rounded.ShortText
    AssistantAction.REWRITE -> Icons.Rounded.AutoFixHigh
    AssistantAction.TRANSLATE -> Icons.Rounded.Translate
    AssistantAction.CONTINUE -> Icons.Rounded.EditNote
    AssistantAction.EXTRACT_TASKS -> Icons.Rounded.FactCheck
    AssistantAction.SUGGEST_TITLES -> Icons.Rounded.Title
    AssistantAction.BREAK_DOWN -> Icons.Rounded.AccountTree
}
