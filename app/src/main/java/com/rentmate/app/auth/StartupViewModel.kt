package com.rentmate.app.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StartupViewModel"

/** Splash while the saved session loads, then the screen to start on. */
sealed interface StartupState {
    /** The saved session is still loading; show the splash. */
    data object Loading : StartupState
    /** The first screen is known. */
    data class Ready(val destination: StartDestination) : StartupState
}

/**
 * RM-0002: decides the app's start destination once per launch, so a user who
 * signed in before lands on the Dashboard instead of Login.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)

    /** Splash or the chosen start destination. */
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = try {
                sessionRepository.restoreSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "session restore failed; showing Login", e)
                StartDestination.LOGIN
            }
            Log.d(TAG, "start destination = $destination")
            _state.value = StartupState.Ready(destination)
        }
    }
}
