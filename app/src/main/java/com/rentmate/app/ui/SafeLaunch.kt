package com.rentmate.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches [block] in [viewModelScope] so that no exception can escape and crash the app.
 *
 * Anything [block] throws (IOException when offline, RestException from Supabase, ...)
 * is logged and handed to [onError] so the screen can show an error state.
 * Cancellation is rethrown so leaving a screen still stops its work.
 * A [CoroutineExceptionHandler] is installed as a last resort for failures in
 * child coroutines that [block] starts itself.
 */
fun ViewModel.launchSafely(
    tag: String,
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
): Job {
    val lastResort = CoroutineExceptionHandler { _, throwable ->
        Log.e(tag, "Uncaught coroutine failure", throwable)
        onError(throwable)
    }
    return viewModelScope.launch(lastResort) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Repository call failed", e)
            onError(e)
        }
    }
}
