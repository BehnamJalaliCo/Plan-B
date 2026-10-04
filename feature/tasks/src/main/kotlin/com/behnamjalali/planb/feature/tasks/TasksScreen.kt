package com.behnamjalali.planb.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSearchBar
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTaskCard
import kotlinx.coroutines.launch

@Composable
fun taskViewLabel(view: TaskView): String = stringResource(
    when (view) {
        TaskView.INBOX -> R.string.tasks_view_inbox
        TaskView.TODAY -> R.string.tasks_view_today
        TaskView.UPCOMING -> R.string.tasks_view_upcoming
        TaskView.SCHEDULED -> R.string.tasks_view_scheduled
        TaskView.COMPLETED -> R.string.tasks_view_completed
        TaskView.ARCHIVED -> R.string.tasks_view_archived
        TaskView.ALL -> R.string.tasks_view_all
    },
)

@Composable
private fun sortLabel(sort: TaskSort): String = stringResource(
    when (sort) {
        TaskSort.MANUAL -> R.string.tasks_sort_manual
        TaskSort.DUE_DATE -> R.string.tasks_sort_due
        TaskSort.PRIORITY -> R.string.tasks_sort_priority
        TaskSort.CREATED -> R.string.tasks_sort_created
        TaskSort.TITLE -> R.string.tasks_sort_title
    },
)

/** All user intents of the task list, so the screen stays stateless and testable. */
data class TasksCallbacks(
    val onViewChange: (TaskView) -> Unit = {},
    val onProjectFilter: (EntityId?) -> Unit = {},
    val onTagFilter: (EntityId?) -> Unit = {},
    val onSortChange: (TaskSort) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onOpenTask: (EntityId) -> Unit = {},
    val onNewTask: () -> Unit = {},
    val onToggleComplete: (EntityId, Boolean) -> Unit = { _, _ -> },
    val onToggleSelection: (EntityId) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onCompleteSelected: (Boolean) -> Unit = {},
    val onArchiveSelected: (Boolean) -> Unit = {},
    val onMoveSelected: (EntityId?) -> Unit = {},
    val onDelete: (Collection<EntityId>) -> Unit = {},
    val onDuplicate: (EntityId) -> Unit = {},
    val onMove: (EntityId, Int) -> Unit = { _, _ -> },
)

@Composable
fun TasksScreen(
    state: TasksUiState,
    callbacks: TasksCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(modifier.fillMaxSize()) {
        if (state.selecting) {
            SelectionBar(state, callbacks)
        } else {
            var sortMenu by remember { mutableStateOf(false) }
            PlannerTopBar(
                title = stringResource(R.string.tasks_title),
                actions = {
                    Box {
                        PlannerIconButton(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.tasks_sort), { sortMenu = true })
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TaskSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    leadingIcon = if (sort == state.filter.sort) {
                                        { Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(com.behnamjalali.planb.core.designsystem.R.string.ds_selected)) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        callbacks.onSortChange(sort)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.tasks_new), callbacks.onNewTask)
                },
            )
        }
        PlannerSearchBar(
            query = state.filter.query,
            onQueryChange = callbacks.onQueryChange,
            placeholder = stringResource(R.string.tasks_search_hint),
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(VIEW_ORDER) { view ->
                PlannerChip(
                    label = taskViewLabel(view),
                    selected = view == state.filter.view,
                    onClick = { callbacks.onViewChange(view) },
                    icon = when (view) {
                        TaskView.INBOX -> Icons.Rounded.Inbox
                        TaskView.TODAY -> Icons.Rounded.WbSunny
                        TaskView.COMPLETED -> Icons.Rounded.CheckCircle
                        TaskView.ARCHIVED -> Icons.Rounded.Archive
                        else -> null
                    },
                )
            }
        }
        FilterRow(state, callbacks)
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.tasks_error))
            state.tasks.isEmpty() -> EmptyTasks(state, callbacks)
            else -> TaskList(state, callbacks, contentPadding)
        }
    }
}

@Composable
private fun FilterRow(state: TasksUiState, callbacks: TasksCallbacks) {
    var projectMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    val project = state.filter.projectId?.let { state.projectNames[it] }
    val tag = state.filter.tagId?.let { id -> state.tags.firstOrNull { it.id == id } }
    Row(
        Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box {
            PlannerChip(
                label = project?.title ?: stringResource(R.string.tasks_filter_any_project),
                selected = project != null,
                onClick = { projectMenu = true },
                icon = Icons.Rounded.Folder,
            )
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tasks_filter_any_project)) }, onClick = {
                    callbacks.onProjectFilter(null)
                    projectMenu = false
                })
                state.projects.forEach { p ->
                    DropdownMenuItem(text = { Text(p.title) }, onClick = {
                        callbacks.onProjectFilter(p.id)
                        projectMenu = false
                    })
                }
            }
        }
        if (state.tags.isNotEmpty() || tag != null) {
            Box {
                PlannerChip(
                    label = tag?.let { "#${it.name}" } ?: stringResource(R.string.tasks_filter_any_tag),
                    selected = tag != null,
                    onClick = { tagMenu = true },
                    icon = Icons.AutoMirrored.Rounded.Label,
                )
                DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_filter_any_tag)) }, onClick = {
                        callbacks.onTagFilter(null)
                        tagMenu = false
                    })
                    state.tags.forEach { t ->
                        DropdownMenuItem(text = { Text("#${t.name}") }, onClick = {
                            callbacks.onTagFilter(t.id)
                            tagMenu = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasks(state: TasksUiState, callbacks: TasksCallbacks) {
    val filtered = state.filter.query.isNotBlank() || state.filter.projectId != null || state.filter.tagId != null
    val (title, message) = when {
        filtered -> R.string.tasks_empty_generic_title to R.string.tasks_empty_filtered
        state.filter.view == TaskView.INBOX -> R.string.tasks_empty_inbox_title to R.string.tasks_empty_inbox
        state.filter.view == TaskView.TODAY -> R.string.tasks_empty_today_title to R.string.tasks_empty_today
        else -> R.string.tasks_empty_generic_title to R.string.tasks_empty_generic
    }
    PlannerEmptyState(
        icon = if (state.filter.view == TaskView.TODAY) Icons.Rounded.WbSunny else Icons.Rounded.Inbox,
        title = stringResource(title),
        message = stringResource(message),
        actionLabel = stringResource(R.string.tasks_new),
        onAction = callbacks.onNewTask,
    )
}

@Composable
private fun TaskList(state: TasksUiState, callbacks: TasksCallbacks, contentPadding: PaddingValues) {
    val projects = state.projectNames
    val moveUp = stringResource(R.string.tasks_action_move_up)
    val moveDown = stringResource(R.string.tasks_action_move_down)
    val deleteLabel = stringResource(R.string.tasks_action_delete)
    val duplicateLabel = stringResource(R.string.tasks_action_duplicate)
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(state.tasks, key = { it.id }, contentType = { "task" }) { task ->
            val project = task.projectId?.let { projects[it] }
            val actions = buildList {
                if (state.canReorder) {
                    add(CustomAccessibilityAction(moveUp) { callbacks.onMove(task.id, -1); true })
                    add(CustomAccessibilityAction(moveDown) { callbacks.onMove(task.id, 1); true })
                }
                add(CustomAccessibilityAction(duplicateLabel) { callbacks.onDuplicate(task.id); true })
                add(CustomAccessibilityAction(deleteLabel) { callbacks.onDelete(listOf(task.id)); true })
            }
            SwipeableTask(
                enabled = !state.selecting,
                onComplete = { callbacks.onToggleComplete(task.id, !task.isCompleted) },
                onDelete = { callbacks.onDelete(listOf(task.id)) },
                modifier = Modifier
                    .animateItem()
                    .semantics { customActions = actions },
            ) {
                PlannerTaskCard(
                    task = task,
                    onToggleComplete = { callbacks.onToggleComplete(task.id, it) },
                    onClick = {
                        if (state.selecting) callbacks.onToggleSelection(task.id) else callbacks.onOpenTask(task.id)
                    },
                    onLongClick = { callbacks.onToggleSelection(task.id) },
                    projectName = project?.title,
                    projectColor = project?.color,
                    selected = task.id in state.selection,
                )
            }
        }
    }
}

@Composable
private fun SwipeableTask(
    enabled: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val colors = PlanBTheme.colors
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        gesturesEnabled = enabled,
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, icon, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(colors.success, Icons.Rounded.CheckCircle, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.error, Icons.Rounded.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(androidx.compose.ui.graphics.Color.Transparent, null, Alignment.Center)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.18f), RoundedCornerShape(Radius.lg))
                    .padding(horizontal = Spacing.xl),
                contentAlignment = alignment,
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = color)
            }
        },
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onComplete()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
    ) {
        content()
    }
}

@Composable
private fun SelectionBar(state: TasksUiState, callbacks: TasksCallbacks) {
    val numbers = PlannerLocals.numbers
    var moveMenu by remember { mutableStateOf(false) }
    val archivedView = state.filter.view == TaskView.ARCHIVED
    val completedView = state.filter.view == TaskView.COMPLETED
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlannerIconButton(Icons.Rounded.Close, stringResource(R.string.tasks_clear_selection), callbacks.onClearSelection)
            Text(
                stringResource(R.string.tasks_selected_count, numbers.format(state.selection.size)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PlannerIconButton(Icons.Rounded.SelectAll, stringResource(R.string.tasks_select), callbacks.onSelectAll)
            PlannerIconButton(
                Icons.Rounded.CheckCircle,
                stringResource(if (completedView) R.string.tasks_action_uncomplete else R.string.tasks_action_complete),
                { callbacks.onCompleteSelected(!completedView) },
            )
            Box {
                PlannerIconButton(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.tasks_action_move), { moveMenu = true })
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_no_project)) }, onClick = {
                        callbacks.onMoveSelected(null)
                        moveMenu = false
                    })
                    state.projects.forEach { p ->
                        DropdownMenuItem(text = { Text(p.title) }, onClick = {
                            callbacks.onMoveSelected(p.id)
                            moveMenu = false
                        })
                    }
                }
            }
            PlannerIconButton(
                if (archivedView) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                stringResource(if (archivedView) R.string.tasks_action_unarchive else R.string.tasks_action_archive),
                { callbacks.onArchiveSelected(!archivedView) },
            )
            PlannerIconButton(Icons.Rounded.Delete, stringResource(R.string.tasks_action_delete), { callbacks.onDelete(state.selection) })
        }
    }
}

private val VIEW_ORDER = listOf(
    TaskView.TODAY,
    TaskView.INBOX,
    TaskView.UPCOMING,
    TaskView.SCHEDULED,
    TaskView.ALL,
    TaskView.COMPLETED,
    TaskView.ARCHIVED,
)
