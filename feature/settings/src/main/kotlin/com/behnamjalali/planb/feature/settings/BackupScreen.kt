package com.behnamjalali.planb.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.backup.BackupFormat
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.PlannerLocals
import java.time.Instant
import java.time.ZoneId

@Composable
fun BackupDestination(onBack: () -> Unit, snackbarHostState: SnackbarHostState, viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    var exportKind by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            val text = when (m) {
                DataMessage.BackupCreated -> context.getString(R.string.backup_created)
                DataMessage.BackupFailed -> context.getString(R.string.backup_failed)
                DataMessage.Restored -> context.getString(R.string.backup_restored)
                is DataMessage.RestoreError -> context.getString(restoreErrorMessage(m.error))
                is DataMessage.Exported -> context.resources.getQuantityString(R.plurals.export_done, m.count, numbers.format(m.count))
                DataMessage.ExportFailed -> context.getString(R.string.export_failed)
                is DataMessage.Imported -> context.getString(R.string.import_done, numbers.format(m.imported), numbers.format(m.skipped))
                DataMessage.ImportFailed -> context.getString(R.string.import_failed)
                DataMessage.Deleted -> context.getString(R.string.data_deleted)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME)) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspect) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.export(ExportKind.TASKS_CSV, it) }
    }
    val jsonTasks = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(exportKind ?: ExportKind.TASKS_JSON, it) }
    }
    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.export(ExportKind.NOTES_MARKDOWN, it) }
    }
    val importTasks = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importTasks) }
    val stamp = PlannerLocals.today.toString()

    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.backup_title), onBack = onBack)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { PlannerSectionHeader(stringResource(R.string.backup_section_backup)) }
            item {
                SettingsRow(stringResource(R.string.backup_create), icon = Icons.Rounded.Backup, subtitle = stringResource(R.string.backup_create_summary),
                    onClick = { if (!state.busy) createBackup.launch("Plan-B-backup-$stamp.zip") })
            }
            item {
                SettingsRow(stringResource(R.string.backup_restore), icon = Icons.Rounded.Restore, subtitle = stringResource(R.string.backup_restore_summary),
                    onClick = { if (!state.busy) openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
            }
            item { PlannerSectionHeader(stringResource(R.string.backup_section_export)) }
            item { SettingsRow(stringResource(R.string.export_tasks_csv), icon = Icons.Rounded.TableChart, onClick = { csv.launch("Plan-B-tasks-$stamp.csv") }) }
            item {
                SettingsRow(stringResource(R.string.export_tasks_json), icon = Icons.Rounded.DataObject, onClick = {
                    exportKind = ExportKind.TASKS_JSON
                    jsonTasks.launch("Plan-B-tasks-$stamp.json")
                })
            }
            item { SettingsRow(stringResource(R.string.export_notes_markdown), icon = Icons.Rounded.FileDownload, onClick = { zip.launch("Plan-B-notes-$stamp.zip") }) }
            item {
                SettingsRow(stringResource(R.string.export_notes_json), icon = Icons.Rounded.DataObject, onClick = {
                    exportKind = ExportKind.NOTES_JSON
                    jsonTasks.launch("Plan-B-notes-$stamp.json")
                })
            }
            item { PlannerSectionHeader(stringResource(R.string.backup_section_import)) }
            item {
                SettingsRow(stringResource(R.string.import_tasks), icon = Icons.Rounded.FileUpload, subtitle = stringResource(R.string.import_tasks_summary),
                    onClick = { importTasks.launch(arrayOf("text/csv", "text/comma-separated-values", "application/json", "text/*")) })
            }
            item { Text(stringResource(R.string.import_notes_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { PlannerSectionHeader(stringResource(R.string.backup_section_manage)) }
            item { Text(stringResource(R.string.data_archived_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                SettingsRow(stringResource(R.string.data_delete_all), icon = Icons.Rounded.DeleteForever, subtitle = stringResource(R.string.data_delete_all_summary),
                    onClick = { confirmDelete = true })
            }
        }
    }

    state.pendingRestore?.let { archive ->
        val m = archive.manifest
        val c = archive.database.counts()
        val created = Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault())
        PlannerDialog(
            title = stringResource(R.string.backup_confirm_title),
            message = stringResource(
                R.string.backup_confirm_message,
                "${formatter.mediumDate(created.toLocalDate())} ${formatter.time(created.toLocalTime())}",
                numbers.localize(m.appVersion),
                numbers.format(c["tasks"] ?: 0), numbers.format(c["notes"] ?: 0), numbers.format(c["projects"] ?: 0),
                numbers.format(c["habits"] ?: 0), numbers.format(c["events"] ?: 0),
            ),
            onDismiss = viewModel::cancelRestore,
            confirmLabel = stringResource(R.string.backup_confirm_action),
            destructive = true,
            onConfirm = viewModel::confirmRestore,
        )
    }
    if (confirmDelete) {
        PlannerDialog(
            title = stringResource(R.string.data_delete_all),
            message = stringResource(R.string.data_delete_all_confirm),
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAll()
            },
        )
    }
}
