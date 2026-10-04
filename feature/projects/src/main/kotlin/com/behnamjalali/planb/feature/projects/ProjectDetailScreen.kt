package com.behnamjalali.planb.feature.projects

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.AnimatedTaskCheckbox
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerProgressRing
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.ui.ConfirmDeleteDialog
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTaskCard
import com.behnamjalali.planb.core.ui.projectStatusLabel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

enum class ProjectTab(val label: Int) {
    OVERVIEW(R.string.project_tab_overview),
    TASKS(R.string.project_tab_tasks),
    MILESTONES(R.string.project_tab_milestones),
    NOTES(R.string.project_tab_notes),
    BOARD(R.string.project_tab_board),
}

data class ProjectDetailCallbacks(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onOpenTask: (EntityId) -> Unit = {},
    val onAddTask: (String) -> Unit = {},
    val onToggleTask: (EntityId, Boolean) -> Unit = { _, _ -> },
    val onTaskStatus: (EntityId, TaskStatus) -> Unit = { _, _ -> },
    val onAddMilestone: (String, java.time.LocalDate?) -> Unit = { _, _ -> },
    val onToggleMilestone: (ProjectMilestone) -> Unit = {},
    val onDeleteMilestone: (EntityId) -> Unit = {},
    val onStatus: (ProjectStatus) -> Unit = {},
    val onArchive: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onNotes: (String) -> Unit = {},
)

@Composable
fun ProjectDetailDestination(
    onBack: () -> Unit,
    onEdit: (EntityId) -> Unit,
    onOpenTask: (EntityId) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProjectEvent.Deleted -> onBack()
                ProjectEvent.Failed -> snackbarHostState.showSnackbar(resources.getString(com.behnamjalali.planb.core.ui.R.string.ui_error_generic))
                ProjectEvent.NotesSaved -> Unit
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    ProjectDetailScreen(
        state = state,
        callbacks = ProjectDetailCallbacks(
            onBack = onBack,
            onEdit = { state.summary?.project?.id?.let(onEdit) },
            onOpenTask = onOpenTask,
            onAddTask = viewModel::addTask,
            onToggleTask = viewModel::setTaskCompleted,
            onTaskStatus = viewModel::setTaskStatus,
            onAddMilestone = viewModel::addMilestone,
            onToggleMilestone = viewModel::toggleMilestone,
            onDeleteMilestone = viewModel::deleteMilestone,
            onStatus = viewModel::setStatus,
            onArchive = viewModel::setArchived,
            onDelete = viewModel::delete,
            onNotes = viewModel::updateNotes,
        ),
    )
}

@Composable
fun ProjectDetailScreen(state: ProjectDetailUiState, callbacks: ProjectDetailCallbacks, initialTab: ProjectTab = ProjectTab.OVERVIEW) {
    val summary = state.summary
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = callbacks.onBack)
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = summary?.project?.title.orEmpty(),
            onBack = callbacks.onBack,
            actions = {
                Box {
                    PlannerIconButton(Icons.Rounded.MoreVert, stringResource(com.behnamjalali.planb.core.ui.R.string.ui_more), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.project_edit)) }, onClick = {
                            menu = false
                            callbacks.onEdit()
                        })
                        val archived = summary?.project?.archived == true
                        DropdownMenuItem(text = { Text(stringResource(if (archived) R.string.project_unarchive else R.string.project_archive)) }, onClick = {
                            menu = false
                            callbacks.onArchive(!archived)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.project_delete)) }, onClick = {
                            menu = false
                            confirmDelete = true
                        })
                    }
                }
            },
        )
        if (state.loading || summary == null) {
            PlannerLoadingState()
            return@Column
        }
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = Spacing.screen, containerColor = MaterialTheme.colorScheme.background) {
            ProjectTab.entries.forEach { t ->
                Tab(selected = tab == t.ordinal, onClick = { tab = t.ordinal }, text = { Text(stringResource(t.label)) })
            }
        }
        when (ProjectTab.entries[tab]) {
            ProjectTab.OVERVIEW -> Overview(state, callbacks)
            ProjectTab.TASKS -> TasksTab(state, callbacks)
            ProjectTab.MILESTONES -> MilestonesTab(state, callbacks)
            ProjectTab.NOTES -> NotesTab(summary.project.description, callbacks.onNotes)
            ProjectTab.BOARD -> BoardTab(state, callbacks)
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.project_delete),
            message = stringResource(R.string.project_delete_confirm),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                callbacks.onDelete()
            },
        )
    }
}

@Composable
private fun Overview(state: ProjectDetailUiState, callbacks: ProjectDetailCallbacks) {
    val summary = state.summary ?: return
    val project = summary.project
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val tones = PlanBTheme.colors.accent(project.color)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PlannerHeroSurface(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlannerProgressRing(summary.progress, size = 84.dp, strokeWidth = 9.dp, color = tones.strong, trackColor = tones.container) {
                    Text(numbers.percent(summary.progress), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.project_progress), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.project_tasks_done, numbers.format(summary.completedTasks), numbers.format(summary.totalTasks)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (summary.totalMilestones > 0) {
                        Text(
                            stringResource(R.string.project_milestones_done, numbers.format(summary.completedMilestones), numbers.format(summary.totalMilestones)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (project.description.isNotBlank()) {
            Text(project.description, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        PlannerSectionHeader(stringResource(R.string.project_status))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(listOf(ProjectStatus.ACTIVE, ProjectStatus.PAUSED, ProjectStatus.COMPLETED)) { status ->
                PlannerChip(projectStatusLabel(status), project.status == status, { callbacks.onStatus(status) })
            }
        }
        if (project.startDate != null || project.dueDate != null) {
            PlannerSectionHeader(stringResource(R.string.project_dates))
            project.startDate?.let { Text("${stringResource(R.string.project_start)}: ${formatter.mediumDate(it)}", style = MaterialTheme.typography.bodyMedium) }
            project.dueDate?.let { Text("${stringResource(R.string.project_due)}: ${formatter.relativeDate(it, today)}", style = MaterialTheme.typography.bodyMedium) }
        }
        val nextMilestone = state.milestones.firstOrNull { !it.completed }
        if (nextMilestone != null) {
            PlannerSectionHeader(stringResource(R.string.project_tab_milestones))
            MilestoneRow(nextMilestone, callbacks)
        }
        if (state.openTasks.isNotEmpty()) {
            PlannerSectionHeader(stringResource(R.string.project_tab_tasks))
            state.openTasks.take(3).forEach { task ->
                PlannerTaskCard(task, { callbacks.onToggleTask(task.id, it) }, onClick = { callbacks.onOpenTask(task.id) })
            }
        }
    }
}

@Composable
private fun AddRow(hint: Int, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    PlannerTextField(
        value = text,
        onValueChange = { text = it },
        label = stringResource(hint),
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = {
            onAdd(text)
            text = ""
        }),
    )
}

@Composable
private fun TasksTab(state: ProjectDetailUiState, callbacks: ProjectDetailCallbacks) {
    LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(key = "add") { AddRow(R.string.project_task_hint, callbacks.onAddTask) }
        if (state.openTasks.isEmpty() && state.completedTasks.isEmpty()) {
            item(key = "empty") { Text(stringResource(R.string.project_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.openTasks, key = { it.id }) { task ->
            PlannerTaskCard(task, { callbacks.onToggleTask(task.id, it) }, Modifier.animateItem(), onClick = { callbacks.onOpenTask(task.id) })
        }
        if (state.completedTasks.isNotEmpty()) {
            item(key = "done_header") { PlannerSectionHeader(stringResource(R.string.project_completed_tasks)) }
            items(state.completedTasks, key = { "d${it.id}" }) { task ->
                PlannerTaskCard(task, { callbacks.onToggleTask(task.id, it) }, Modifier.animateItem(), onClick = { callbacks.onOpenTask(task.id) })
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: ProjectMilestone, callbacks: ProjectDetailCallbacks) {
    val formatter = PlannerLocals.formatter
    PlannerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedTaskCheckbox(milestone.completed, { callbacks.onToggleMilestone(milestone) }, contentDescription = milestone.title)
            Column(Modifier.weight(1f)) {
                Text(milestone.title, style = MaterialTheme.typography.bodyLarge)
                milestone.date?.let {
                    Text(formatter.relativeDate(it, PlannerLocals.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.project_delete_milestone), { callbacks.onDeleteMilestone(milestone.id) })
        }
    }
}

@Composable
private fun MilestonesTab(state: ProjectDetailUiState, callbacks: ProjectDetailCallbacks) {
    var title by rememberSaveable { mutableStateOf("") }
    var dateEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val formatter = PlannerLocals.formatter
    LazyColumn(contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        item(key = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.project_milestone_hint),
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = {
                        callbacks.onAddMilestone(title, dateEpoch?.let(java.time.LocalDate::ofEpochDay))
                        title = ""
                        dateEpoch = null
                    }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlannerChip(
                        dateEpoch?.let { formatter.mediumDate(java.time.LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.project_milestone_date),
                        dateEpoch != null,
                        { picking = true },
                        icon = Icons.Rounded.Event,
                    )
                    PlannerChip(stringResource(R.string.project_add_milestone), false, {
                        callbacks.onAddMilestone(title, dateEpoch?.let(java.time.LocalDate::ofEpochDay))
                        title = ""
                        dateEpoch = null
                    })
                }
            }
        }
        if (state.milestones.isEmpty()) {
            item(key = "empty") {
                PlannerEmptyState(Icons.Rounded.Event, stringResource(R.string.project_tab_milestones), stringResource(R.string.project_no_milestones))
            }
        }
        items(state.milestones, key = { it.id }) { MilestoneRow(it, callbacks) }
    }
    if (picking) {
        PlannerDatePickerDialog(dateEpoch?.let(java.time.LocalDate::ofEpochDay), { picking = false }, {
            dateEpoch = it?.toEpochDay()
            picking = false
        })
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun NotesTab(initial: String, onNotes: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { text }.debounce(300).collect { if (it != initial) onNotes(it) }
    }
    Column(Modifier.fillMaxSize().padding(Spacing.screen)) {
        PlannerTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.project_tab_notes),
            placeholder = stringResource(R.string.project_notes_hint),
            singleLine = false,
            minLines = 10,
        )
    }
}

@Composable
private fun BoardTab(state: ProjectDetailUiState, callbacks: ProjectDetailCallbacks) {
    val all = state.openTasks + state.completedTasks
    val columns = listOf(
        TaskStatus.TODO to R.string.project_board_todo,
        TaskStatus.IN_PROGRESS to R.string.project_board_in_progress,
        TaskStatus.DONE to R.string.project_board_done,
    )
    LazyRow(
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(columns, key = { it.first }) { (status, label) ->
            val tasks = all.filter { it.status == status }
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlannerSectionHeader(stringResource(label), trailing = PlannerLocals.numbers.format(tasks.size))
                if (tasks.isEmpty()) {
                    Text(stringResource(R.string.project_board_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tasks.forEach { task -> BoardCard(task, status, callbacks) }
            }
        }
    }
}

@Composable
private fun BoardCard(task: Task, status: TaskStatus, callbacks: ProjectDetailCallbacks) {
    PlannerCard(Modifier.fillMaxWidth(), onClick = { callbacks.onOpenTask(task.id) }, contentPadding = PaddingValues(Spacing.sm)) {
        Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(Spacing.xs))
        Spacer(Modifier.height(Spacing.xs))
        Row {
            if (status != TaskStatus.TODO) {
                PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.project_board_move_back), {
                    callbacks.onTaskStatus(task.id, TaskStatus.entries[status.ordinal - 1])
                })
            }
            Spacer(Modifier.weight(1f))
            if (status != TaskStatus.DONE) {
                PlannerIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.project_board_move_forward), {
                    callbacks.onTaskStatus(task.id, TaskStatus.entries[status.ordinal + 1])
                })
            }
        }
    }
}
