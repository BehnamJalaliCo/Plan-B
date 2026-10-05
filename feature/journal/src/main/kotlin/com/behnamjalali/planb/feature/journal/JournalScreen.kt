package com.behnamjalali.planb.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.JournalPage
import com.behnamjalali.planb.core.model.JournalPrompts
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.metaSeparator
import com.behnamjalali.planb.core.ui.rememberNotificationPermissionRequest

/** Icons of mood levels 1..5. */
internal val moodIcons: List<ImageVector> = listOf(
    Icons.Rounded.SentimentVeryDissatisfied,
    Icons.Rounded.SentimentDissatisfied,
    Icons.Rounded.SentimentNeutral,
    Icons.Rounded.SentimentSatisfied,
    Icons.Rounded.SentimentVerySatisfied,
)

private val energyIcons: List<ImageVector> = listOf(
    Icons.Rounded.Battery0Bar,
    Icons.Rounded.Battery2Bar,
    Icons.Rounded.Battery4Bar,
    Icons.Rounded.Battery6Bar,
    Icons.Rounded.BatteryFull,
)

internal val moodLabels = listOf(R.string.journal_mood_1, R.string.journal_mood_2, R.string.journal_mood_3, R.string.journal_mood_4, R.string.journal_mood_5)
private val energyLabels = listOf(R.string.journal_energy_1, R.string.journal_energy_2, R.string.journal_energy_3, R.string.journal_energy_4, R.string.journal_energy_5)

/** Mood level 1..5 as a color, from the error color (very low) to the success color (great). */
@Composable
internal fun moodColor(level: Int): Color {
    val scheme = MaterialTheme.colorScheme
    val colors = PlanBTheme.colors
    return when (level) {
        1 -> scheme.error
        2 -> colors.warning.copy(alpha = 0.85f)
        3 -> scheme.outline
        4 -> colors.success.copy(alpha = 0.7f)
        else -> colors.success
    }
}

/** The prompt text of a key: built-in prompts from resources, the user's own from the settings. */
@Composable
internal fun promptText(key: String?, custom: List<String>): String? {
    val builtIn = stringArrayResource(R.array.journal_prompts)
    JournalPrompts.builtInNumber(key)?.let { return builtIn.getOrNull(it - 1) }
    return JournalPrompts.customText(key, custom)
}

@Composable
fun JournalDestination(
    onBack: () -> Unit,
    onOpenNote: (EntityId) -> Unit,
    onOpenCalendar: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is JournalEvent.OpenNote -> onOpenNote(event.id)
                JournalEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.journal_failed))
            }
        }
    }
    val texts = JournalTexts(
        notebook = stringResource(R.string.journal_notebook),
        pageTitle = if (state.loading) "" else PlannerLocals.formatter.fullDate(state.today),
        prompt = promptText(state.promptKey, state.settings.customPrompts),
    )
    JournalScreen(
        state = state,
        actions = JournalActions(
            onBack = onBack,
            onOpenNote = onOpenNote,
            onOpenCalendar = onOpenCalendar,
            onWrite = { viewModel.write(texts) },
            onAnotherPrompt = viewModel::anotherPrompt,
            onMood = { viewModel.setMood(texts, it) },
            onEnergy = { viewModel.setEnergy(texts, it) },
            onTags = { viewModel.setTags(texts, it) },
            onReminder = viewModel::setReminder,
            onReminderTime = viewModel::setReminderTime,
            onAddPrompt = viewModel::addPrompt,
            onRemovePrompt = viewModel::removePrompt,
        ),
    )
}

data class JournalActions(
    val onBack: () -> Unit = {},
    val onOpenNote: (EntityId) -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onWrite: () -> Unit = {},
    val onAnotherPrompt: () -> Unit = {},
    val onMood: (Int?) -> Unit = {},
    val onEnergy: (Int?) -> Unit = {},
    val onTags: (String) -> Unit = {},
    val onReminder: (Boolean) -> Unit = {},
    val onReminderTime: (java.time.LocalTime) -> Unit = {},
    val onAddPrompt: (String) -> Unit = {},
    val onRemovePrompt: (String) -> Unit = {},
)

/**
 * The daily journal (Plan-B Pro #25): today's prompt, a mood and energy check-in, tags, the
 * streak and recent pages. Each day's page is a note in the journal notebook (shared with the
 * ritual reflections), written in the regular editor.
 */
@Composable
fun JournalScreen(state: JournalUi, actions: JournalActions) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.journal_title),
            onBack = actions.onBack,
            actions = {
                PlannerIconButton(Icons.Rounded.CalendarMonth, stringResource(R.string.journal_calendar), actions.onOpenCalendar)
                PlannerIconButton(Icons.Rounded.Settings, stringResource(R.string.journal_settings), { settingsOpen = true })
            },
        )
        ProGate(ProFeature.JOURNAL, teaser = { ProTeaser(ProFeature.JOURNAL, Modifier.padding(horizontal = Spacing.screen)) }) {
            if (state.loading) {
                PlannerLoadingState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.huge),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item(key = "today") { TodayCard(state, actions) }
                    item(key = "streak") { StreakLine(state.streak) }
                    item(key = "recent_h") { PlannerSectionHeader(stringResource(R.string.journal_recent)) }
                    if (state.recent.isEmpty()) {
                        item(key = "empty") {
                            Text(stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(state.recent, key = { it.entryId }) { page -> PageRow(page, state, actions.onOpenNote) }
                }
            }
        }
    }
    if (settingsOpen) JournalSettingsDialog(state, actions) { settingsOpen = false }
}

@Composable
private fun TodayCard(state: JournalUi, actions: JournalActions) {
    val formatter = PlannerLocals.formatter
    PlannerCard(modifier = Modifier.fillMaxWidth()) {
        Text(formatter.fullDate(state.today), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.journal_prompt_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                promptText(state.promptKey, state.settings.customPrompts).orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
            )
            // Before the page exists, another prompt can be picked; afterwards the page keeps its own.
            if (state.page == null) PlannerIconButton(Icons.Rounded.Refresh, stringResource(R.string.journal_another_prompt), actions.onAnotherPrompt)
        }
        LevelPicker(stringResource(R.string.journal_mood), state.mood?.mood, moodIcons, moodLabels, { moodColor(it) }, actions.onMood)
        LevelPicker(stringResource(R.string.journal_energy), state.mood?.energy, energyIcons, energyLabels, { MaterialTheme.colorScheme.tertiary }, actions.onEnergy)
        TagsRow(state, actions.onTags)
        PlannerButton(
            text = stringResource(if (state.page == null) R.string.journal_write else R.string.journal_continue),
            onClick = actions.onWrite,
            icon = Icons.Rounded.EditNote,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun LevelPicker(
    label: String,
    selected: Int?,
    icons: List<ImageVector>,
    labels: List<Int>,
    color: @Composable (Int) -> Color,
    onSelect: (Int?) -> Unit,
) {
    Column(Modifier.padding(top = Spacing.sm)) {
        Text(
            label + (selected?.let { metaSeparator() + stringResource(labels[it - 1]) } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            icons.forEachIndexed { i, icon ->
                val level = i + 1
                val isSelected = selected == level
                val description = stringResource(R.string.journal_level_cd, label, stringResource(labels[i]))
                val tint = if (isSelected) color(level) else MaterialTheme.colorScheme.outline
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) tint.copy(alpha = 0.18f) else Color.Transparent,
                    modifier = Modifier
                        .size(48.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(if (isSelected) null else level) })
                        .semantics { contentDescription = description },
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(Spacing.md))
                }
            }
        }
    }
}

@Composable
private fun TagsRow(state: JournalUi, onTags: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(state.tags) { mutableStateOf(state.tags.joinToString(", ")) }
    Column(Modifier.padding(top = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.journal_tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (!editing) TextButton(onClick = { editing = true }) { Text(stringResource(R.string.journal_tags_hint)) }
        }
        if (state.tags.isNotEmpty() && !editing) {
            Text(state.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (editing) {
            PlannerTextField(text, { text = it }, stringResource(R.string.journal_tags_hint))
            TextButton(onClick = {
                onTags(text)
                editing = false
            }) { Text(stringResource(R.string.journal_tags_save)) }
        }
    }
}

@Composable
private fun StreakLine(streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = if (streak > 0) PlanBTheme.colors.warning else MaterialTheme.colorScheme.outline)
        Text(
            if (streak > 0) pluralStringResource(R.plurals.journal_streak, streak, PlannerLocals.numbers.format(streak)) else stringResource(R.string.journal_streak_start),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PageRow(page: JournalPage, state: JournalUi, onOpen: (EntityId) -> Unit) {
    val formatter = PlannerLocals.formatter
    PlannerCard(onClick = { onOpen(page.noteId) }, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(if (page.locked) Icons.Rounded.Lock else Icons.AutoMirrored.Rounded.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(formatter.weekdayDate(page.date, state.today), style = MaterialTheme.typography.titleSmall)
                val preview = if (page.locked) stringResource(R.string.journal_locked) else page.preview.ifBlank { promptText(page.promptId, state.settings.customPrompts).orEmpty() }
                if (preview.isNotBlank()) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun JournalSettingsDialog(state: JournalUi, actions: JournalActions, onDismiss: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var newPrompt by rememberSaveable { mutableStateOf("") }
    val askPermission = rememberNotificationPermissionRequest()
    val formatter = PlannerLocals.formatter
    PlannerDialog(
        title = stringResource(R.string.journal_settings),
        onDismiss = onDismiss,
        confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_done),
        onConfirm = onDismiss,
        dismissLabel = "",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.journal_reminder), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.journal_reminder_sub, formatter.time(state.settings.reminderTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.settings.reminder, onCheckedChange = { on ->
                if (on) askPermission()
                actions.onReminder(on)
            })
        }
        if (state.settings.reminder) {
            PlannerButton(
                text = stringResource(R.string.journal_reminder_time) + metaSeparator() + formatter.time(state.settings.reminderTime),
                onClick = { picking = true },
                style = PlannerButtonStyle.Tonal,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.journal_my_prompts), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.journal_my_prompts_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.settings.customPrompts.forEach { prompt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.journal_delete_prompt, prompt), { actions.onRemovePrompt(prompt) })
            }
        }
        PlannerTextField(newPrompt, { newPrompt = it }, stringResource(R.string.journal_prompt_hint))
        TextButton(onClick = {
            actions.onAddPrompt(newPrompt)
            newPrompt = ""
        }, enabled = newPrompt.isNotBlank()) { Text(stringResource(R.string.journal_add_prompt)) }
    }
    if (picking) {
        PlannerTimePickerDialog(
            initial = state.settings.reminderTime,
            onDismiss = { picking = false },
            onConfirm = { time ->
                picking = false
                time?.let(actions.onReminderTime)
            },
            allowClear = false,
        )
    }
}
