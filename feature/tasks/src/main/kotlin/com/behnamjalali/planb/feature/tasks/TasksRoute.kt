package com.behnamjalali.planb.feature.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.ui.PlannerLocals

@Composable
fun TasksDestination(
    onOpenTask: (EntityId) -> Unit,
    onNewTask: () -> Unit,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val numbers: NumberFormatter = PlannerLocals.numbers
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            // The snackbar now owns this delete's undo; one that was already committed shows nothing.
            if (message is TasksMessage.Deleted && !viewModel.holdDelete(message.token)) return@collect
            // A newer message replaces the visible snackbar instead of queueing behind it, so every
            // undo is offered while its task is still pending. Each snackbar runs on its own.
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                when (message) {
                    is TasksMessage.Deleted -> {
                        var undo = false
                        try {
                            undo = snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.tasks_deleted, numbers.format(message.count)),
                                actionLabel = resources.getString(R.string.tasks_undo),
                                duration = SnackbarDuration.Short,
                            ) == SnackbarResult.ActionPerformed
                        } finally {
                            // Leaving the screen while the snackbar shows commits the delete right away.
                            if (undo) viewModel.undoDelete(message.token) else viewModel.commitDelete(message.token)
                        }
                    }
                    is TasksMessage.Completed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.tasks_completed_message),
                            actionLabel = resources.getString(R.string.tasks_undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(message.taskId, message.nextOccurrenceId)
                    }
                    TasksMessage.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.tasks_error))
                }
            }
        }
    }
    TasksScreen(
        state = state,
        contentPadding = contentPadding,
        callbacks = TasksCallbacks(
            onViewChange = viewModel::setView,
            onProjectFilter = viewModel::setProject,
            onTagFilter = viewModel::setTag,
            onSortChange = viewModel::setSort,
            onQueryChange = viewModel::setQuery,
            onOpenTask = onOpenTask,
            onNewTask = onNewTask,
            onToggleComplete = viewModel::setCompleted,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onCompleteSelected = viewModel::completeSelected,
            onArchiveSelected = viewModel::archiveSelected,
            onMoveSelected = viewModel::moveSelected,
            onDelete = { ids -> viewModel.requestDelete(ids) },
            onDuplicate = viewModel::duplicate,
            onMove = viewModel::move,
        ),
    )
}
