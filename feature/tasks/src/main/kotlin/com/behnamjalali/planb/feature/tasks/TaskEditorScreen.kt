package com.behnamjalali.planb.feature.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.AnimatedTaskCheckbox
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.ui.CustomRecurrenceDialog
import com.behnamjalali.planb.core.ui.EditorRow
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.RecurrenceMenu
import com.behnamjalali.planb.core.ui.RecurrencePreset
import com.behnamjalali.planb.core.ui.ReminderMenu
import com.behnamjalali.planb.core.ui.presetRule
import com.behnamjalali.planb.core.ui.priorityLabel
import com.behnamjalali.planb.core.ui.recurrenceSummary
import com.behnamjalali.planb.core.ui.reminderLabel
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskEditorDestination(
    onClose: () -> Unit,
    onOpenSubtask: (EntityId) -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val calendarSystem by viewModel.calendarSystem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved, EditorEvent.Deleted -> onClose()
                EditorEvent.Failed -> snackbar.showSnackbar(resources.getString(R.string.tasks_error))
                EditorEvent.NotFound -> {
                    snackbar.showSnackbar(resources.getString(R.string.task_editor_not_found))
                    onClose()
                }
            }
        }
    }
    val requestClose = { if (viewModel.isDirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)

    TaskEditorScreen(
        form = form,
        isNew = viewModel.isNew,
        projects = projects,
        subtasks = subtasks,
        calendarSystem = calendarSystem,
        snackbarHostState = snackbar,
        onClose = requestClose,
        onUpdate = viewModel::update,
        onDueDate = viewModel::setDueDate,
        onDueTime = { viewModel.setDueTime(it) },
        onRecurrence = viewModel::setRecurrence,
        onAddTag = viewModel::addTag,
        onRemoveTag = viewModel::removeTag,
        onAddSubtask = viewModel::addSubtask,
        onRemovePendingSubtask = viewModel::removePendingSubtask,
        onToggleSubtask = { id, done -> viewModel.toggleSubtask(id, done) },
        onDeleteSubtask = { viewModel.deleteSubtask(it) },
        onOpenSubtask = onOpenSubtask,
        onSave = { pendingTag -> viewModel.save(pendingTag) },
        onDelete = viewModel::delete,
    )

    if (confirmDiscard) {
        PlannerDialog(
            title = stringResource(R.string.task_editor_discard_title),
            message = stringResource(R.string.task_editor_discard_message),
            onDismiss = { confirmDiscard = false },
            confirmLabel = stringResource(R.string.task_editor_discard),
            dismissLabel = stringResource(R.string.task_editor_keep_editing),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    form: TaskForm,
    isNew: Boolean,
    projects: List<Project>,
    subtasks: List<Task>,
    calendarSystem: CalendarSystem,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onUpdate: ((TaskForm) -> TaskForm) -> Unit,
    onDueDate: (LocalDate?) -> Unit,
    onDueTime: (LocalTime?) -> Unit,
    onRecurrence: (RecurrenceRule?) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddSubtask: (String) -> Unit,
    onRemovePendingSubtask: (Int) -> Unit,
    onToggleSubtask: (EntityId, Boolean) -> Unit,
    onDeleteSubtask: (EntityId) -> Unit,
    onOpenSubtask: (EntityId) -> Unit,
    onSave: (pendingTag: String) -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val context = LocalContext.current
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var customRepeat by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var subtaskInput by rememberSaveable { mutableStateOf("") }
    val notSet = stringResource(R.string.task_editor_not_set)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PlannerTopBar(
                title = stringResource(if (isNew) R.string.task_editor_new else R.string.task_editor_edit),
                actions = {
                    if (!isNew) {
                        PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.task_editor_delete), { confirmDelete = true })
                    }
                },
                onBack = onClose,
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            ) {
                PlannerButton(
                    text = stringResource(R.string.task_editor_save),
                    onClick = {
                        titleTouched = true
                        onSave(tagInput)
                        tagInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = form.canSave,
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
                label = stringResource(R.string.task_editor_title),
                isError = titleTouched && form.title.isBlank(),
                supportingText = if (titleTouched && form.title.isBlank()) stringResource(R.string.task_editor_title_required) else null,
            )
            PlannerTextField(
                value = form.description,
                onValueChange = { v -> onUpdate { it.copy(description = v) } },
                label = stringResource(R.string.task_editor_description),
                singleLine = false,
                minLines = 2,
            )

            Text(stringResource(R.string.task_editor_status), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TaskStatus.entries.forEach { status ->
                    PlannerChip(
                        stringResource(
                            when (status) {
                                TaskStatus.TODO -> R.string.task_status_todo
                                TaskStatus.IN_PROGRESS -> R.string.task_status_in_progress
                                TaskStatus.DONE -> R.string.task_status_done
                            },
                        ),
                        form.status == status,
                        { onUpdate { it.copy(status = status) } },
                    )
                }
            }
            Text(stringResource(R.string.task_editor_priority), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Priority.entries.forEach { p ->
                    PlannerChip(priorityLabel(p), form.priority == p.weight, { onUpdate { it.copy(priority = p.weight) } }, icon = Icons.Rounded.Flag)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

            EditorRow(Icons.Rounded.Event, stringResource(R.string.task_editor_due_date),
                form.due?.let { formatter.weekdayDate(it, today) } ?: notSet, { picker = "due" })
            EditorRow(Icons.Rounded.Schedule, stringResource(R.string.task_editor_due_time),
                form.dueAt?.let { formatter.time(it) } ?: notSet, { picker = "dueTime" })
            EditorRow(Icons.Rounded.EventAvailable, stringResource(R.string.task_editor_start_date),
                form.start?.let { formatter.weekdayDate(it, today) } ?: notSet, { picker = "start" })
            if (!form.datesValid) {
                Text(
                    stringResource(R.string.task_editor_start_after_due),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
            EditorRow(Icons.Rounded.Schedule, stringResource(R.string.task_editor_start_time),
                form.startAt?.let { formatter.time(it) } ?: notSet, { picker = "startTime" })
            Box {
                EditorRow(Icons.Rounded.Notifications, stringResource(R.string.task_editor_reminder), reminderLabel(form.reminder), {
                    if (form.dueDate == null) picker = "due" else reminderMenu = true
                })
                ReminderMenu(reminderMenu, { reminderMenu = false }) { offset ->
                    onUpdate { it.copy(reminder = offset) }
                    if (offset != null) ensureNotificationPermission()
                }
            }
            Box {
                EditorRow(Icons.Rounded.Repeat, stringResource(R.string.task_editor_repeat), recurrenceSummary(form.rule), { repeatMenu = true })
                RecurrenceMenu(repeatMenu, { repeatMenu = false }) { preset ->
                    if (preset == RecurrencePreset.CUSTOM) customRepeat = true else onRecurrence(presetRule(preset, calendarSystem))
                }
            }
            Box {
                EditorRow(
                    Icons.Rounded.Folder,
                    stringResource(R.string.task_editor_project),
                    projects.firstOrNull { it.id == form.projectId }?.title ?: stringResource(R.string.tasks_no_project),
                    { projectMenu = true },
                )
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_no_project)) }, onClick = {
                        onUpdate { it.copy(projectId = null) }
                        projectMenu = false
                    })
                    projects.forEach { p ->
                        DropdownMenuItem(text = { Text(p.title) }, onClick = {
                            onUpdate { it.copy(projectId = p.id) }
                            projectMenu = false
                        })
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

            Text(stringResource(R.string.task_editor_tags), style = MaterialTheme.typography.labelLarge)
            if (form.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    form.tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tag.name) },
                            label = { Text("#${tag.name}") },
                            trailingIcon = {
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.task_editor_remove_tag, tag.name),
                                )
                            },
                        )
                    }
                }
            }
            PlannerTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = stringResource(R.string.task_editor_tag_hint),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    onAddTag(tagInput)
                    tagInput = ""
                }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerTextField(
                    value = form.estimate,
                    onValueChange = { v -> onUpdate { it.copy(estimate = v.take(5)) } },
                    label = stringResource(R.string.task_editor_estimate),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    isError = !form.estimateValid,
                    leadingIcon = Icons.Rounded.Timelapse,
                )
                PlannerTextField(
                    value = form.actual,
                    onValueChange = { v -> onUpdate { it.copy(actual = v.take(5)) } },
                    label = stringResource(R.string.task_editor_actual),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    isError = !form.actualValid,
                )
            }
            PlannerTextField(
                value = form.notes,
                onValueChange = { v -> onUpdate { it.copy(notes = v) } },
                label = stringResource(R.string.task_editor_notes),
                singleLine = false,
                minLines = 3,
            )

            if (form.parentTaskId == null) {
                PlannerSectionHeader(stringResource(R.string.task_editor_subtasks))
                subtasks.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedTaskCheckbox(sub.isCompleted, { onToggleSubtask(sub.id, it) }, contentDescription = sub.title)
                        Text(
                            sub.title,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenSubtask(sub.id) }
                                .padding(vertical = Spacing.md),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.tasks_action_delete), { onDeleteSubtask(sub.id) })
                    }
                }
                form.pendingSubtasks.forEachIndexed { index, title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedTaskCheckbox(false, {}, contentDescription = title)
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.tasks_action_delete), { onRemovePendingSubtask(index) })
                    }
                }
                PlannerTextField(
                    value = subtaskInput,
                    onValueChange = { subtaskInput = it },
                    label = stringResource(R.string.task_editor_subtask_hint),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = {
                        onAddSubtask(subtaskInput)
                        subtaskInput = ""
                    }),
                )
                PlannerButton(
                    stringResource(R.string.task_editor_add_subtask),
                    {
                        onAddSubtask(subtaskInput)
                        subtaskInput = ""
                    },
                    style = PlannerButtonStyle.Tonal,
                    enabled = subtaskInput.isNotBlank(),
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    when (picker) {
        "due", "start" -> PlannerDatePickerDialog(
            initial = if (picker == "due") form.due else form.start,
            onDismiss = { picker = null },
            onConfirm = { date ->
                if (picker == "due") onDueDate(date) else onUpdate { it.copy(startDate = date?.toEpochDay()) }
                picker = null
            },
        )
        "dueTime", "startTime" -> PlannerTimePickerDialog(
            initial = if (picker == "dueTime") form.dueAt else form.startAt,
            onDismiss = { picker = null },
            onConfirm = { time ->
                if (picker == "dueTime") {
                    if (form.dueDate == null && time != null) onDueDate(today)
                    onDueTime(time)
                    if (time != null) ensureNotificationPermission()
                } else {
                    onUpdate { it.copy(startTime = time?.toSecondOfDay()) }
                }
                picker = null
            },
        )
    }
    if (customRepeat) {
        CustomRecurrenceDialog(
            initial = form.rule,
            calendarSystem = calendarSystem,
            anchor = form.due ?: today,
            onDismiss = { customRepeat = false },
            onConfirm = {
                onRecurrence(it)
                customRepeat = false
            },
        )
    }
    if (confirmDelete) {
        PlannerDialog(
            title = stringResource(R.string.task_editor_delete),
            message = stringResource(R.string.task_editor_delete_confirm),
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(R.string.tasks_action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}
