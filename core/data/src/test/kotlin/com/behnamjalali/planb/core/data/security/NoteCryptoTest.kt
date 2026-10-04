package com.behnamjalali.planb.core.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class NoteCryptoTest {
    private val params = KeyParams(NoteCrypto.newSalt(), ITERATIONS)
    private val key = NoteCrypto.deriveKey("درست horse battery".toCharArray(), params)
    private val text = "یادداشت محرمانه — secret note ✓".toByteArray(Charsets.UTF_8)

    @Test
    fun roundTrip_restoresTheText_andCarriesItsKeyParameters() {
        val envelope = NoteCrypto.encrypt(key, params, text)
        assertThat(envelope[0]).isEqualTo(NoteCrypto.VERSION)
        assertThat(NoteCrypto.params(envelope).id).isEqualTo(params.id)
        assertThat(NoteCrypto.decrypt(key, envelope)).isEqualTo(text)
        // The plaintext never appears in the envelope.
        assertThat(String(envelope, Charsets.ISO_8859_1)).doesNotContain("secret")
    }

    @Test
    fun samePassphraseAndSalt_deriveTheSameKey_onAnotherDevice() {
        val envelope = NoteCrypto.encrypt(key, params, text)
        val again = NoteCrypto.deriveKey("درست horse battery".toCharArray(), NoteCrypto.params(envelope))
        assertThat(NoteCrypto.decrypt(again, envelope)).isEqualTo(text)
    }

    @Test
    fun encryptingTwice_usesFreshIvs() {
        assertThat(NoteCrypto.encrypt(key, params, text)).isNotEqualTo(NoteCrypto.encrypt(key, params, text))
    }

    @Test
    fun wrongPassphrase_failsToDecrypt() {
        val envelope = NoteCrypto.encrypt(key, params, text)
        val wrong = NoteCrypto.deriveKey("wrong passphrase".toCharArray(), params)
        assertThrows(NoteCryptoException::class.java) { NoteCrypto.decrypt(wrong, envelope) }
    }

    @Test
    fun anyChange_toBodyOrHeader_isDetected() {
        val envelope = NoteCrypto.encrypt(key, params, text)
        // Ciphertext, IV, salt and the last byte of the tag.
        for (index in listOf(envelope.size - 20, 1 + 4 + NoteCrypto.SALT_BYTES + 2, 1 + 4 + 3, envelope.lastIndex)) {
            val tampered = envelope.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertThrows(NoteCryptoException::class.java) { NoteCrypto.decrypt(key, tampered) }
        }
        // The work factor is authenticated too.
        val iterations = envelope.copyOf().also { it[4] = (it[4].toInt() xor 0x01).toByte() }
        assertThrows(NoteCryptoException::class.java) { NoteCrypto.decrypt(key, iterations) }
    }

    @Test
    fun malformedEnvelopes_areRejected() {
        assertThrows(NoteCryptoException::class.java) { NoteCrypto.params(ByteArray(10)) }
        val envelope = NoteCrypto.encrypt(key, params, text)
        val otherVersion = envelope.copyOf().also { it[0] = 9 }
        assertThrows(NoteCryptoException::class.java) { NoteCrypto.decrypt(key, otherVersion) }
        // A crafted work factor beyond the limit is refused before any key derivation.
        val huge = envelope.copyOf().also { it[1] = 0x7f }
        assertThrows(NoteCryptoException::class.java) { NoteCrypto.params(huge) }
    }

    @Test
    fun defaultWorkFactor_isHigh() {
        assertThat(NoteCrypto.DEFAULT_ITERATIONS).isAtLeast(600_000)
    }

    private companion object {
        const val ITERATIONS = 1_000
    }
}
