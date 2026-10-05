package com.behnamjalali.planb.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerPill
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressBar
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.focus.label
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.FocusProSettings
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard

/** What the Focus Pro card does; see [FocusViewModel]. */
data class FocusProActions(
    val onSound: (AmbientSound?) -> Unit = {},
    val onVolume: (Int) -> Unit = {},
    val onStrict: (Boolean) -> Unit = {},
    val onOpenDndAccess: () -> Unit = {},
    val onDailyGoal: (Int) -> Unit = {},
    val onLongBreak: (every: Int, minutes: Int) -> Unit = { _, _ -> },
)

/**
 * Focus Pro (Plan-B Pro #26): ambient sound and volume, strict mode (Do Not Disturb while a
 * session runs), the daily focus goal and long breaks. Free users see one calm row with a Pro
 * badge that opens the Pro screen when tapped.
 */
@Composable
internal fun FocusProSection(state: FocusUiState, actions: FocusProActions) {
    if (!LocalProAccess.current.isPro) {
        val guard = rememberProGuard()
        PlannerCard(Modifier.fillMaxWidth(), onClick = { guard.run(ProFeature.FOCUS_PRO) {} }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_pro_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.focus_pro_teaser), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProBadge()
            }
        }
        return
    }
    PlannerCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.focus_pro_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        SoundPicker(state, actions)
        Spacer(Modifier.height(Spacing.md))
        StrictRow(state, actions)
        GoalRow(state, actions)
        LongBreakRow(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPicker(state: FocusUiState, actions: FocusProActions) {
    Text(stringResource(R.string.focus_pro_sound), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PlannerChip(stringResource(R.string.focus_pro_sound_off), state.sound == null, { actions.onSound(null) }, icon = Icons.Rounded.VolumeOff)
        AmbientSound.entries.forEach { sound ->
            PlannerChip(stringResource(sound.label()), state.sound == sound, { actions.onSound(sound) })
        }
    }
    if (state.sound != null) {
        // Moves freely while dragging; saved when let go.
        var volume by remember(state.focusPro.volume) { mutableFloatStateOf(state.focusPro.volume.toFloat()) }
        val label = stringResource(R.string.focus_pro_volume)
        val percent = PlannerLocals.numbers.percent(volume / 100f)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { actions.onVolume(volume.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = label
                    stateDescription = percent
                },
            )
        }
        if (state.active == null) {
            Text(stringResource(R.string.focus_pro_sound_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StrictRow(state: FocusUiState, actions: FocusProActions) {
    var explain by rememberSaveable { mutableStateOf(false) }
    val onToggle = { on: Boolean ->
        actions.onStrict(on)
        // Asked in context: only when turning it on without access.
        if (on && !state.dndAccess) explain = true
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).toggleable(state.strict, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(Icons.Rounded.DoNotDisturbOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.focus_pro_strict), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.focus_pro_strict_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.strict, onCheckedChange = null)
    }
    if (state.strict && !state.dndAccess) {
        Text(stringResource(R.string.focus_pro_strict_no_access), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        PlannerButton(stringResource(R.string.focus_pro_strict_allow), actions.onOpenDndAccess, style = PlannerButtonStyle.Text)
    }
    if (explain) {
        PlannerDialog(
            title = stringResource(R.string.focus_pro_strict_dialog_title),
            onDismiss = { explain = false },
            confirmLabel = stringResource(R.string.focus_pro_strict_open_settings),
            onConfirm = {
                explain = false
                actions.onOpenDndAccess()
            },
            dismissLabel = stringResource(R.string.focus_pro_not_now),
        ) {
            Text(stringResource(R.string.focus_pro_strict_dialog_text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GoalRow(state: FocusUiState, actions: FocusProActions) {
    val formatter = PlannerLocals.formatter
    val goal = state.focusPro.dailyGoalMinutes
    val none = stringResource(R.string.focus_pro_goal_none)
    ChoiceRow(
        icon = Icons.Rounded.Flag,
        label = stringResource(R.string.focus_pro_goal),
        choices = FocusProSettings.GOAL_CHOICES,
        selected = goal,
        text = { if (it == 0) none else formatter.duration(it) },
        onSelect = actions.onDailyGoal,
    )
    if (goal > 0) {
        val done = state.focusedTodayMinutes
        val text = stringResource(R.string.focus_pro_goal_progress, formatter.duration(done), formatter.duration(goal))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        PlannerProgressBar(progress = (done.toFloat() / goal).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().semantics { contentDescription = text })
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun LongBreakRow(state: FocusUiState, actions: FocusProActions) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val pro = state.focusPro
    ChoiceRow(
        icon = Icons.Rounded.Snooze,
        label = stringResource(R.string.focus_pro_long_break_every),
        choices = FocusProSettings.LONG_BREAK_EVERY_CHOICES,
        selected = pro.longBreakEvery,
        text = { pluralStringResource(R.plurals.focus_pro_sessions, it, numbers.format(it)) },
        onSelect = { actions.onLongBreak(it, pro.longBreakMinutes) },
    )
    ChoiceRow(
        icon = Icons.Rounded.Snooze,
        label = stringResource(R.string.focus_pro_long_break_length),
        choices = FocusProSettings.LONG_BREAK_CHOICES,
        selected = pro.longBreakMinutes,
        text = { formatter.duration(it) },
        onSelect = { actions.onLongBreak(pro.longBreakEvery, it) },
    )
}

@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    choices: List<Int>,
    selected: Int,
    text: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        EditorRow(icon, label, text(selected), { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(text(choice), color = if (choice == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        menu = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

/** "Session 2 of 4 · next break 5 min", or the long break after a full cycle (Focus Pro). */
@Composable
internal fun cycleText(state: FocusUiState): String {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    val cycle = state.cycle
    return if (cycle.longBreak && state.active == null) {
        stringResource(R.string.focus_pro_long_break_now, formatter.duration(cycle.breakMinutes))
    } else {
        stringResource(R.string.focus_pro_cycle, numbers.format(cycle.positionInCycle), numbers.format(cycle.cycleLength), formatter.duration(cycle.breakMinutes))
    }
}

/** While a session runs: the sound playing and whether Do Not Disturb is on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusProStatus(state: FocusUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.sound?.let { sound ->
            PlannerPill(stringResource(R.string.focus_pro_sound_playing, stringResource(sound.label())), icon = Icons.Rounded.Headphones)
        }
        if (state.active?.strict == true && state.dndAccess) {
            PlannerPill(stringResource(R.string.focus_pro_strict_on), icon = Icons.Rounded.DoNotDisturbOn)
        }
    }
}
