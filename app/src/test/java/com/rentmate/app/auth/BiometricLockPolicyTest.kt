package com.rentmate.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0013: when the app locks at launch and after time in the background. */
class BiometricLockPolicyTest {

    private val ok = BiometricAvailability.AVAILABLE

    @Test
    fun launch_locks_restoredSession_withToggleOn() {
        assertTrue(BiometricLockPolicy.shouldLockAtLaunch(true, true, ok))
    }

    @Test
    fun launch_doesNotLock_afterFreshSignIn() {
        assertFalse(BiometricLockPolicy.shouldLockAtLaunch(true, false, ok))
    }

    @Test
    fun launch_doesNotLock_whenToggleOff() {
        assertFalse(BiometricLockPolicy.shouldLockAtLaunch(false, true, ok))
    }

    @Test
    fun launch_doesNotLock_whenDeviceCannotAuthenticate() {
        listOf(
            BiometricAvailability.NONE_ENROLLED,
            BiometricAvailability.NO_HARDWARE,
            BiometricAvailability.UNAVAILABLE
        ).forEach {
            assertFalse("$it", BiometricLockPolicy.shouldLockAtLaunch(true, true, it))
        }
    }

    @Test
    fun relock_after60s() {
        assertTrue(BiometricLockPolicy.shouldRelock(true, ok, false, 1_000L, 1_000L + RELOCK_AFTER_MS))
    }

    @Test
    fun noRelock_before60s() {
        assertFalse(BiometricLockPolicy.shouldRelock(true, ok, false, 1_000L, 1_000L + RELOCK_AFTER_MS - 1))
    }

    @Test
    fun noRelock_ifNeverBackgrounded() {
        assertFalse(BiometricLockPolicy.shouldRelock(true, ok, false, null, 999_999L))
    }

    @Test
    fun noRelock_ifToggleOffOrUnavailableOrAlreadyLocked() {
        val later = 1_000L + RELOCK_AFTER_MS * 2
        assertFalse(BiometricLockPolicy.shouldRelock(false, ok, false, 1_000L, later))
        assertFalse(BiometricLockPolicy.shouldRelock(true, BiometricAvailability.NO_HARDWARE, false, 1_000L, later))
        assertFalse(BiometricLockPolicy.shouldRelock(true, ok, true, 1_000L, later))
    }

    @Test
    fun availability_mapsCanAuthenticateCodes() {
        assertEquals(BiometricAvailability.AVAILABLE, availabilityFromCode(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(BiometricAvailability.NONE_ENROLLED, availabilityFromCode(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(BiometricAvailability.NO_HARDWARE, availabilityFromCode(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertEquals(BiometricAvailability.UNAVAILABLE, availabilityFromCode(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        assertEquals(BiometricAvailability.UNAVAILABLE, availabilityFromCode(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED))
    }

    @Test
    fun errorCodes_areClassified() {
        assertEquals(AuthFailure.CANCELLED, failureFromErrorCode(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(AuthFailure.CANCELLED, failureFromErrorCode(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(AuthFailure.LOCKOUT, failureFromErrorCode(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(AuthFailure.NO_CREDENTIALS, failureFromErrorCode(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(AuthFailure.OTHER, failureFromErrorCode(BiometricPrompt.ERROR_HW_UNAVAILABLE))
    }
}
