package com.behnamjalali.planb.feature.calendar

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
import com.behnamjalali.planb.core.model.EntityId
import java.time.LocalDate

@Composable
fun CalendarDestination(
    onOpenEvent: (EntityId) -> Unit,
    onOpenTask: (EntityId) -> Unit,
    onNewEvent: (LocalDate) -> Unit,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                CalendarMessage.Failed -> snackbarHostState.showSnackbar(resources.getString(R.string.calendar_error))
                is CalendarMessage.Completed -> {
                    // A completed task leaves the calendar; offer the same undo as the task list.
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.calendar_task_completed),
                        actionLabel = resources.getString(R.string.calendar_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(message.taskId)
                }
            }
        }
    }
    CalendarScreen(
        state = state,
        contentPadding = contentPadding,
        callbacks = CalendarCallbacks(
            onViewChange = viewModel::setView,
            onSelect = viewModel::select,
            onPage = viewModel::page,
            onToday = viewModel::goToToday,
            onOpenEvent = onOpenEvent,
            onOpenTask = onOpenTask,
            onToggleTask = viewModel::setTaskCompleted,
            onNewEvent = onNewEvent,
        ),
    )
}
