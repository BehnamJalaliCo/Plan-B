package com.behnamjalali.planb.core.data.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A body that cannot be decrypted: wrong passphrase, damaged or tampered data. */
class NoteCryptoException(message: String, cause: Throwable? = null) : GeneralSecurityException(message, cause)

/** Salt and work factor of an envelope; together with the passphrase they give its key. */
class KeyParams(val salt: ByteArray, val iterations: Int) {
    /** Stable identity of the derived key (salt and work factor), never the key itself. */
    val id: String get() = salt.joinToString("") { "%02x".format(it) } + ":" + iterations
}

/**
 * Encryption of locked note bodies (Plan-B Pro #36).
 *
 * The key is derived from the user's passphrase with PBKDF2-HMAC-SHA256 and a random salt, so
 * a body stays readable on another device after a restore (an Android Keystore key never leaves
 * the device). Bodies are sealed with AES-256-GCM. The envelope is self-describing:
 *
 * ```
 * version (1 byte = 1) | iterations (4 bytes, big endian) | salt (16) | IV (12) | ciphertext + GCM tag (16)
 * ```
 *
 * The header (version, iterations, salt and IV) is authenticated as associated data, so any
 * change to the envelope, including its header, fails to decrypt.
 */
object NoteCrypto {
    const val VERSION: Byte = 1

    /** OWASP's recommendation for PBKDF2-HMAC-SHA256. The key is derived once per session. */
    const val DEFAULT_ITERATIONS = 600_000

    /** Envelopes outside these bounds are rejected (a crafted file must not stall the app). */
    const val MIN_ITERATIONS = 1_000
    const val MAX_ITERATIONS = 10_000_000

    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 1 + 4 + SALT_BYTES + IV_BYTES
    private const val TAG_BYTES = TAG_BITS / 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    /** Derives the 256-bit key. Slow on purpose; call it off the main thread. */
    fun deriveKey(passphrase: CharArray, params: KeyParams): ByteArray {
        require(params.salt.size == SALT_BYTES) { "Bad salt" }
        require(params.iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Bad iteration count" }
        val spec = PBEKeySpec(passphrase, params.salt, params.iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, params: KeyParams, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(VERSION)
            .putInt(params.iterations)
            .put(params.salt)
            .put(iv)
            .array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    /** The key parameters of [envelope]; throws [NoteCryptoException] when it is malformed. */
    fun params(envelope: ByteArray): KeyParams {
        if (envelope.size < HEADER_BYTES + TAG_BYTES) throw NoteCryptoException("Envelope too short")
        if (envelope[0] != VERSION) throw NoteCryptoException("Unknown envelope version")
        val buffer = ByteBuffer.wrap(envelope, 1, 4 + SALT_BYTES)
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw NoteCryptoException("Bad iteration count")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        return KeyParams(salt, iterations)
    }

    /** Decrypts [envelope] with [key]; a wrong key or any change to the data throws [NoteCryptoException]. */
    fun decrypt(key: ByteArray, envelope: ByteArray): ByteArray {
        params(envelope)
        val iv = envelope.copyOfRange(1 + 4 + SALT_BYTES, HEADER_BYTES)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(envelope, 0, HEADER_BYTES)
            cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw NoteCryptoException("Cannot decrypt", e)
        }
    }
}
