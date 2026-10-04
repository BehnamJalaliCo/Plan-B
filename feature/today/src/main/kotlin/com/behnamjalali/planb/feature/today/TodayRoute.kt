package com.behnamjalali.planb.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerBottomSheet
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.DashboardConfig
import com.behnamjalali.planb.core.model.DashboardSection

@Composable
fun TodayDestination(
    actions: TodayActions,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var customizing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(R.string.capture_failed)) }
    }
    TodayScreen(
        state = state,
        actions = actions.copy(onCustomize = { customizing = true }),
        onToggleTask = viewModel::setTaskCompleted,
        onCheckInHabit = viewModel::checkInHabit,
        contentPadding = contentPadding,
    )
    val config = (state as? TodayUiState.Success)?.data?.dashboard
    if (customizing && config != null) {
        DashboardCustomizeSheet(
            config = config,
            onDismiss = { customizing = false },
            onMove = viewModel::moveSection,
            onVisibleChange = viewModel::setSectionVisible,
        )
    }
}

@Composable
fun sectionLabel(section: DashboardSection): String = stringResource(
    when (section) {
        DashboardSection.SUMMARY -> R.string.today_section_summary
        DashboardSection.TIMELINE -> R.string.today_section_timeline
        DashboardSection.TASKS -> R.string.today_section_tasks
        DashboardSection.UPCOMING -> R.string.today_section_upcoming
        DashboardSection.HABITS -> R.string.today_section_habits
        DashboardSection.FOCUS -> R.string.today_section_focus
        DashboardSection.PROJECTS -> R.string.today_section_projects
        DashboardSection.NOTES -> R.string.today_section_notes
    },
)

/** Show/hide and reorder sections; buttons (not only drag) keep it accessible. */
@Composable
fun DashboardCustomizeSheet(
    config: DashboardConfig,
    onDismiss: () -> Unit,
    onMove: (DashboardSection, Int) -> Unit,
    onVisibleChange: (DashboardSection, Boolean) -> Unit,
) {
    PlannerBottomSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.today_customize_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.today_customize_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            config.order.forEachIndexed { index, section ->
                val label = sectionLabel(section)
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PlannerIconButton(
                        Icons.Rounded.KeyboardArrowUp,
                        stringResource(R.string.today_move_up, label),
                        { onMove(section, -1) },
                        enabled = index > 0,
                    )
                    PlannerIconButton(
                        Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.today_move_down, label),
                        { onMove(section, 1) },
                        enabled = index < config.order.lastIndex,
                    )
                    Switch(checked = section !in config.hidden, onCheckedChange = { onVisibleChange(section, it) })
                }
            }
        }
    }
}
