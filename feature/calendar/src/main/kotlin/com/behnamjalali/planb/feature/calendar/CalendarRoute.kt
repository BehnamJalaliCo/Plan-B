package com.behnamjalali.planb.feature.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.failures.collect { snackbarHostState.showSnackbar(context.getString(R.string.calendar_error)) }
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
