package com.behnamjalali.planb.core.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.datastore.createPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How long the app may stay in the background before App lock asks again. */
enum class AppLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(5 * 60_000),
    FIFTEEN_MINUTES(15 * 60_000),
}

/** App lock settings (Plan-B Pro #36). Device-only: never exported or backed up. */
data class AppLockSettings(
    val enabled: Boolean = false,
    val timeout: AppLockTimeout = AppLockTimeout.ONE_MINUTE,
    /** Hide the app's content in the recent-apps overview while App lock is on. */
    val hideInRecents: Boolean = true,
)

/** The stored part of the note vault: salt, work factor and a check value, never the key or passphrase. */
internal class VaultRecord(val params: KeyParams, val verifier: ByteArray)

/** A vault key wrapped by an Android Keystore key that needs biometric authentication. */
class WrappedKey(val ciphertext: ByteArray, val iv: ByteArray)

/** The device-only preferences file of App lock and the note vault. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SecurityStore

/**
 * Security settings in their own DataStore file (`planb_security`), separate from the user
 * preferences, so nothing here ever reaches a backup or an export. It holds no secret: the
 * passphrase and the derived key are never written anywhere.
 */
@Singleton
class SecurityPreferences @Inject constructor(@SecurityStore private val store: DataStore<Preferences>) {
    private object Keys {
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val hideInRecents = booleanPreferencesKey("hide_in_recents")
        val vaultSalt = stringPreferencesKey("vault_salt")
        val vaultIterations = intPreferencesKey("vault_iterations")
        val vaultVerifier = stringPreferencesKey("vault_verifier")
        val bioKey = stringPreferencesKey("vault_biometric_key")
        val bioIv = stringPreferencesKey("vault_biometric_iv")
    }

    private val data: Flow<Preferences> = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val appLock: Flow<AppLockSettings> = data.map { it.appLock() }.distinctUntilChanged()

    private fun Preferences.appLock() = AppLockSettings(
        enabled = this[Keys.lockEnabled] ?: false,
        timeout = this[Keys.lockTimeout]?.let { name -> AppLockTimeout.entries.firstOrNull { it.name == name } } ?: AppLockTimeout.ONE_MINUTE,
        hideInRecents = this[Keys.hideInRecents] ?: true,
    )

    suspend fun updateAppLock(transform: (AppLockSettings) -> AppLockSettings) {
        store.edit { p ->
            val next = transform(p.appLock())
            p[Keys.lockEnabled] = next.enabled
            p[Keys.lockTimeout] = next.timeout.name
            p[Keys.hideInRecents] = next.hideInRecents
        }
    }

    internal val vault: Flow<VaultRecord?> = data.map { p -> p.vaultRecord() }

    internal suspend fun vaultRecord(): VaultRecord? = data.first().vaultRecord()

    private fun Preferences.vaultRecord(): VaultRecord? {
        val salt = this[Keys.vaultSalt]?.let(::decode) ?: return null
        val iterations = this[Keys.vaultIterations] ?: return null
        val verifier = this[Keys.vaultVerifier]?.let(::decode) ?: return null
        if (salt.size != NoteCrypto.SALT_BYTES || iterations !in NoteCrypto.MIN_ITERATIONS..NoteCrypto.MAX_ITERATIONS) return null
        return VaultRecord(KeyParams(salt, iterations), verifier)
    }

    internal suspend fun saveVault(record: VaultRecord) {
        store.edit { p ->
            p[Keys.vaultSalt] = encode(record.params.salt)
            p[Keys.vaultIterations] = record.params.iterations
            p[Keys.vaultVerifier] = encode(record.verifier)
            // A key wrapped for another vault would not match any more.
            p.remove(Keys.bioKey)
            p.remove(Keys.bioIv)
        }
    }

    val biometricKey: Flow<WrappedKey?> = data.map { p ->
        val key = p[Keys.bioKey]?.let(::decode)
        val iv = p[Keys.bioIv]?.let(::decode)
        if (key != null && iv != null) WrappedKey(key, iv) else null
    }

    internal suspend fun saveBiometricKey(key: WrappedKey?) {
        store.edit { p ->
            if (key == null) {
                p.remove(Keys.bioKey)
                p.remove(Keys.bioIv)
            } else {
                p[Keys.bioKey] = encode(key.ciphertext)
                p[Keys.bioIv] = encode(key.iv)
            }
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray? = runCatching { Base64.decode(text, Base64.NO_WRAP) }.getOrNull()
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityStoreModule {
    /** Not the user preferences file, so it is never exported. */
    private const val FILE = "planb_security"

    @Provides
    @Singleton
    @SecurityStore
    fun provideSecurityDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createPreferencesDataStore(scope) { context.preferencesDataStoreFile(FILE) }
}
