package com.behnamjalali.planb.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.rememberPlannerHaptics
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.rememberNotificationPermissionRequest
import java.time.Instant
import java.time.ZoneId

data class FocusCallbacks(
    val onBack: () -> Unit = {},
    val onSelectMinutes: (Int) -> Unit = {},
    val onLinkTask: (EntityId?) -> Unit = {},
    val onStart: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onElapsed: () -> Unit = {},
)

@Composable
fun FocusDestination(onBack: () -> Unit, snackbarHostState: SnackbarHostState, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberPlannerHaptics()
    val requestNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            when (m) {
                FocusMessage.Completed -> {
                    haptics.success()
                    snackbarHostState.showSnackbar(context.getString(R.string.focus_completed_message))
                }
                FocusMessage.Failed -> snackbarHostState.showSnackbar(context.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
            }
        }
    }
    // Re-reads the wall clock every frame while running; logical time comes from stored timestamps.
    var now by remember { mutableStateOf(viewModel.now()) }
    val running = state.active?.status == FocusStatus.RUNNING
    LaunchedEffect(running) {
        now = viewModel.now()
        while (running) {
            withFrameMillis { now = viewModel.now() }
            if ((state.active?.remainingMillis(now) ?: 1L) <= 0L) {
                viewModel.onElapsed()
                break
            }
            kotlinx.coroutines.delay(200)
        }
    }
    FocusScreen(
        state = state,
        now = now,
        callbacks = FocusCallbacks(
            onBack = onBack,
            onSelectMinutes = viewModel::selectMinutes,
            onLinkTask = viewModel::linkTask,
            onStart = {
                requestNotifications()
                viewModel.start()
            },
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onFinish = viewModel::finish,
            onCancel = viewModel::cancel,
        ),
    )
}

@Composable
fun FocusScreen(state: FocusUiState, now: Instant, callbacks: FocusCallbacks) {
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    var customDialog by rememberSaveable { mutableStateOf(false) }
    var taskMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.focus_title), onBack = callbacks.onBack)
        if (state.loading) {
            PlannerLoadingState()
            return@Column
        }
        val active = state.active
        val planned = active?.plannedDurationMillis ?: (state.selectedMinutes * 60_000L)
        val remaining = active?.remainingMillis(now) ?: planned
        val progress = if (planned == 0L) 0f else 1f - remaining.toFloat() / planned
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "ring") {
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), contentAlignment = Alignment.Center) {
                    PlannerProgressRing(progress, size = 240.dp, strokeWidth = 14.dp, color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatter.timer(remaining),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(
                                stringResource(
                                    when (active?.status) {
                                        FocusStatus.RUNNING -> R.string.focus_running
                                        FocusStatus.PAUSED -> R.string.focus_paused
                                        else -> R.string.focus_ready
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "controls") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    when (active?.status) {
                        FocusStatus.RUNNING -> {
                            PlannerButton(stringResource(R.string.focus_pause), callbacks.onPause, icon = Icons.Rounded.Pause, style = PlannerButtonStyle.Tonal)
                            PlannerButton(stringResource(R.string.focus_finish), callbacks.onFinish, icon = Icons.Rounded.Stop)
                        }
                        FocusStatus.PAUSED -> {
                            PlannerButton(stringResource(R.string.focus_resume), callbacks.onResume, icon = Icons.Rounded.PlayArrow)
                            PlannerButton(stringResource(R.string.focus_finish), callbacks.onFinish, icon = Icons.Rounded.Stop, style = PlannerButtonStyle.Tonal)
                        }
                        else -> PlannerButton(stringResource(R.string.focus_start), callbacks.onStart, icon = Icons.Rounded.PlayArrow)
                    }
                }
                if (active != null) {
                    PlannerButton(stringResource(R.string.focus_cancel), callbacks.onCancel, icon = Icons.Rounded.Close, style = PlannerButtonStyle.Text)
                }
            }
            if (active == null) {
                item(key = "presets") {
                    val presets = listOf(state.pomodoroMinutes, 15, 45, 60).distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(presets) { m ->
                            val label = if (m == state.pomodoroMinutes) stringResource(R.string.focus_pomodoro, formatter.duration(m)) else formatter.duration(m)
                            PlannerChip(label, state.selectedMinutes == m, { callbacks.onSelectMinutes(m) })
                        }
                        item {
                            val custom = state.selectedMinutes !in presets
                            PlannerChip(if (custom) formatter.duration(state.selectedMinutes) else stringResource(R.string.focus_custom), custom, { customDialog = true })
                        }
                    }
                }
                item(key = "task") {
                    Box(Modifier.fillMaxWidth()) {
                        EditorRow(Icons.Rounded.TaskAlt, stringResource(R.string.focus_link_task), state.linkedTask?.title ?: stringResource(R.string.focus_no_task), { taskMenu = true })
                        DropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.focus_no_task)) }, onClick = { callbacks.onLinkTask(null); taskMenu = false })
                            state.tasks.forEach { t -> DropdownMenuItem(text = { Text(t.title) }, onClick = { callbacks.onLinkTask(t.id); taskMenu = false }) }
                        }
                    }
                }
            } else if (state.linkedTask != null) {
                item(key = "linked") { EditorRow(Icons.Rounded.TaskAlt, stringResource(R.string.focus_link_task), state.linkedTask!!.title, {}) }
            }
            item(key = "today") {
                Text(stringResource(R.string.focus_today, formatter.duration(state.focusedTodayMinutes)), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.focus_break_hint, formatter.duration(state.breakMinutes)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "history_h") { PlannerSectionHeader(stringResource(R.string.focus_history), Modifier.fillMaxWidth()) }
            if (state.history.isEmpty()) {
                item(key = "history_empty") { Text(stringResource(R.string.focus_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.history, key = { it.id }) { s ->
                PlannerCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val date = s.startedAt.atZone(ZoneId.systemDefault())
                            Text(formatter.duration((s.actualDurationMillis / 60_000L).toInt()), style = MaterialTheme.typography.titleSmall)
                            Text("${formatter.relativeDate(date.toLocalDate(), PlannerLocals.today)} · ${formatter.time(date.toLocalTime())}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(if (s.status == FocusStatus.COMPLETED) R.string.focus_session_completed else R.string.focus_session_cancelled),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (s.status == FocusStatus.COMPLETED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
    if (customDialog) {
        var text by rememberSaveable { mutableStateOf(state.selectedMinutes.toString()) }
        val value = Digits.toLatin(text).toIntOrNull()?.takeIf { it in 1..180 }
        PlannerDialog(
            title = stringResource(R.string.focus_custom_title),
            onDismiss = { customDialog = false },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_done),
            confirmEnabled = value != null,
            onConfirm = {
                value?.let(callbacks.onSelectMinutes)
                customDialog = false
            },
        ) {
            PlannerTextField(text, { text = it.take(3) }, stringResource(R.string.focus_minutes), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(numbers.localize("1 – 180"), style = MaterialTheme.typography.bodySmall)
        }
    }
}
