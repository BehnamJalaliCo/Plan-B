package com.behnamjalali.planb.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerEmptyState
import com.behnamjalali.planb.core.designsystem.component.PlannerErrorState
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.ProjectStatus
import com.behnamjalali.planb.core.ui.PlannerProjectCard

@Composable
fun projectFilterLabel(status: ProjectStatus): String = stringResource(
    when (status) {
        ProjectStatus.ACTIVE -> R.string.projects_filter_active
        ProjectStatus.PAUSED -> R.string.projects_filter_paused
        ProjectStatus.COMPLETED -> R.string.projects_filter_completed
        ProjectStatus.ARCHIVED -> R.string.projects_filter_archived
    },
)

@Composable
fun ProjectsDestination(
    onBack: () -> Unit,
    onOpenProject: (EntityId) -> Unit,
    onNewProject: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProjectsScreen(state, onBack, onOpenProject, onNewProject, viewModel::setFilter)
}

@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onBack: (() -> Unit)?,
    onOpenProject: (EntityId) -> Unit,
    onNewProject: () -> Unit,
    onFilter: (ProjectStatus) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(
            title = stringResource(R.string.projects_title),
            onBack = onBack,
            actions = { PlannerIconButton(Icons.Rounded.Add, stringResource(R.string.projects_new), onNewProject) },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(ProjectStatus.entries) { status ->
                PlannerChip(projectFilterLabel(status), status == state.filter, { onFilter(status) })
            }
        }
        when {
            state.loading -> PlannerLoadingState()
            state.error -> PlannerErrorState(stringResource(R.string.projects_error))
            state.projects.isEmpty() -> PlannerEmptyState(
                icon = Icons.Rounded.RocketLaunch,
                title = stringResource(R.string.projects_empty_title),
                message = stringResource(if (state.filter == ProjectStatus.ACTIVE) R.string.projects_empty else R.string.projects_empty_filtered),
                actionLabel = stringResource(R.string.projects_new),
                onAction = onNewProject,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.projects, key = { it.project.id }) { summary ->
                    PlannerProjectCard(summary, Modifier.fillMaxWidth().animateItem(), onClick = { onOpenProject(summary.project.id) })
                }
            }
        }
    }
}
