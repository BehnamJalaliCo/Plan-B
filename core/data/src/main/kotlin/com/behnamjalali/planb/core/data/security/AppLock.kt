package com.behnamjalali.planb.core.data.security

import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** When the app went to the background, on both clocks. */
data class BackgroundMark(val wallMillis: Long, val monotonicMillis: Long)

/** Pure rules of App lock, kept apart from Android so they are unit-tested. */
object AppLockPolicy {
    /** Locked notes forget their key after this long in the background even without App lock. */
    const val VAULT_TIMEOUT_MILLIS = 5 * 60_000L

    /**
     * Time spent in the background. The wall clock also counts deep sleep; the monotonic clock
     * cannot be moved by the user. A wall clock that went backwards counts as "very long".
     */
    fun elapsed(mark: BackgroundMark, nowWall: Long, nowMonotonic: Long): Long {
        val wall = nowWall - mark.wallMillis
        val monotonic = nowMonotonic - mark.monotonicMillis
        if (wall < 0 || monotonic < 0) return Long.MAX_VALUE
        return maxOf(wall, monotonic)
    }

    /** Whether returning to the app after [elapsed] ms must show the lock screen. */
    fun shouldLock(settings: AppLockSettings, elapsed: Long): Boolean =
        settings.enabled && elapsed >= settings.timeout.millis

    /** Whether the keys of locked notes must be forgotten after [elapsed] ms in the background. */
    fun shouldLockVault(settings: AppLockSettings, elapsed: Long): Boolean =
        shouldLock(settings, elapsed) || elapsed >= VAULT_TIMEOUT_MILLIS
}

enum class LockState {
    /** Settings not read yet: show nothing (cold start only, a few milliseconds). */
    UNKNOWN,
    LOCKED,
    UNLOCKED,
}

/**
 * App lock state for the single activity: locked on a cold start when App lock is on, and again
 * after the chosen time in the background. The activity reports [onBackground] (not for
 * configuration changes) and [onForeground].
 */
@Singleton
class AppLockController @Inject constructor(
    private val preferences: SecurityPreferences,
    private val vault: NoteVault,
    private val time: TimeProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LockState.UNKNOWN)
    val state: StateFlow<LockState> = _state.asStateFlow()

    val settings: StateFlow<AppLockSettings?> = preferences.appLock.stateIn(scope, SharingStarted.Eagerly, null)

    private var background: BackgroundMark? = null

    /** While the device lock prompt is open (it may cover the app); leaving then is not "leaving". */
    @Volatile private var authenticating = false

    init {
        scope.launch {
            val enabled = runCatching { preferences.appLock.first().enabled }.getOrDefault(false)
            _state.update { if (it == LockState.UNKNOWN) if (enabled) LockState.LOCKED else LockState.UNLOCKED else it }
        }
    }

    fun onBackground() {
        if (authenticating) return
        background = BackgroundMark(time.now().toEpochMilli(), time.monotonicMillis())
    }

    fun authenticationStarted() {
        authenticating = true
    }

    fun authenticationFinished() {
        authenticating = false
    }

    fun onForeground() {
        val mark = background ?: return
        background = null
        val current = settings.value ?: return
        val elapsed = AppLockPolicy.elapsed(mark, time.now().toEpochMilli(), time.monotonicMillis())
        if (AppLockPolicy.shouldLockVault(current, elapsed)) vault.lock()
        if (AppLockPolicy.shouldLock(current, elapsed)) _state.update { LockState.LOCKED }
    }

    /** The user authenticated on the lock screen. */
    fun unlock() = _state.update { LockState.UNLOCKED }

    /** App lock was just turned on: the user is here, so the app stays open until it next leaves. */
    fun enabledNow() = _state.update { if (it == LockState.UNKNOWN) LockState.UNLOCKED else it }

    /** Locks right away (also forgets the keys of locked notes). */
    fun lockNow() {
        vault.lock()
        if (settings.value?.enabled == true) _state.update { LockState.LOCKED }
    }
}
