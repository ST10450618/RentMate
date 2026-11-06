package com.rentmate.app.auth

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val TAG = "BiometricAuth"

/**
 * Thin wrapper over androidx.biometric: capability check, enrolment intent and
 * showing the system prompt. Decision logic lives in [BiometricLockPolicy].
 */
object BiometricAuthenticator {

    /** What this device accepts: see [authenticatorsFor]. */
    val authenticators: Int = authenticatorsFor(Build.VERSION.SDK_INT)

    /**
     * Fingerprint/face, or the screen-lock PIN/pattern/password, on every API level,
     * so a phone that only has a PIN can still lock the app.
     *
     * androidx.biometric does not support BIOMETRIC_STRONG | DEVICE_CREDENTIAL on
     * API 28-29, so before API 30 we use BIOMETRIC_WEAK | DEVICE_CREDENTIAL, which is
     * supported everywhere. Weak (class 2) biometrics are fine here: this is an app
     * lock, not a crypto key. Because DEVICE_CREDENTIAL is always included, the
     * prompt never has a Cancel button; the user backs out instead.
     */
    fun authenticatorsFor(sdkInt: Int): Int =
        if (sdkInt >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

    /** Asks the system what this device can do right now. Never throws. */
    fun availability(context: Context): BiometricAvailability = try {
        val code = BiometricManager.from(context).canAuthenticate(authenticators)
        Log.d(TAG, "canAuthenticate code=$code")
        availabilityFromCode(code)
    } catch (e: Exception) {
        Log.e(TAG, "canAuthenticate failed", e)
        BiometricAvailability.UNAVAILABLE
    }

    /** Intent that opens the system screen to enrol a fingerprint/face or screen lock. */
    fun enrolmentIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    /**
     * Shows the system prompt. Exactly one of [onSuccess] / [onFailure] is called per
     * terminal outcome. Failure callbacks carry the raw error code and message.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelText: String,
        onSuccess: () -> Unit,
        onFailure: (AuthFailure, Int, CharSequence?) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "prompt succeeded type=${result.authenticationType}")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.w(TAG, "prompt error $errorCode: $errString")
                onFailure(failureFromErrorCode(errorCode), errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                // A non-matching finger: the prompt stays open and lets the user retry.
                Log.d(TAG, "prompt: biometric not recognised")
            }
        }
        try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .apply {
                    // A negative button is not allowed together with DEVICE_CREDENTIAL.
                    if (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
                        setNegativeButtonText(cancelText)
                    }
                }
                .build()
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        } catch (e: Exception) {
            Log.e(TAG, "could not show prompt", e)
            onFailure(AuthFailure.OTHER, -1, e.message)
        }
    }
}
