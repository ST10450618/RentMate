package com.rentmate.app.auth

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.ui.screens.LockScreen

private const val TAG = "BiometricLockHost"

/**
 * RM-0013: wraps the app so its content stays hidden behind [LockScreen] until the
 * user passes a biometric / device-credential prompt.
 *
 * The lock applies only when the start destination came from a restored session
 * (anything other than Login). The content stays composed underneath so navigation
 * state survives a re-lock, but it is covered by an opaque, touch-blocking screen.
 */
@Composable
fun BiometricLockHost(content: @Composable () -> Unit) {
    val startupViewModel: StartupViewModel = hiltViewModel()
    val lockViewModel: BiometricLockViewModel = hiltViewModel()
    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    val lock by lockViewModel.state.collectAsStateWithLifecycle()

    val ready = startup as? StartupState.Ready
    val restored = ready != null && ready.destination != StartDestination.LOGIN

    LaunchedEffect(ready) {
        if (ready != null) lockViewModel.onStartupReady(restoredSession = restored)
    }

    // Track time in the background so a long absence re-locks the app.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        lockViewModel.onBackgrounded(SystemClock.elapsedRealtime())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        lockViewModel.onForegrounded(SystemClock.elapsedRealtime())
    }

    // Cover the content while locked, and also in the gap before the launch decision
    // is made, so the Dashboard never flashes before the prompt.
    val covered = lock is LockState.Locked || (lock is LockState.Undecided && restored)

    val activity = LocalContext.current.findFragmentActivity()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val cancel = stringResource(R.string.lock_prompt_cancel)

    val attempt = (lock as? LockState.Locked)?.attempt
    LaunchedEffect(attempt) {
        if (attempt == null) return@LaunchedEffect
        if (activity == null) {
            Log.e(TAG, "no FragmentActivity; cannot show prompt")
            lockViewModel.onAuthFailed(AuthFailure.OTHER, -1, "no FragmentActivity")
            return@LaunchedEffect
        }
        Log.d(TAG, "showing prompt, attempt=$attempt")
        BiometricAuthenticator.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            cancelText = cancel,
            onSuccess = lockViewModel::onAuthenticated,
            onFailure = lockViewModel::onAuthFailed
        )
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (covered) {
            LockScreen(
                failure = (lock as? LockState.Locked)?.failure,
                onRetry = lockViewModel::retry
            )
        }
    }
}

/** Walks the [ContextWrapper] chain to find the activity that can host a BiometricPrompt. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
