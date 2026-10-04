package com.behnamjalali.planb.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The assistant's settings (the key itself is never part of this object). [enabled] is off by
 * default and can only be switched on after explicit consent ([consentAt]).
 */
data class AiSettings(
    val providerId: String? = null,
    val baseUrl: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val consentAt: Instant? = null,
    val hasKey: Boolean = false,
) {
    val provider: AiProvider? get() = AiProviders.byId(providerId)

    /** The base URL to call: the user's entry, or the provider's default. */
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider?.defaultBaseUrl.orEmpty() }
}

/** Encrypts small secrets with a key that never leaves this device. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable Android Keystore key. The output is IV (12 bytes) followed
 * by the ciphertext and tag. The key cannot be backed up or moved to another device, which is
 * why the stored key never appears in backups: it would be unreadable anywhere else.
 */
class AndroidKeystoreCipher @Inject constructor() : SecretCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "planb_ai_provider_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** The device-only file for assistant settings (never part of a backup or export). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AiPreferences

/**
 * Assistant settings and the provider key. The key is stored only encrypted ([SecretCipher]),
 * read only when a request is made, and never logged, exported or backed up.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    @AiPreferences private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val time: TimeProvider,
) {
    private object Keys {
        val provider = stringPreferencesKey("provider")
        val baseUrl = stringPreferencesKey("base_url")
        val model = stringPreferencesKey("model")
        val enabled = booleanPreferencesKey("enabled")
        val consentAt = longPreferencesKey("consent_at")
        val sealedKey = stringPreferencesKey("sealed_key")
    }

    private val data: Flow<Preferences> = dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val settings: Flow<AiSettings> = data.map { prefs ->
        val consent = prefs[Keys.consentAt]?.let(Instant::ofEpochMilli)
        AiSettings(
            providerId = prefs[Keys.provider],
            baseUrl = prefs[Keys.baseUrl].orEmpty(),
            model = prefs[Keys.model].orEmpty(),
            enabled = prefs[Keys.enabled] == true && consent != null,
            consentAt = consent,
            hasKey = prefs[Keys.sealedKey] != null,
        )
    }

    suspend fun current(): AiSettings = settings.first()

    suspend fun setProvider(provider: AiProvider, baseUrl: String = "", model: String = provider.suggestedModels.firstOrNull().orEmpty()) {
        dataStore.edit {
            it[Keys.provider] = provider.id
            it[Keys.baseUrl] = baseUrl.trim()
            it[Keys.model] = model.trim()
        }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[Keys.model] = model.trim() }
    }

    suspend fun setBaseUrl(baseUrl: String) {
        dataStore.edit { it[Keys.baseUrl] = baseUrl.trim() }
    }

    /** Stores the key encrypted; a blank key removes it. */
    suspend fun setApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return clearApiKey()
        val sealed = Base64.getEncoder().encodeToString(cipher.encrypt(trimmed.toByteArray(Charsets.UTF_8)))
        dataStore.edit { it[Keys.sealedKey] = sealed }
    }

    suspend fun clearApiKey() {
        dataStore.edit { it.remove(Keys.sealedKey) }
    }

    /**
     * Turns the assistant on. Callers show what is sent and where first; this records that
     * consent with the current time. Turning it off keeps the consent record.
     */
    suspend fun enableWithConsent() {
        val now = time.now().toEpochMilli()
        dataStore.edit {
            it[Keys.consentAt] = now
            it[Keys.enabled] = true
        }
    }

    suspend fun disable() {
        dataStore.edit { it[Keys.enabled] = false }
    }

    /** Removes everything, including the key (for example "forget my provider"). */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    /**
     * The endpoint for a request, or null when the assistant is off or incomplete. A key that
     * cannot be decrypted (for example after the device's keys were reset) counts as missing.
     */
    suspend fun endpoint(): AiEndpoint? {
        val prefs = data.first()
        val settings = current()
        if (!settings.enabled) return null
        val provider = settings.provider ?: return null
        val baseUrl = settings.effectiveBaseUrl.takeIf { it.isNotBlank() } ?: return null
        val model = settings.model.takeIf { it.isNotBlank() } ?: return null
        val sealed = prefs[Keys.sealedKey] ?: return null
        val key = runCatching { String(cipher.decrypt(Base64.getDecoder().decode(sealed)), Charsets.UTF_8) }.getOrNull() ?: return null
        return AiEndpoint(provider.wireFormat, baseUrl, model, key)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds abstract fun bindCipher(impl: AndroidKeystoreCipher): SecretCipher
}

@Module
@InstallIn(SingletonComponent::class)
object AiStorageModule {
    /** Not the user preferences file, so it is never exported or backed up. */
    private const val FILE = "planb_ai_settings"

    @Provides
    @Singleton
    @AiPreferences
    fun provideAiDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(FILE) },
    )
}
