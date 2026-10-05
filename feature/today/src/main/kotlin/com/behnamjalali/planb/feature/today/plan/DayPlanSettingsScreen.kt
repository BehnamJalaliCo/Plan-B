package com.behnamjalali.planb.feature.today.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material.icons.rounded.WorkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.DayPlanSettings
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.PlannerTimePickerDialog
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.rememberNotificationPermissionRequest
import com.behnamjalali.planb.feature.today.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Working hours and ritual reminders live in the user preferences (exported with a backup). */
@HiltViewModel
class DayPlanSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<DayPlanSettings?> = settings.settings.map { it.dayPlan }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (DayPlanSettings) -> DayPlanSettings) {
        viewModelScope.launch { settings.update { it.copy(dayPlan = transform(it.dayPlan)) } }
    }
}

@Composable
fun DayPlanSettingsDestination(onBack: () -> Unit, viewModel: DayPlanSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.dayplan_settings_title), onBack = onBack)
        // Settings made while Pro was active stay editable as a teaser only; reminders already set keep working.
        ProGate(
            ProFeature.AUTO_PLANNING,
            teaser = {
                Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ProTeaser(ProFeature.AUTO_PLANNING)
                    ProTeaser(ProFeature.DAILY_RITUALS)
                }
            },
        ) {
            val s = state
            if (s == null) PlannerLoadingState() else DayPlanSettingsScreen(s, viewModel::update)
        }
    }
}

private enum class TimeField { WORK_START, WORK_END, LUNCH_START, LUNCH_END, MORNING, EVENING }

@Composable
fun DayPlanSettingsScreen(settings: DayPlanSettings, onUpdate: ((DayPlanSettings) -> DayPlanSettings) -> Unit) {
    val formatter = PlannerLocals.formatter
    val askPermission = rememberNotificationPermissionRequest()
    var editing by rememberSaveable { mutableStateOf<TimeField?>(null) }
    var choosingBuffer by rememberSaveable { mutableStateOf(false) }
    val off = stringResource(R.string.dayplan_off)
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item { PlannerSectionHeader(stringResource(R.string.dayplan_hours_section)) }
        item { TimeRow(stringResource(R.string.dayplan_start), Icons.Rounded.WorkHistory, formatter.time(settings.workStart)) { editing = TimeField.WORK_START } }
        item { TimeRow(stringResource(R.string.dayplan_end), Icons.Rounded.WorkOff, formatter.time(settings.workEnd)) { editing = TimeField.WORK_END } }
        if (!settings.hasWorkingHours) {
            item {
                Text(
                    stringResource(R.string.dayplan_hours_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        item {
            SwitchRow(
                stringResource(R.string.dayplan_lunch),
                Icons.Rounded.LunchDining,
                if (settings.lunchEnabled) formatter.timeRange(settings.lunchStart, settings.lunchEnd) else off,
                settings.lunchEnabled,
            ) { on -> onUpdate { it.copy(lunchEnabled = on) } }
        }
        if (settings.lunchEnabled) {
            item { TimeRow(stringResource(R.string.dayplan_lunch_start), null, formatter.time(settings.lunchStart)) { editing = TimeField.LUNCH_START } }
            item { TimeRow(stringResource(R.string.dayplan_lunch_end), null, formatter.time(settings.lunchEnd)) { editing = TimeField.LUNCH_END } }
        }
        item {
            TimeRow(stringResource(R.string.dayplan_buffer), Icons.Rounded.SpaceBar, bufferLabel(settings.bufferMinutes)) { choosingBuffer = true }
        }
        item { PlannerSectionHeader(stringResource(R.string.dayplan_rituals_section)) }
        item {
            SwitchRow(
                stringResource(R.string.dayplan_morning_reminder),
                Icons.Rounded.WbSunny,
                if (settings.morningReminder) formatter.time(settings.morningTime) else off,
                settings.morningReminder,
            ) { on ->
                if (on) askPermission()
                onUpdate { it.copy(morningReminder = on) }
            }
        }
        if (settings.morningReminder) {
            item { TimeRow(stringResource(R.string.dayplan_reminder_time), Icons.Rounded.Alarm, formatter.time(settings.morningTime)) { editing = TimeField.MORNING } }
        }
        item {
            SwitchRow(
                stringResource(R.string.dayplan_evening_reminder),
                Icons.Rounded.Bedtime,
                if (settings.eveningReminder) formatter.time(settings.eveningTime) else off,
                settings.eveningReminder,
            ) { on ->
                if (on) askPermission()
                onUpdate { it.copy(eveningReminder = on) }
            }
        }
        if (settings.eveningReminder) {
            item { TimeRow(stringResource(R.string.dayplan_reminder_time), Icons.Rounded.WbTwilight, formatter.time(settings.eveningTime)) { editing = TimeField.EVENING } }
        }
        item {
            Text(
                stringResource(R.string.dayplan_reminder_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }

    editing?.let { field ->
        val initial = when (field) {
            TimeField.WORK_START -> settings.workStart
            TimeField.WORK_END -> settings.workEnd
            TimeField.LUNCH_START -> settings.lunchStart
            TimeField.LUNCH_END -> settings.lunchEnd
            TimeField.MORNING -> settings.morningTime
            TimeField.EVENING -> settings.eveningTime
        }
        PlannerTimePickerDialog(
            initial = initial,
            onDismiss = { editing = null },
            allowClear = false,
            onConfirm = { value: LocalTime? ->
                val picked = value ?: initial
                onUpdate {
                    when (field) {
                        TimeField.WORK_START -> it.copy(workStart = picked)
                        TimeField.WORK_END -> it.copy(workEnd = picked)
                        TimeField.LUNCH_START -> it.copy(lunchStart = picked)
                        TimeField.LUNCH_END -> it.copy(lunchEnd = picked)
                        TimeField.MORNING -> it.copy(morningTime = picked)
                        TimeField.EVENING -> it.copy(eveningTime = picked)
                    }
                }
                editing = null
            },
        )
    }
    if (choosingBuffer) {
        PlannerDialog(
            title = stringResource(R.string.dayplan_buffer),
            onDismiss = { choosingBuffer = false },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel),
            dismissLabel = "",
            onConfirm = { choosingBuffer = false },
        ) {
            DayPlanSettings.BUFFER_CHOICES.forEach { minutes ->
                SettingsRow(
                    bufferLabel(minutes),
                    onClick = {
                        onUpdate { it.copy(bufferMinutes = minutes) }
                        choosingBuffer = false
                    },
                    trailing = if (minutes == settings.bufferMinutes) {
                        { RadioButton(selected = true, onClick = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun bufferLabel(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.plan_no_buffer) else PlannerLocals.formatter.duration(minutes)

@Composable
private fun TimeRow(title: String, icon: ImageVector?, value: String, onClick: () -> Unit) {
    SettingsRow(title, icon = icon, subtitle = value, onClick = onClick)
}

@Composable
private fun SwitchRow(title: String, icon: ImageVector, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}
