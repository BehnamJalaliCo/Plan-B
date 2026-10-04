package com.behnamjalali.planb.feature.security

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.ActivityRepository
import com.behnamjalali.planb.core.data.repository.TrashRepository
import com.behnamjalali.planb.core.data.security.AppLockController
import com.behnamjalali.planb.core.data.security.AppLockSettings
import com.behnamjalali.planb.core.data.security.AppLockTimeout
import com.behnamjalali.planb.core.data.security.BiometricKeyStore
import com.behnamjalali.planb.core.data.security.LockState
import com.behnamjalali.planb.core.data.security.NoteVault
import com.behnamjalali.planb.core.data.security.SecurityPreferences
import com.behnamjalali.planb.core.model.ActivityEntityType
import com.behnamjalali.planb.core.model.ActivityEntry
import com.behnamjalali.planb.core.model.TrashItem
import com.behnamjalali.planb.core.model.TrashItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object SecurityRoute
@Serializable data object TrashRoute

/** The global activity history, or that of one item when [entityType] is set. */
@Serializable
data class ActivityRoute(val entityType: String? = null, val entityId: Long = 0)

// region App lock gate

/** Drives the lock screen shown over the whole app. */
@HiltViewModel
class AppLockViewModel @Inject constructor(private val controller: AppLockController) : ViewModel() {
    val state: StateFlow<LockState> = controller.state

    /** Hide the app in the recent-apps overview: App lock is on with "hide in recents". */
    val hideInRecents: StateFlow<Boolean> = controller.settings
        .map { it?.enabled == true && it.hideInRecents }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onBackground() = controller.onBackground()
    fun onForeground() = controller.onForeground()
    fun authenticationStarted() = controller.authenticationStarted()
    fun authenticationFinished() = controller.authenticationFinished()
    fun unlock() = controller.unlock()
}

// endregion

// region Security settings

data class SecurityUiState(
    val loaded: Boolean = false,
    val lock: AppLockSettings = AppLockSettings(),
    val vaultSetUp: Boolean = false,
    val vaultUnlocked: Boolean = false,
    val fingerprintForNotes: Boolean = false,
)

sealed interface SecurityEvent {
    data object PassphraseSet : SecurityEvent
    data object WrongPassphrase : SecurityEvent
    data object NotesLocked : SecurityEvent
    data object FingerprintOn : SecurityEvent
    data object Failed : SecurityEvent
}

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val preferences: SecurityPreferences,
    private val controller: AppLockController,
    private val vault: NoteVault,
    private val keyStore: BiometricKeyStore,
) : ViewModel() {
    val state: StateFlow<SecurityUiState> = combine(
        preferences.appLock,
        vault.isSetUp,
        vault.unlocked,
        vault.biometricEnabled,
    ) { lock, setUp, unlocked, fingerprint ->
        SecurityUiState(loaded = true, lock = lock, vaultSetUp = setUp, vaultUnlocked = unlocked, fingerprintForNotes = fingerprint)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _events = MutableSharedFlow<SecurityEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SecurityEvent> = _events

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(SecurityEvent.Failed) }
    }

    /** Called after the user confirmed with the device lock, so they can unlock again later. */
    fun setLockEnabled(enabled: Boolean) = launch {
        preferences.updateAppLock { it.copy(enabled = enabled) }
        if (enabled) controller.enabledNow()
    }

    fun setTimeout(timeout: AppLockTimeout) = launch { preferences.updateAppLock { it.copy(timeout = timeout) } }

    fun setHideInRecents(hide: Boolean) = launch { preferences.updateAppLock { it.copy(hideInRecents = hide) } }

    fun setUpPassphrase(passphrase: CharArray) = work {
        try {
            vault.setUp(passphrase)
            _events.tryEmit(SecurityEvent.PassphraseSet)
        } finally {
            passphrase.fill(' ')
        }
    }

    fun unlockNotes(passphrase: CharArray) = work {
        try {
            if (!vault.unlock(passphrase)) _events.tryEmit(SecurityEvent.WrongPassphrase)
        } finally {
            passphrase.fill(' ')
        }
    }

    fun lockNotesNow() {
        vault.lock()
        _events.tryEmit(SecurityEvent.NotesLocked)
    }

    /** A Keystore cipher for BiometricPrompt; null when this device cannot hold such a key. */
    fun fingerprintCipher(): Cipher? = keyStore.encryptCipher()

    fun enableFingerprint(authenticated: Cipher) = launch {
        vault.enableBiometric(authenticated)
        _events.tryEmit(SecurityEvent.FingerprintOn)
    }

    fun disableFingerprint() = launch {
        vault.disableBiometric()
        keyStore.delete()
    }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            runCatchingSafely { block() }.onFailure { _events.tryEmit(SecurityEvent.Failed) }
        } finally {
            _busy.value = false
        }
    }
}

// endregion

// region Trash

enum class TrashFilter { ALL, TASKS, NOTES }

data class TrashUiState(
    val loading: Boolean = true,
    val items: List<TrashItem> = emptyList(),
    val filter: TrashFilter = TrashFilter.ALL,
) {
    val visible: List<TrashItem>
        get() = when (filter) {
            TrashFilter.ALL -> items
            TrashFilter.TASKS -> items.filter { it.type == TrashItemType.TASK }
            TrashFilter.NOTES -> items.filter { it.type == TrashItemType.NOTE }
        }
}

sealed interface TrashEvent {
    data class Restored(val item: TrashItem) : TrashEvent
    data object Deleted : TrashEvent
    data class Emptied(val count: Int) : TrashEvent
    data object Failed : TrashEvent
}

@HiltViewModel
class TrashViewModel @Inject constructor(private val trash: TrashRepository) : ViewModel() {
    private val filter = MutableStateFlow(TrashFilter.ALL)

    val state: StateFlow<TrashUiState> = combine(trash.observeTrash(), filter) { items, f ->
        TrashUiState(loading = false, items = items, filter = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    private val _events = MutableSharedFlow<TrashEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TrashEvent> = _events

    fun setFilter(value: TrashFilter) = filter.update { value }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatchingSafely { block() }.onFailure { _events.tryEmit(TrashEvent.Failed) }
    }

    fun restore(item: TrashItem) = launch {
        trash.restore(item)
        _events.tryEmit(TrashEvent.Restored(item))
    }

    fun deleteForever(item: TrashItem) = launch {
        trash.deleteForever(item)
        _events.tryEmit(TrashEvent.Deleted)
    }

    fun empty() = launch { _events.tryEmit(TrashEvent.Emptied(trash.emptyTrash())) }
}

// endregion

// region Activity

data class ActivityUiState(
    val loading: Boolean = true,
    val entries: List<ActivityEntry> = emptyList(),
    /** Null shows every kind of item. */
    val filter: ActivityEntityType? = null,
    /** True when showing the history of one item. */
    val single: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val activity: ActivityRepository,
) : ViewModel() {
    private val route = runCatching { savedState.toRoute<ActivityRoute>() }.getOrDefault(ActivityRoute())
    private val item = route.entityType?.let { name -> ActivityEntityType.entries.firstOrNull { it.name == name } }
    private val filter = MutableStateFlow<ActivityEntityType?>(null)

    val state: StateFlow<ActivityUiState> = filter.flatMapLatest { type ->
        val source = if (item != null) activity.observeFor(item, route.entityId) else activity.observeRecent(type)
        source.map { ActivityUiState(loading = false, entries = it, filter = type, single = item != null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState(single = item != null))

    fun setFilter(type: ActivityEntityType?) = filter.update { type }
}

// endregion
