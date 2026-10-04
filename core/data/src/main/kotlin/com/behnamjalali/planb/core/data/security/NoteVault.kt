package com.behnamjalali.planb.core.data.security

import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The vault is locked (or has no key for this body); ask for the passphrase. */
class VaultLockedException : IllegalStateException("The note vault is locked")

/**
 * Holds the keys of locked notes for the current session (Plan-B Pro #36).
 *
 * The passphrase is never stored. Setting it up stores only a random salt, the work factor and
 * a check value (a known text encrypted with the derived key) in the device-only
 * [SecurityPreferences]. Unlocking derives the key again and keeps it in memory until [lock]
 * (App lock, a long time in the background, or process death). Bodies restored from another
 * device carry their own salt; their key is derived on demand from the same passphrase.
 */
@Singleton
class NoteVault internal constructor(
    private val preferences: SecurityPreferences,
    private val iterations: Int,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor(
        preferences: SecurityPreferences,
        @Dispatcher(PlanBDispatcher.Default) dispatcher: CoroutineDispatcher,
    ) : this(preferences, NoteCrypto.DEFAULT_ITERATIONS, dispatcher)

    private val mutex = Mutex()

    /** Derived keys by [KeyParams.id]; the local vault's key is one of them. */
    private val keys = mutableMapOf<String, ByteArray>()
    private var localId: String? = null

    private val _unlocked = MutableStateFlow(false)

    /** True while the key of this device's vault is in memory. */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** True once a passphrase was set up (or adopted from restored notes) on this device. */
    val isSetUp: Flow<Boolean> = preferences.vault.map { it != null }

    suspend fun setUpDone(): Boolean = preferences.vaultRecord() != null

    /** Sets the passphrase for the first time and unlocks. */
    suspend fun setUp(passphrase: CharArray) = mutex.withLock {
        check(preferences.vaultRecord() == null) { "Vault already set up" }
        val params = KeyParams(NoteCrypto.newSalt(), iterations)
        val key = derive(passphrase, params)
        preferences.saveVault(VaultRecord(params, NoteCrypto.encrypt(key, params, VERIFIER)))
        install(params, key, local = true)
    }

    /**
     * Unlocks with [passphrase]. [sample] is an encrypted body to check against when this device
     * has no vault yet (restored notes) or when the body was locked with another salt. Returns
     * false for a wrong passphrase.
     */
    suspend fun unlock(passphrase: CharArray, sample: ByteArray? = null): Boolean = mutex.withLock {
        val record = preferences.vaultRecord()
        var ok = false
        if (record != null) {
            val key = derive(passphrase, record.params)
            if (verifies(key, record.verifier)) {
                install(record.params, key, local = true)
                ok = true
            }
        }
        val sampleParams = sample?.let { runCatching { NoteCrypto.params(it) }.getOrNull() }
        if (sample != null && sampleParams != null && sampleParams.id !in keys) {
            val key = derive(passphrase, sampleParams)
            if (runCatching { NoteCrypto.decrypt(key, sample) }.isSuccess) {
                if (record == null) {
                    // A new device with restored notes: adopt their salt as this device's vault.
                    preferences.saveVault(VaultRecord(sampleParams, NoteCrypto.encrypt(key, sampleParams, VERIFIER)))
                    install(sampleParams, key, local = true)
                } else {
                    install(sampleParams, key, local = false)
                }
                ok = true
            }
        }
        ok
    }

    /** Forgets every key (the notes stay encrypted). */
    fun lock() {
        synchronized(keys) {
            keys.values.forEach { it.fill(0) }
            keys.clear()
            localId = null
        }
        _unlocked.update { false }
    }

    /** Whether [envelope] can be opened in this session. */
    fun canDecrypt(envelope: ByteArray): Boolean {
        val id = runCatching { NoteCrypto.params(envelope).id }.getOrNull() ?: return false
        return synchronized(keys) { id in keys }
    }

    /** Encrypts with this device's vault key; throws [VaultLockedException] when locked. */
    fun encrypt(plaintext: ByteArray): ByteArray = synchronized(keys) {
        val id = localId ?: throw VaultLockedException()
        val key = keys[id] ?: throw VaultLockedException()
        val params = paramsById.getValue(id)
        NoteCrypto.encrypt(key, params, plaintext)
    }

    /** Throws [VaultLockedException] without a key, [NoteCryptoException] for damaged data. */
    fun decrypt(envelope: ByteArray): ByteArray {
        val id = NoteCrypto.params(envelope).id
        val key = synchronized(keys) { keys[id]?.copyOf() } ?: throw VaultLockedException()
        try {
            return NoteCrypto.decrypt(key, envelope)
        } finally {
            key.fill(0)
        }
    }

    // region Biometric unlock: the vault key wrapped by a Keystore key that needs a fingerprint.

    val biometricEnabled: Flow<Boolean> = preferences.biometricKey.map { it != null }

    suspend fun biometricKey(): WrappedKey? = preferences.biometricKey.first()

    /** Wraps the unlocked vault key with [cipher] (initialised for encryption and authenticated). */
    suspend fun enableBiometric(cipher: Cipher) {
        val key = synchronized(keys) { localId?.let { keys[it]?.copyOf() } } ?: throw VaultLockedException()
        try {
            preferences.saveBiometricKey(WrappedKey(cipher.doFinal(key), cipher.iv))
        } finally {
            key.fill(0)
        }
    }

    suspend fun disableBiometric() = preferences.saveBiometricKey(null)

    /** Unwraps the vault key with [cipher] (initialised for decryption and authenticated). */
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean = mutex.withLock {
        val record = preferences.vaultRecord() ?: return@withLock false
        val wrapped = biometricKey() ?: return@withLock false
        val key = runCatching { cipher.doFinal(wrapped.ciphertext) }.getOrNull() ?: return@withLock false
        if (!verifies(key, record.verifier)) return@withLock false
        install(record.params, key, local = true)
        true
    }

    // endregion

    private val paramsById = mutableMapOf<String, KeyParams>()

    private suspend fun derive(passphrase: CharArray, params: KeyParams): ByteArray =
        withContext(dispatcher) { NoteCrypto.deriveKey(passphrase, params) }

    private fun verifies(key: ByteArray, verifier: ByteArray): Boolean =
        runCatching { NoteCrypto.decrypt(key, verifier).contentEquals(VERIFIER) }.getOrDefault(false)

    private fun install(params: KeyParams, key: ByteArray, local: Boolean) {
        synchronized(keys) {
            keys[params.id]?.fill(0)
            keys[params.id] = key
            paramsById[params.id] = params
            if (local) localId = params.id
        }
        if (local) _unlocked.update { true }
    }

    private companion object {
        val VERIFIER = "planb-note-vault-v1".toByteArray(Charsets.UTF_8)
    }
}
