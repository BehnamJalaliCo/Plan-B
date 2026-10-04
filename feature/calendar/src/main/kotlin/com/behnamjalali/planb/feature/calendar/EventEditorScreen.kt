package com.behnamjalali.planb.feature.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.ui.AccentColorPicker
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.CustomRecurrenceDialog
import com.behnamjalali.planb.core.ui.DiscardChangesDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.RecurrenceMenu
import com.behnamjalali.planb.core.ui.RecurrencePreset
import com.behnamjalali.planb.core.ui.ReminderMenu
import com.behnamjalali.planb.core.ui.presetRule
import com.behnamjalali.planb.core.ui.recurrenceSummary
import com.behnamjalali.planb.core.ui.reminderLabel
import java.time.LocalTime

@Composable
fun EventEditorDestination(onClose: () -> Unit, viewModel: EventEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val calendarSystem by viewModel.calendarSystem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.editorEvents.collect { event ->
            when (event) {
                EventEditorEvent.Saved, EventEditorEvent.Deleted -> onClose()
                EventEditorEvent.Failed -> snackbar.showSnackbar(resources.getString(R.string.event_editor_failed))
                EventEditorEvent.NotFound -> {
                    snackbar.showSnackbar(resources.getString(R.string.event_editor_not_found))
                    onClose()
                }
            }
        }
    }
    val requestClose = { if (viewModel.isDirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)
    EventEditorScreen(
        form = form,
        isNew = viewModel.isNew,
        calendarSystem = calendarSystem,
        snackbarHostState = snackbar,
        onClose = requestClose,
        onUpdate = viewModel::update,
        onStart = viewModel::setStart,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDismiss = { confirmDiscard = false }, onDiscard = {
            confirmDiscard = false
            onClose()
        })
    }
}

@Composable
fun EventEditorScreen(
    form: EventForm,
    isNew: Boolean,
    calendarSystem: CalendarSystem,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onUpdate: ((EventForm) -> EventForm) -> Unit,
    onStart: (LocalTime?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val context = LocalContext.current
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    val notSet = stringResource(R.string.event_editor_not_set)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PlannerTopBar(
                title = stringResource(if (isNew) R.string.event_editor_new else R.string.event_editor_edit),
                onBack = onClose,
                actions = {
                    if (!isNew) PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.event_editor_delete), { confirmDelete = true })
                },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                PlannerButton(
                    stringResource(R.string.event_editor_save),
                    {
                        titleTouched = true
                        onSave()
                    },
                    Modifier.fillMaxWidth(),
                    enabled = form.valid,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PlannerTextField(
                value = form.title,
                onValueChange = { v ->
                    titleTouched = true
                    onUpdate { it.copy(title = v) }
                },
                label = stringResource(R.string.event_editor_title),
                isError = titleTouched && form.title.isBlank(),
                supportingText = if (titleTouched && form.title.isBlank()) stringResource(R.string.event_editor_title_required) else null,
            )
            EditorRow(Icons.Rounded.Event, stringResource(R.string.event_editor_date), formatter.weekdayDate(form.localDate, today), { picker = "date" })
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .toggleable(form.allDay, role = Role.Switch) { allDay ->
                        onUpdate { it.copy(allDay = allDay, start = if (allDay) null else it.start ?: 9 * 3600, end = if (allDay) null else it.end ?: 10 * 3600) }
                    }
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.event_editor_all_day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.allDay, onCheckedChange = null)
            }
            if (!form.allDay) {
                EditorRow(Icons.Rounded.Schedule, stringResource(R.string.event_editor_start), form.startTime?.let { formatter.time(it) } ?: notSet, { picker = "start" })
                EditorRow(Icons.Rounded.Schedule, stringResource(R.string.event_editor_end), form.endTime?.let { formatter.time(it) } ?: notSet, { picker = "end" })
                if (form.timeError) {
                    Text(stringResource(R.string.event_editor_end_before_start), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Box {
                EditorRow(Icons.Rounded.Notifications, stringResource(R.string.event_editor_reminder), reminderLabel(form.reminder), { reminderMenu = true })
                ReminderMenu(reminderMenu, { reminderMenu = false }) { offset ->
                    onUpdate { it.copy(reminder = offset) }
                    if (offset != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            Box {
                EditorRow(Icons.Rounded.Repeat, stringResource(R.string.event_editor_repeat), recurrenceSummary(form.rule), { repeatMenu = true })
                RecurrenceMenu(repeatMenu, { repeatMenu = false }) { preset ->
                    if (preset == RecurrencePreset.CUSTOM) customRepeat = true
                    else onUpdate { it.copy(recurrence = presetRule(preset, calendarSystem)?.encode()) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
            Text(stringResource(R.string.event_editor_color), style = MaterialTheme.typography.labelLarge)
            AccentColorPicker(AccentColor.fromKey(form.color), { c -> onUpdate { it.copy(color = c.key) } })
            PlannerTextField(
                value = form.description,
                onValueChange = { v -> onUpdate { it.copy(description = v) } },
                label = stringResource(R.string.event_editor_description),
                singleLine = false,
                minLines = 2,
            )
            PlannerTextField(
                value = form.notes,
                onValueChange = { v -> onUpdate { it.copy(notes = v) } },
                label = stringResource(R.string.event_editor_notes),
                singleLine = false,
                minLines = 3,
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    when (picker) {
        "date" -> PlannerDatePickerDialog(
            initial = form.localDate,
            onDismiss = { picker = null },
            onConfirm = { date ->
                date?.let { d -> onUpdate { it.copy(date = d.toEpochDay()) } }
                picker = null
            },
            allowClear = false,
        )
        "start" -> PlannerTimePickerDialog(form.startTime, { picker = null }, {
            onStart(it)
            picker = null
        }, allowClear = false)
        "end" -> PlannerTimePickerDialog(form.endTime, { picker = null }, { t ->
            onUpdate { it.copy(end = t?.toSecondOfDay()) }
            picker = null
        }, allowClear = false)
    }
    if (customRepeat) {
        CustomRecurrenceDialog(form.rule, calendarSystem, form.localDate, { customRepeat = false }, { rule ->
            onUpdate { it.copy(recurrence = rule.encode()) }
            customRepeat = false
        })
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.event_editor_delete),
            message = stringResource(R.string.event_editor_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}
