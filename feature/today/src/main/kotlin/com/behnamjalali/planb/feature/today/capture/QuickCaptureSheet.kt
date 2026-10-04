package com.behnamjalali.planb.feature.today.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.rememberPlannerHaptics
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.feature.today.R
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun captureTypeLabel(type: CaptureType): String = stringResource(
    when (type) {
        CaptureType.TASK -> R.string.capture_type_task
        CaptureType.NOTE -> R.string.capture_type_note
        CaptureType.EVENT -> R.string.capture_type_event
        CaptureType.HABIT -> R.string.capture_type_habit
        CaptureType.PROJECT -> R.string.capture_type_project
    },
)

fun captureSavedMessage(type: CaptureType): Int = when (type) {
    CaptureType.TASK -> R.string.capture_saved_task
    CaptureType.NOTE -> R.string.capture_saved_note
    CaptureType.EVENT -> R.string.capture_saved_event
    CaptureType.HABIT -> R.string.capture_saved_habit
    CaptureType.PROJECT -> R.string.capture_saved_project
}

/**
 * Minimal-tap capture of tasks, notes, events, habits and projects.
 * The title field is focused automatically so typing can start immediately.
 */
@Composable
fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    onSaved: (CaptureType, EntityId) -> Unit,
    onFailed: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val dateEpoch by viewModel.dateEpoch.collectAsStateWithLifecycle()
    val timeSeconds by viewModel.timeSeconds.collectAsStateWithLifecycle()
    val highPriority by viewModel.highPriority.collectAsStateWithLifecycle()
    val defaultNotebook = stringResource(com.behnamjalali.planb.core.data.R.string.data_default_notebook)
    val haptics = rememberPlannerHaptics()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.Saved -> {
                    haptics.success()
                    onSaved(event.type, event.id)
                }
                CaptureEvent.Failed -> onFailed()
            }
        }
    }

    QuickCaptureContent(
        type = type,
        title = title,
        body = body,
        date = dateEpoch?.let(LocalDate::ofEpochDay),
        time = timeSeconds?.let { LocalTime.ofSecondOfDay(it.toLong()) },
        highPriority = highPriority,
        onDismiss = onDismiss,
        onTypeChange = viewModel::setType,
        onTitleChange = viewModel::setTitle,
        onBodyChange = viewModel::setBody,
        onDateChange = viewModel::setDate,
        onTimeChange = viewModel::setTime,
        onPriorityChange = viewModel::setHighPriority,
        onSave = { viewModel.save(defaultNotebook) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickCaptureContent(
    type: CaptureType,
    title: String,
    body: String,
    date: LocalDate?,
    time: LocalTime?,
    highPriority: Boolean,
    onDismiss: () -> Unit,
    onTypeChange: (CaptureType) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onPriorityChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val focusRequester = remember { FocusRequester() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val canSave = title.isNotBlank() || (type == CaptureType.NOTE && body.isNotBlank())

    LaunchedEffect(type) { runCatching { focusRequester.requestFocus() } }

    PlannerBottomSheet(onDismiss = onDismiss, modifier = Modifier.imePadding()) {
        Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.md))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(CaptureType.entries) { option ->
                PlannerChip(
                    label = captureTypeLabel(option),
                    selected = option == type,
                    onClick = { onTypeChange(option) },
                    icon = when (option) {
                        CaptureType.TASK -> Icons.Rounded.CheckCircle
                        CaptureType.NOTE -> Icons.AutoMirrored.Rounded.Notes
                        CaptureType.EVENT -> Icons.Rounded.Event
                        CaptureType.HABIT -> Icons.Rounded.Repeat
                        CaptureType.PROJECT -> Icons.Rounded.Folder
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PlannerTextField(
            value = title,
            onValueChange = onTitleChange,
            label = stringResource(
                when (type) {
                    CaptureType.TASK -> R.string.capture_hint_task
                    CaptureType.NOTE -> R.string.capture_hint_note
                    CaptureType.EVENT -> R.string.capture_hint_event
                    CaptureType.HABIT -> R.string.capture_hint_habit
                    CaptureType.PROJECT -> R.string.capture_hint_project
                },
            ),
            modifier = Modifier.focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (type == CaptureType.NOTE) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
        )
        if (type == CaptureType.NOTE) {
            Spacer(Modifier.height(Spacing.sm))
            PlannerTextField(
                value = body,
                onValueChange = onBodyChange,
                label = stringResource(R.string.capture_hint_note_body),
                singleLine = false,
                minLines = 3,
                maxLines = 8,
            )
        }
        if (type == CaptureType.TASK || type == CaptureType.EVENT) {
            Spacer(Modifier.height(Spacing.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerChip(formatter.relativeDate(today, today), date == today, { onDateChange(today) }, icon = Icons.Rounded.Today)
                PlannerChip(formatter.relativeDate(today.plusDays(1), today), date == today.plusDays(1), { onDateChange(today.plusDays(1)) })
                val custom = date != null && date != today && date != today.plusDays(1)
                PlannerChip(
                    if (custom) formatter.shortDate(date, today) else stringResource(R.string.capture_pick_date),
                    custom,
                    { pickingDate = true },
                    icon = Icons.Rounded.Event,
                )
                if (type == CaptureType.TASK) {
                    PlannerChip(stringResource(R.string.capture_no_date), date == null, { onDateChange(null) })
                }
                PlannerChip(
                    time?.let { formatter.time(it) } ?: stringResource(
                        if (type == CaptureType.EVENT) R.string.capture_all_day else R.string.capture_pick_time,
                    ),
                    time != null,
                    { pickingTime = true },
                    icon = Icons.Rounded.Schedule,
                )
                if (type == CaptureType.TASK) {
                    PlannerChip(stringResource(R.string.capture_high_priority), highPriority, { onPriorityChange(!highPriority) }, icon = Icons.Rounded.Flag)
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row {
            Spacer(Modifier.weight(1f))
            PlannerButton(stringResource(R.string.capture_save), onSave, enabled = canSave)
        }
    }

    if (pickingDate) {
        PlannerDatePickerDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onConfirm = {
                onDateChange(it)
                pickingDate = false
            },
            allowClear = type == CaptureType.TASK,
        )
    }
    if (pickingTime) {
        PlannerTimePickerDialog(
            initial = time,
            onDismiss = { pickingTime = false },
            onConfirm = {
                onTimeChange(it)
                pickingTime = false
            },
        )
    }
}
