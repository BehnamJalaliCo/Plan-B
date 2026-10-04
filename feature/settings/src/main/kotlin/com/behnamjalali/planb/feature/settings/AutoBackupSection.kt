package com.behnamjalali.planb.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.backup.AutoBackupError
import com.behnamjalali.planb.core.backup.AutoBackupFrequency
import com.behnamjalali.planb.core.backup.AutoBackupPreferences
import com.behnamjalali.planb.core.backup.AutoBackupResult
import com.behnamjalali.planb.core.backup.AutoBackupRunner
import com.behnamjalali.planb.core.backup.AutoBackupScheduler
import com.behnamjalali.planb.core.backup.AutoBackupSettings
import com.behnamjalali.planb.core.backup.adoptBackupFolder
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Automatic backups (Plan-B Pro #37) in Settings › Backup & restore. */
@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val preferences: AutoBackupPreferences,
    private val scheduler: AutoBackupScheduler,
    private val runner: AutoBackupRunner,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val settings: StateFlow<AutoBackupSettings?> = preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _results = MutableSharedFlow<AutoBackupResult>(extraBufferCapacity = 2)
    val results: SharedFlow<AutoBackupResult> = _results

    /** Applies a change and reschedules (or cancels) the periodic work to match. */
    fun update(transform: (AutoBackupSettings) -> AutoBackupSettings) = viewModelScope.launch {
        runCatchingSafely { scheduler.apply(preferences.update(transform)) }
    }

    /** A folder was picked: it becomes the target and automatic backups switch on. */
    fun folderPicked(uri: String, name: String?) = update { it.copy(folderUri = uri, folderName = name, enabled = true, lastError = null) }

    /** "Back up now": runs on the application scope so leaving the screen does not cancel it. */
    fun backUpNow() {
        if (_running.value) return
        _running.value = true
        appScope.launch {
            try {
                _results.tryEmit(runCatchingSafely { runner.run(manual = true) }.getOrElse { AutoBackupResult.Failed(AutoBackupError.WRITE_FAILED) })
            } finally {
                _running.value = false
            }
        }
    }
}

@Composable
fun AutoBackupSection(snackbarHostState: SnackbarHostState, viewModel: AutoBackupViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current
    val resources = LocalResources.current
    val guard = rememberProGuard()
    val isPro = LocalProAccess.current.isPro
    var frequencyDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            val text = when (result) {
                is AutoBackupResult.Success -> resources.getString(R.string.auto_backup_done)
                is AutoBackupResult.Failed -> resources.getString(errorText(result.error))
                AutoBackupResult.Skipped -> return@collect
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { adoptBackupFolder(context, uri, s.folderUri) }
            .onSuccess { name -> viewModel.folderPicked(uri.toString(), name) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlannerSectionHeader(stringResource(R.string.auto_backup_section))
        SettingsRow(
            title = stringResource(R.string.auto_backup),
            subtitle = stringResource(R.string.auto_backup_summary),
            icon = Icons.Rounded.Schedule,
            modifier = Modifier.toggleable(s.enabled, role = Role.Switch) { enable ->
                when {
                    !enable -> viewModel.update { it.copy(enabled = false) }
                    else -> guard.run(ProFeature.AUTO_BACKUP) {
                        if (s.folderUri == null) pickFolder.launch(null) else viewModel.update { it.copy(enabled = true) }
                    }
                }
            },
            trailing = { if (!isPro && !s.enabled) ProBadge() else Switch(checked = s.enabled, onCheckedChange = null) },
        )
        // Configured automatic backups stay visible (and can be switched off) if Pro ends.
        if (s.enabled || s.folderUri != null) {
            Row(
                stringResource(R.string.auto_backup_folder),
                s.folderName ?: stringResource(R.string.auto_backup_folder_none),
                Icons.Rounded.Folder,
            ) { guard.run(ProFeature.AUTO_BACKUP) { pickFolder.launch(null) } }
            Row(
                stringResource(R.string.auto_backup_frequency),
                stringResource(if (s.frequency == AutoBackupFrequency.DAILY) R.string.auto_backup_daily else R.string.auto_backup_weekly),
                Icons.Rounded.EventRepeat,
            ) { frequencyDialog = true }
            SettingsRow(
                title = stringResource(R.string.auto_backup_charging),
                subtitle = stringResource(R.string.auto_backup_charging_summary),
                icon = Icons.Rounded.BatteryChargingFull,
                modifier = Modifier.toggleable(s.chargingOnly, role = Role.Switch) { v -> viewModel.update { it.copy(chargingOnly = v) } },
                trailing = { Switch(checked = s.chargingOnly, onCheckedChange = null) },
            )
            Row(
                stringResource(R.string.auto_backup_now),
                stringResource(if (running) R.string.auto_backup_running else R.string.auto_backup_now_summary),
                Icons.Rounded.CloudUpload,
            ) { guard.run(ProFeature.AUTO_BACKUP, viewModel::backUpNow) }
            Text(
                status(s),
                style = MaterialTheme.typography.bodySmall,
                color = if (s.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (frequencyDialog) {
        ChoiceDialog(
            stringResource(R.string.auto_backup_frequency),
            listOf(
                Choice(AutoBackupFrequency.DAILY, stringResource(R.string.auto_backup_daily)),
                Choice(AutoBackupFrequency.WEEKLY, stringResource(R.string.auto_backup_weekly)),
            ),
            s.frequency,
            { v -> viewModel.update { it.copy(frequency = v) } },
        ) { frequencyDialog = false }
    }
}

@Composable
private fun Row(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) =
    SettingsRow(title = title, subtitle = subtitle, icon = icon, onClick = onClick)

@Composable
private fun status(s: AutoBackupSettings): String {
    val formatter = PlannerLocals.formatter
    fun moment(at: java.time.Instant): String {
        val local = at.atZone(ZoneId.systemDefault())
        return "${formatter.mediumDate(local.toLocalDate())} ${formatter.time(local.toLocalTime())}"
    }
    val last = s.lastSuccessAt?.let { stringResource(R.string.auto_backup_last, moment(it)) } ?: stringResource(R.string.auto_backup_never)
    val error = s.lastError?.let { stringResource(R.string.auto_backup_last_failed, moment(s.lastRunAt ?: return@let null), stringResource(errorText(it))) }
    return listOfNotNull(error, last, stringResource(R.string.auto_backup_keeps, PlannerLocals.numbers.format(com.behnamjalali.planb.core.backup.AutoBackupNaming.KEEP))).joinToString("\n")
}

private fun errorText(error: AutoBackupError) = when (error) {
    AutoBackupError.FOLDER_UNAVAILABLE -> R.string.auto_backup_error_folder
    AutoBackupError.WRITE_FAILED -> R.string.auto_backup_error_write
}
