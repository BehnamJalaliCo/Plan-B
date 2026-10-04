package com.behnamjalali.planb.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.backup.AppVersion
import com.behnamjalali.planb.core.backup.BackupArchive
import com.behnamjalali.planb.core.backup.BackupException
import com.behnamjalali.planb.core.backup.BackupManager
import com.behnamjalali.planb.core.backup.DataTransfer
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object SettingsRoute
@Serializable data object BackupRoute
@Serializable data object PrivacyRoute
@Serializable data object AboutRoute
@Serializable data object LicensesRoute

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val version: AppVersion,
) : ViewModel() {
    val state: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}

sealed interface DataMessage {
    data object BackupCreated : DataMessage
    data object BackupFailed : DataMessage
    /** [language] is the restored language preference; the app switches to it (MainActivity). */
    data class Restored(val language: AppLanguage) : DataMessage
    data class RestoreError(val error: Throwable) : DataMessage
    data class Exported(val count: Int) : DataMessage
    data object ExportFailed : DataMessage
    data class Imported(val imported: Int, val skipped: Int) : DataMessage
    data object ImportFailed : DataMessage
    data object Deleted : DataMessage
}

data class DataUiState(
    val busy: Boolean = false,
    /** A validated backup waiting for the user's confirmation. */
    val pendingRestore: BackupArchive? = null,
    /** The latest result, kept until the screen has shown it (a result is never dropped). */
    val message: DataMessage? = null,
)

enum class ExportKind { TASKS_CSV, TASKS_JSON, NOTES_MARKDOWN, NOTES_JSON }

/**
 * Backup, restore, export, import and data deletion. All file and database work
 * happens off the main thread inside the repositories. Restore and delete-all run in the
 * application scope: leaving the screen must not cancel them half-way (the data step is a
 * transaction, but preferences and reminders follow it).
 */
@HiltViewModel
class DataViewModel @Inject constructor(
    private val backup: BackupManager,
    private val transfer: DataTransfer,
    private val settings: SettingsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    private fun report(message: DataMessage) = _state.update { it.copy(message = message) }

    /** Called once the screen has shown [message]. */
    fun messageShown(message: DataMessage) = _state.update { if (it.message == message) it.copy(message = null) else it }

    private fun work(scope: CoroutineScope = viewModelScope, block: suspend () -> Unit) = scope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    fun createBackup(uri: Uri) = work {
        runCatchingSafely { backup.exportTo(uri) }
            .onSuccess { report(DataMessage.BackupCreated) }
            .onFailure { report(DataMessage.BackupFailed) }
    }

    /** Step 1: open and validate; nothing is changed yet. */
    fun inspect(uri: Uri) = work {
        runCatchingSafely { backup.inspect(uri) }
            .onSuccess { archive -> _state.update { it.copy(pendingRestore = archive) } }
            .onFailure { report(DataMessage.RestoreError(it)) }
    }

    fun cancelRestore() {
        _state.value.pendingRestore?.let(backup::discard)
        _state.update { it.copy(pendingRestore = null) }
    }

    /** Step 2: after explicit confirmation, replace data transactionally. */
    fun confirmRestore() {
        val archive = _state.value.pendingRestore ?: return
        _state.update { it.copy(pendingRestore = null) }
        work(appScope) {
            runCatchingSafely { backup.restore(archive) }
                .onSuccess { report(DataMessage.Restored(settings.current().language)) }
                .onFailure {
                    backup.discard(archive)
                    report(DataMessage.RestoreError(it))
                }
        }
    }

    fun export(kind: ExportKind, uri: Uri) = work {
        runCatchingSafely {
            when (kind) {
                ExportKind.TASKS_CSV -> transfer.exportTasksCsv(uri)
                ExportKind.TASKS_JSON -> transfer.exportTasksJson(uri)
                ExportKind.NOTES_MARKDOWN -> transfer.exportNotesMarkdownZip(uri)
                ExportKind.NOTES_JSON -> transfer.exportNotesJson(uri)
            }
        }.onSuccess { report(DataMessage.Exported(it)) }
            .onFailure { report(DataMessage.ExportFailed) }
    }

    fun importTasks(uri: Uri) = work {
        runCatchingSafely { transfer.importTasks(uri) }
            .onSuccess { report(DataMessage.Imported(it.imported, it.skipped)) }
            .onFailure { report(DataMessage.ImportFailed) }
    }

    fun deleteAll() = work(appScope) {
        runCatchingSafely { backup.deleteAllData() }
            .onSuccess { report(DataMessage.Deleted) }
            .onFailure { report(DataMessage.RestoreError(it)) }
    }
}

/** Maps backup failures to user-facing messages. */
fun restoreErrorMessage(error: Throwable): Int = when (error) {
    is BackupException.NotABackup -> R.string.backup_error_not_backup
    is BackupException.Corrupt -> R.string.backup_error_corrupt
    is BackupException.UnsupportedVersion, is BackupException.NewerDatabase -> R.string.backup_error_version
    is BackupException.Invalid -> R.string.backup_error_invalid
    else -> R.string.backup_error_restore
}
