package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rentmate.app.R
import com.rentmate.app.auth.BiometricAuthenticator
import com.rentmate.app.auth.BiometricAvailability
import com.rentmate.app.auth.BiometricLockPolicy
import com.rentmate.app.auth.EnableOutcome

private const val TAG = "BiometricSettings"

/**
 * RM-0014: state and behaviour for the biometric row on the Settings screen.
 * [availability] is refreshed whenever the screen resumes, so enrolling a
 * fingerprint in system settings and coming back updates the row.
 */
class BiometricToggleState internal constructor(
    availability: BiometricAvailability,
    private val showEnrolDialog: (Boolean) -> Unit,
    private val applyToggle: (Boolean) -> Unit
) {
    /** What this device can do right now. */
    val availability: BiometricAvailability = availability

    /**
     * The user flipped the switch to [on]. Turning off always works; turning on is
     * checked against [availability] and may open the enrolment dialog instead.
     */
    fun onToggle(on: Boolean) {
        if (!on) {
            Log.d(TAG, "toggle off")
            applyToggle(false)
            return
        }
        when (val outcome = BiometricLockPolicy.onEnableRequested(availability)) {
            EnableOutcome.ENABLE -> {
                Log.d(TAG, "toggle on: enabled")
                applyToggle(true)
            }
            EnableOutcome.OFFER_ENROLMENT -> {
                Log.w(TAG, "toggle on refused ($outcome): nothing enrolled, offering enrolment")
                showEnrolDialog(true)
            }
            EnableOutcome.EXPLAIN_UNSUPPORTED ->
                Log.w(TAG, "toggle on refused ($outcome): availability=$availability")
        }
    }
}

/** The explanation string shown under the toggle for [availability], or null when none is needed. */
fun biometricNoteRes(availability: BiometricAvailability): Int? = when (availability) {
    BiometricAvailability.AVAILABLE -> null
    BiometricAvailability.NONE_ENROLLED -> R.string.biometric_note_none_enrolled
    BiometricAvailability.NO_HARDWARE -> R.string.biometric_note_no_hardware
    BiometricAvailability.UNAVAILABLE -> R.string.biometric_note_unavailable
}

/** Remembers a [BiometricToggleState]; [applyToggle] persists the new value. */
@Composable
fun rememberBiometricToggleState(applyToggle: (Boolean) -> Unit): BiometricToggleState {
    val context = LocalContext.current
    var availability by remember { mutableStateOf(BiometricAuthenticator.availability(context)) }
    var showDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        availability = BiometricAuthenticator.availability(context)
    }

    if (showDialog) {
        BiometricEnrolDialog(
            onOpenSettings = {
                showDialog = false
                try {
                    context.startActivity(BiometricAuthenticator.enrolmentIntent())
                    Log.d(TAG, "opened enrolment settings")
                } catch (e: Exception) {
                    Log.e(TAG, "could not open enrolment settings", e)
                }
            },
            onDismiss = { showDialog = false }
        )
    }
    return BiometricToggleState(availability, { showDialog = it }, applyToggle)
}

/** Offers to open the system screen where a fingerprint, face or screen lock is set up. */
@Composable
fun BiometricEnrolDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.biometric_enrol_title)) },
        text = { Text(stringResource(R.string.biometric_enrol_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.biometric_enrol_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.biometric_enrol_cancel)) }
        }
    )
}
