package com.behnamjalali.planb.feature.calendar

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.calendarsync.ContentResolverCalendarStore
import com.behnamjalali.planb.core.calendarsync.DeviceCalendar
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.CalendarDecorations
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import java.time.Instant
import java.time.ZoneId

@Composable
fun CalendarSettingsDestination(onBack: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onPermissionResult(result.values.isNotEmpty() && result.values.all { it })
    }
    CalendarSettingsScreen(
        state = state,
        onBack = onBack,
        actions = CalendarSettingsActions(
            onDecorations = viewModel::setDecorations,
            onEnableSync = { if (state.permission) viewModel.onPermissionResult(true) else launcher.launch(ContentResolverCalendarStore.PERMISSIONS) },
            onDisableSync = viewModel::disable,
            onVisible = viewModel::setVisible,
            onTarget = viewModel::setTarget,
            onCreateLocal = viewModel::createLocalCalendar,
            onSyncNow = viewModel::syncNow,
        ),
    )
}

data class CalendarSettingsActions(
    val onDecorations: ((CalendarDecorations) -> CalendarDecorations) -> Unit = {},
    /** Asks for the calendar permission (after the rationale) and turns sync on. */
    val onEnableSync: () -> Unit = {},
    val onDisableSync: () -> Unit = {},
    val onVisible: (Long, Boolean) -> Unit = { _, _ -> },
    val onTarget: (Long?) -> Unit = {},
    val onCreateLocal: (name: String, color: Int) -> Unit = { _, _ -> },
    val onSyncNow: () -> Unit = {},
)

@Composable
fun CalendarSettingsScreen(state: CalendarSettingsUiState, onBack: () -> Unit, actions: CalendarSettingsActions) {
    var rationale by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.calendar_settings_title), onBack = onBack)
        if (state.loading) {
            PlannerLoadingState()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { PlannerSectionHeader(stringResource(R.string.calendar_settings_official)) }
            item {
                ProGate(ProFeature.IRAN_HOLIDAYS) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        val d = state.decorations
                        SwitchRow(stringResource(R.string.calendar_settings_holidays), stringResource(R.string.calendar_settings_holidays_summary), Icons.Rounded.Celebration, d.holidays) { v ->
                            actions.onDecorations { it.copy(holidays = v) }
                        }
                        SwitchRow(stringResource(R.string.calendar_settings_occasions), stringResource(R.string.calendar_settings_occasions_summary), Icons.Rounded.Event, d.occasions) { v ->
                            actions.onDecorations { it.copy(occasions = v) }
                        }
                        SwitchRow(stringResource(R.string.calendar_settings_hijri), stringResource(R.string.calendar_settings_hijri_summary), Icons.Rounded.DarkMode, d.hijriDate) { v ->
                            actions.onDecorations { it.copy(hijriDate = v) }
                        }
                        Note(stringResource(R.string.calendar_settings_lunar_source))
                    }
                }
            }
            item { PlannerSectionHeader(stringResource(R.string.calendar_settings_device)) }
            item {
                ProGate(ProFeature.CALENDAR_SYNC) {
                    SwitchRow(
                        stringResource(R.string.calendar_settings_sync),
                        stringResource(R.string.calendar_settings_sync_summary),
                        Icons.Rounded.Sync,
                        state.sync.enabled && state.permission,
                    ) { on -> if (on) rationale = true else actions.onDisableSync() }
                }
            }
            if (state.sync.enabled) syncDetails(state, actions)
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(R.string.calendar_settings_permission_title)) },
            text = { Text(stringResource(R.string.calendar_settings_permission_text)) },
            confirmButton = {
                TextButton(onClick = { rationale = false; actions.onEnableSync() }) { Text(stringResource(R.string.calendar_settings_permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.calendar_settings_permission_later)) }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.syncDetails(state: CalendarSettingsUiState, actions: CalendarSettingsActions) {
    if (!state.permission) {
        item(key = "denied") { PermissionDenied() }
        return
    }
    item(key = "status") { SyncStatus(state, actions) }
    item(key = "show_header") { PlannerSectionHeader(stringResource(R.string.calendar_settings_show)) }
    if (state.calendars.isEmpty()) {
        item(key = "none") { Note(stringResource(R.string.calendar_settings_no_calendars)) }
    }
    items(state.calendars.filterNot { it.ownedByPlanB }, key = { "show_${it.id}" }) { calendar ->
        val checked = calendar.id in state.sync.visibleCalendarIds
        CalendarRow(calendar, Modifier.toggleable(checked, role = Role.Checkbox) { actions.onVisible(calendar.id, it) }) {
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
    item(key = "write_header") { PlannerSectionHeader(stringResource(R.string.calendar_settings_write)) }
    item(key = "write_none") {
        val selected = state.sync.targetCalendarId == null
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .selectable(selected, role = Role.RadioButton) { actions.onTarget(null) }
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.calendar_settings_write_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null)
        }
    }
    items(state.calendars.filter { it.writable }, key = { "write_${it.id}" }) { calendar ->
        val selected = state.sync.targetCalendarId == calendar.id
        CalendarRow(calendar, Modifier.selectable(selected, role = Role.RadioButton) { actions.onTarget(calendar.id) }) {
            RadioButton(selected = selected, onClick = null)
        }
    }
    if (state.calendars.none { it.ownedByPlanB }) {
        item(key = "create_local") {
            val name = stringResource(R.string.calendar_settings_local_name)
            val color = MaterialTheme.colorScheme.primary.toArgb()
            PlannerButton(
                text = stringResource(R.string.calendar_settings_create_local),
                onClick = { actions.onCreateLocal(name, color) },
                style = PlannerButtonStyle.Tonal,
                icon = Icons.Rounded.Add,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
    }
    item(key = "rules") { Note(stringResource(R.string.calendar_settings_sync_rules)) }
}

@Composable
private fun SyncStatus(state: CalendarSettingsUiState, actions: CalendarSettingsActions) {
    val formatter = PlannerLocals.formatter
    val today = PlannerLocals.today
    val last = state.sync.lastSyncAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
    val text = when {
        state.sync.lastSyncFailed -> stringResource(R.string.calendar_settings_sync_failed)
        last == null -> stringResource(R.string.calendar_settings_never_synced)
        else -> stringResource(
            R.string.calendar_settings_last_sync,
            formatter.relativeDate(last.toLocalDate(), today) + " " + formatter.time(last.toLocalTime()),
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.sync.lastSyncFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PlannerButton(
            text = stringResource(R.string.calendar_settings_sync_now),
            onClick = actions.onSyncNow,
            style = PlannerButtonStyle.Text,
            enabled = !state.syncing,
        )
    }
}

@Composable
private fun PermissionDenied() {
    val context = LocalContext.current
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Text(stringResource(R.string.calendar_settings_permission_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        PlannerButton(
            text = stringResource(R.string.calendar_settings_open_system),
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            style = PlannerButtonStyle.Text,
        )
    }
}

@Composable
private fun CalendarRow(calendar: DeviceCalendar, modifier: Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(Color(calendar.color), CircleShape))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
            val account = listOfNotNull(
                calendar.accountName.takeIf { it.isNotBlank() && it != calendar.displayName },
                if (!calendar.writable) stringResource(R.string.calendar_settings_read_only) else null,
            ).joinToString(com.behnamjalali.planb.core.ui.metaSeparator())
            if (account.isNotEmpty()) Text(account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
