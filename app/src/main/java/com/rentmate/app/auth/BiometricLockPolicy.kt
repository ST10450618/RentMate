package com.rentmate.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** How long the app may stay in the background before the lock returns (RM-0013). */
const val RELOCK_AFTER_MS = 60_000L

/** What this device can do right now, derived from `BiometricManager.canAuthenticate()`. */
enum class BiometricAvailability {
    /** A fingerprint, face or screen-lock credential is ready to use. */
    AVAILABLE,

    /** Hardware exists but nothing is enrolled; the user can be sent to Settings. */
    NONE_ENROLLED,

    /** The device has no biometric hardware (or none that is usable). */
    NO_HARDWARE,

    /** Temporarily or permanently unusable (unsupported, security update, busy). */
    UNAVAILABLE
}

/** Maps a `BiometricManager.BIOMETRIC_*` result code to [BiometricAvailability]. */
fun availabilityFromCode(code: Int): BiometricAvailability = when (code) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
    else -> BiometricAvailability.UNAVAILABLE
}

/** Why an authentication attempt ended without success. */
enum class AuthFailure { CANCELLED, LOCKOUT, NO_CREDENTIALS, OTHER }

/** Classifies a `BiometricPrompt.ERROR_*` code so the lock screen can explain it. */
fun failureFromErrorCode(code: Int): AuthFailure = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED -> AuthFailure.CANCELLED
    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthFailure.LOCKOUT
    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> AuthFailure.NO_CREDENTIALS
    else -> AuthFailure.OTHER
}

/** What to do when the user flips the biometric toggle on (RM-0014). */
enum class EnableOutcome {
    /** Turn the setting on. */
    ENABLE,

    /** Do not enable yet; offer to open the system enrolment screen. */
    OFFER_ENROLMENT,

    /** Do not enable; explain that this device cannot use biometrics. */
    EXPLAIN_UNSUPPORTED
}

/**
 * Pure decision logic for the biometric lock, kept free of Android types so it is
 * unit-testable.
 */
object BiometricLockPolicy {

    /**
     * Whether to lock when the app launches.
     *
     * Only a session restored from storage is locked; an interactive sign-in just
     * proved who the user is. If the device cannot authenticate (nothing enrolled,
     * no hardware) we do not lock, otherwise the user would be locked out for good.
     */
    fun shouldLockAtLaunch(
        toggleOn: Boolean,
        restoredSession: Boolean,
        availability: BiometricAvailability
    ): Boolean = toggleOn && restoredSession && availability == BiometricAvailability.AVAILABLE

    /**
     * Whether to lock again when the app returns to the foreground.
     *
     * @param backgroundedAtMs monotonic time the app went to the background, or null if it did not
     * @param nowMs monotonic time now (same clock as [backgroundedAtMs])
     */
    fun shouldRelock(
        toggleOn: Boolean,
        availability: BiometricAvailability,
        alreadyLocked: Boolean,
        backgroundedAtMs: Long?,
        nowMs: Long
    ): Boolean {
        if (alreadyLocked || backgroundedAtMs == null) return false
        if (!toggleOn || availability != BiometricAvailability.AVAILABLE) return false
        return nowMs - backgroundedAtMs >= RELOCK_AFTER_MS
    }

    /** Decides what happens when the user tries to switch the biometric toggle on. */
    fun onEnableRequested(availability: BiometricAvailability): EnableOutcome = when (availability) {
        BiometricAvailability.AVAILABLE -> EnableOutcome.ENABLE
        BiometricAvailability.NONE_ENROLLED -> EnableOutcome.OFFER_ENROLMENT
        BiometricAvailability.NO_HARDWARE,
        BiometricAvailability.UNAVAILABLE -> EnableOutcome.EXPLAIN_UNSUPPORTED
    }

    /** True when the toggle can be used at all on this device (hardware exists). */
    fun isToggleUsable(availability: BiometricAvailability): Boolean =
        availability == BiometricAvailability.AVAILABLE ||
            availability == BiometricAvailability.NONE_ENROLLED
}
