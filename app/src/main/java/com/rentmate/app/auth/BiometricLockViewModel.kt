package com.rentmate.app.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BiometricLockVM"

/** Lock state shown by [BiometricLockHost]. */
sealed interface LockState {
    /** Startup has not been evaluated yet (content stays hidden if a session was restored). */
    data object Undecided : LockState

    /** Content is visible. */
    data object Unlocked : LockState

    /**
     * Content is hidden. [attempt] increases each time a prompt should be shown;
     * [failure] is the reason the last attempt did not succeed, if any.
     */
    data class Locked(val attempt: Int = 0, val failure: AuthFailure? = null) : LockState
}

/**
 * RM-0013: owns the lock state for the whole activity. It decides, using
 * [BiometricLockPolicy], whether to lock at launch and after a long stay in the
 * background, and records the outcome of each prompt.
 */
@HiltViewModel
class BiometricLockViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val settings = SettingsRepository(context)
    private val _state = MutableStateFlow<LockState>(LockState.Undecided)

    /** Current lock state. */
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundedAtMs: Long? = null
    private var decided = false

    /**
     * Called once the start destination is known. [restoredSession] is true when
     * the user was signed in from a saved session rather than at the Login screen.
     */
    fun onStartupReady(restoredSession: Boolean) {
        if (decided) return
        decided = true
        viewModelScope.launch {
            val toggleOn = readToggle()
            val availability = BiometricAuthenticator.availability(context)
            val lock = BiometricLockPolicy.shouldLockAtLaunch(toggleOn, restoredSession, availability)
            Log.d(TAG, "launch: toggle=$toggleOn restored=$restoredSession availability=$availability lock=$lock")
            _state.value = if (lock) LockState.Locked() else LockState.Unlocked
        }
    }

    /** The app left the foreground; remember when. [nowMs] is a monotonic clock. */
    fun onBackgrounded(nowMs: Long) {
        if (backgroundedAtMs == null) backgroundedAtMs = nowMs
        Log.d(TAG, "backgrounded at $nowMs")
    }

    /** The app is visible again; lock if it was away for at least [RELOCK_AFTER_MS]. */
    fun onForegrounded(nowMs: Long) {
        val since = backgroundedAtMs ?: return
        backgroundedAtMs = null
        if (!decided || _state.value is LockState.Undecided) return
        viewModelScope.launch {
            val toggleOn = readToggle()
            val availability = BiometricAuthenticator.availability(context)
            val lock = BiometricLockPolicy.shouldRelock(
                toggleOn, availability, _state.value is LockState.Locked, since, nowMs
            )
            Log.d(TAG, "foreground after ${nowMs - since}ms: toggle=$toggleOn availability=$availability relock=$lock")
            if (lock) _state.value = LockState.Locked()
        }
    }

    /** The prompt succeeded; reveal the content. */
    fun onAuthenticated() {
        Log.d(TAG, "authentication succeeded")
        _state.value = LockState.Unlocked
    }

    /** The prompt ended with [failure]; stay locked and let the user retry. */
    fun onAuthFailed(failure: AuthFailure, code: Int, message: CharSequence?) {
        Log.w(TAG, "authentication failed: failure=$failure code=$code message=$message")
        val current = _state.value as? LockState.Locked ?: return
        _state.value = current.copy(failure = failure)
    }

    /** The user pressed Retry on the lock screen. */
    fun retry() {
        Log.d(TAG, "retry requested")
        val current = _state.value as? LockState.Locked ?: return
        _state.value = LockState.Locked(attempt = current.attempt + 1)
    }

    private suspend fun readToggle(): Boolean = try {
        settings.settings.first().isOn(SettingToggle.BIOMETRIC_UNLOCK)
    } catch (e: Exception) {
        Log.e(TAG, "could not read biometric toggle; treating as off", e)
        false
    }
}
