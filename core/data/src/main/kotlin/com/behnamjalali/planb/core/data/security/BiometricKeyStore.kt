package com.behnamjalali.planb.core.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An Android Keystore AES key that can only be used right after a strong biometric
 * authentication. It wraps the note vault key so a fingerprint can unlock locked notes; it never
 * leaves the device and is invalidated when a new fingerprint is enrolled (the passphrase is then
 * needed again).
 */
@Singleton
class BiometricKeyStore @Inject constructor() {
    /** A cipher to wrap the vault key, to be authenticated by BiometricPrompt; null when unavailable. */
    fun encryptCipher(): Cipher? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher
    }.getOrNull()

    /** A cipher to unwrap the vault key; null when the key is gone or was invalidated. */
    fun decryptCipher(iv: ByteArray): Cipher? = runCatching {
        val key = key(create = false) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher
    }.getOrNull()

    fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(builder.build())
            generateKey()
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "planb_note_vault_biometric"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
