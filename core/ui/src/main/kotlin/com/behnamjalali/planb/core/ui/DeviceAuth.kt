package com.behnamjalali.planb.core.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * The device's own lock (fingerprint, face, PIN, pattern or password) through BiometricPrompt,
 * for App lock and for unlocking locked notes with a fingerprint (Plan-B Pro #36).
 */
object DeviceAuth {
    private const val DEVICE_LOCK = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Whether the device can confirm the user (a biometric or a screen lock is set up). */
    fun canAuthenticate(context: Context): Boolean =
        runCatching { BiometricManager.from(context).canAuthenticate(DEVICE_LOCK) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

    /** Whether a strong biometric (needed to release a Keystore key) is enrolled. */
    fun canUseStrongBiometric(context: Context): Boolean =
        runCatching { BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

    /** Asks for the device lock; [onResult] gets true only after a successful authentication. */
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(DEVICE_LOCK)
            .build()
        prompt(activity, onSuccess = { onResult(true) }, onFailure = { onResult(false) }).authenticate(info)
    }

    /** Authenticates [cipher] with a strong biometric; [onResult] gets the usable cipher, or null. */
    fun authenticate(activity: FragmentActivity, title: String, cancelLabel: String, cipher: Cipher, onResult: (Cipher?) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancelLabel)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt(activity, onSuccess = { onResult(it.cryptoObject?.cipher) }, onFailure = { onResult(null) })
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun prompt(
        activity: FragmentActivity,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onFailure: () -> Unit,
    ) = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess(result)

            // A single failed attempt keeps the prompt open; only errors (cancel, lockout) end it.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
        },
    )
}

/** The hosting activity, if it can show a BiometricPrompt. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
