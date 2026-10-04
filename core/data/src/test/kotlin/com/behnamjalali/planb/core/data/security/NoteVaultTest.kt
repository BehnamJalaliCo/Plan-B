package com.behnamjalali.planb.core.data.security

import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteVaultTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dirs = mutableListOf<File>()

    private fun preferences(): SecurityPreferences {
        val dir = Files.createTempDirectory("planb-security").toFile().also(dirs::add)
        return SecurityPreferences(createPreferencesDataStore(scope) { File(dir, "security.preferences_pb") })
    }

    private fun vault(prefs: SecurityPreferences = preferences()) = NoteVault(prefs, 1_000, Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
        dirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun setUp_unlocks_andOnlyTheRightPassphraseUnlocksAgain() = runTest {
        val prefs = preferences()
        val vault = vault(prefs)
        assertThat(vault.isSetUp.first()).isFalse()
        vault.setUp("my long passphrase".toCharArray())
        assertThat(vault.unlocked.value).isTrue()
        val envelope = vault.encrypt("body".toByteArray())

        vault.lock()
        assertThat(vault.unlocked.value).isFalse()
        assertThrows(VaultLockedException::class.java) { vault.decrypt(envelope) }
        assertThrows(VaultLockedException::class.java) { vault.encrypt("x".toByteArray()) }

        assertThat(vault.unlock("wrong passphrase".toCharArray())).isFalse()
        assertThat(vault.unlocked.value).isFalse()
        assertThat(vault.unlock("my long passphrase".toCharArray())).isTrue()
        assertThat(vault.decrypt(envelope)).isEqualTo("body".toByteArray())

        // A new process (same device): the stored salt and check value are enough.
        val restarted = vault(prefs)
        assertThat(restarted.isSetUp.first()).isTrue()
        assertThat(restarted.unlock("my long passphrase".toCharArray())).isTrue()
        assertThat(restarted.decrypt(envelope)).isEqualTo("body".toByteArray())
    }

    @Test
    fun restoredNotes_onANewDevice_openWithTheOriginalPassphrase() = runTest {
        val original = vault()
        original.setUp("travel passphrase".toCharArray())
        val envelope = original.encrypt("from the old phone".toByteArray())

        val newDevice = vault()
        assertThat(newDevice.unlock("travel passphrase".toCharArray())).isFalse()
        assertThat(newDevice.unlock("wrong".toCharArray(), sample = envelope)).isFalse()
        assertThat(newDevice.isSetUp.first()).isFalse()
        assertThat(newDevice.unlock("travel passphrase".toCharArray(), sample = envelope)).isTrue()
        assertThat(newDevice.decrypt(envelope)).isEqualTo("from the old phone".toByteArray())
        // The restored salt became this device's vault: new locks use the same passphrase.
        assertThat(newDevice.isSetUp.first()).isTrue()
        val fresh = newDevice.encrypt("new".toByteArray())
        assertThat(original.decrypt(fresh)).isEqualTo("new".toByteArray())
    }

    @Test
    fun appLockPolicy_locksAfterTheChosenTime() {
        val settings = AppLockSettings(enabled = true, timeout = AppLockTimeout.ONE_MINUTE)
        val mark = BackgroundMark(wallMillis = 1_000_000, monotonicMillis = 5_000)
        val short = AppLockPolicy.elapsed(mark, 1_030_000, 35_000)
        assertThat(AppLockPolicy.shouldLock(settings, short)).isFalse()
        val long = AppLockPolicy.elapsed(mark, 1_070_000, 75_000)
        assertThat(AppLockPolicy.shouldLock(settings, long)).isTrue()
        // Deep sleep stops the monotonic clock; the wall clock still counts it.
        assertThat(AppLockPolicy.shouldLock(settings, AppLockPolicy.elapsed(mark, 1_000_000 + 3_600_000, 6_000))).isTrue()
        // Moving the clock back never avoids the lock.
        assertThat(AppLockPolicy.shouldLock(settings, AppLockPolicy.elapsed(mark, 0, 6_000))).isTrue()
        // Immediately means any time in the background; off never locks.
        assertThat(AppLockPolicy.shouldLock(settings.copy(timeout = AppLockTimeout.IMMEDIATELY), 0)).isTrue()
        assertThat(AppLockPolicy.shouldLock(settings.copy(enabled = false), Long.MAX_VALUE)).isFalse()
        // Locked notes forget their key after five minutes even without App lock.
        assertThat(AppLockPolicy.shouldLockVault(settings.copy(enabled = false), 4 * 60_000)).isFalse()
        assertThat(AppLockPolicy.shouldLockVault(settings.copy(enabled = false), 5 * 60_000)).isTrue()
    }

    @Test
    fun controller_locksOnColdStart_andAfterTheTimeout_butNotForShortTrips() = runTest {
        val prefs = preferences()
        prefs.updateAppLock { it.copy(enabled = true, timeout = AppLockTimeout.FIVE_MINUTES) }
        val time = FakeTimeProvider()
        val vault = vault(prefs)
        vault.setUp("controller passphrase".toCharArray())
        val controller = AppLockController(prefs, vault, time, scope)
        waitFor { controller.state.value == LockState.LOCKED && controller.settings.value != null }

        controller.unlock()
        controller.onBackground()
        time.advance(Duration.ofMinutes(2))
        controller.onForeground()
        assertThat(controller.state.value).isEqualTo(LockState.UNLOCKED)
        assertThat(vault.unlocked.value).isTrue()

        controller.onBackground()
        time.advance(Duration.ofMinutes(6))
        controller.onForeground()
        assertThat(controller.state.value).isEqualTo(LockState.LOCKED)
        assertThat(vault.unlocked.value).isFalse()

        // The device-lock prompt may cover the app; that does not count as leaving it.
        controller.unlock()
        controller.authenticationStarted()
        controller.onBackground()
        time.advance(Duration.ofMinutes(10))
        controller.onForeground()
        controller.authenticationFinished()
        assertThat(controller.state.value).isEqualTo(LockState.UNLOCKED)
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(10)
        }
    }
}
