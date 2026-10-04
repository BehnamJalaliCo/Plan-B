package com.behnamjalali.planb.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.backup.AppVersion
import com.behnamjalali.planb.core.backup.BackupArchive
import com.behnamjalali.planb.core.backup.BackupException
import com.behnamjalali.planb.core.backup.BackupManager
import com.behnamjalali.planb.core.backup.DataTransfer
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    data object Restored : DataMessage
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
)

enum class ExportKind { TASKS_CSV, TASKS_JSON, NOTES_MARKDOWN, NOTES_JSON }

/**
 * Backup, restore, export, import and data deletion. All file and database work
 * happens off the main thread inside the repositories.
 */
@HiltViewModel
class DataViewModel @Inject constructor(
    private val backup: BackupManager,
    private val transfer: DataTransfer,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<DataMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<DataMessage> = _messages

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true)
        try {
            block()
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun createBackup(uri: Uri) = work {
        runCatchingSafely { backup.exportTo(uri) }
            .onSuccess { _messages.tryEmit(DataMessage.BackupCreated) }
            .onFailure { _messages.tryEmit(DataMessage.BackupFailed) }
    }

    /** Step 1: open and validate; nothing is changed yet. */
    fun inspect(uri: Uri) = work {
        runCatchingSafely { backup.inspect(uri) }
            .onSuccess { _state.value = _state.value.copy(pendingRestore = it) }
            .onFailure { _messages.tryEmit(DataMessage.RestoreError(it)) }
    }

    fun cancelRestore() {
        _state.value = _state.value.copy(pendingRestore = null)
    }

    /** Step 2: after explicit confirmation, replace data transactionally. */
    fun confirmRestore() {
        val archive = _state.value.pendingRestore ?: return
        _state.value = _state.value.copy(pendingRestore = null)
        work {
            runCatchingSafely { backup.restore(archive) }
                .onSuccess { _messages.tryEmit(DataMessage.Restored) }
                .onFailure { _messages.tryEmit(DataMessage.RestoreError(it)) }
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
        }.onSuccess { _messages.tryEmit(DataMessage.Exported(it)) }
            .onFailure { _messages.tryEmit(DataMessage.ExportFailed) }
    }

    fun importTasks(uri: Uri) = work {
        runCatchingSafely { transfer.importTasks(uri) }
            .onSuccess { _messages.tryEmit(DataMessage.Imported(it.imported, it.skipped)) }
            .onFailure { _messages.tryEmit(DataMessage.ImportFailed) }
    }

    fun deleteAll() = work {
        runCatchingSafely { backup.deleteAllData() }
            .onSuccess { _messages.tryEmit(DataMessage.Deleted) }
            .onFailure { _messages.tryEmit(DataMessage.RestoreError(it)) }
    }
}

/** Maps backup failures to user-facing messages. */
fun restoreErrorMessage(error: Throwable): Int = when (error) {
    is BackupException.NotABackup -> R.string.backup_error_not_backup
    is BackupException.Corrupt -> R.string.backup_error_corrupt
    is BackupException.UnsupportedVersion -> R.string.backup_error_version
    is BackupException.Invalid -> R.string.backup_error_invalid
    else -> R.string.backup_error_restore
}
