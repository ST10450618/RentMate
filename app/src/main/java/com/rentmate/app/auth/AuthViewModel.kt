package com.rentmate.app.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.ui.error.ErrorMapper

private const val TAG = "AuthViewModel"

/** What the Login screen shows while the user signs in. */
sealed interface AuthUiState {
    /** No sign-in has started yet. */
    data object Idle : AuthUiState
    /** The account picker or Supabase call is in progress. */
    data object Loading : AuthUiState
    /** Signed in; [hasHousehold] decides between Household setup and the Dashboard. */
    data class Success(val hasHousehold: Boolean) : AuthUiState
    /** [message] is the raw failure for logs only; show [messageRes] to the user. */
    data class Error(
        val message: String,
        @StringRes val messageRes: Int = R.string.error_sign_in_failed
    ) : AuthUiState
}

/**
 * Drives the Login screen (US-1). It runs [AuthRepository.signIn] and turns the
 * outcome into an [AuthUiState], with a user-facing message chosen by [ErrorMapper].
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    /** Current Login screen state. */
    val state: StateFlow<AuthUiState> = _state

    /** Starts Google sign-in; the result arrives through [state]. */
    fun signIn() {
        viewModelScope.launch {
            Log.d(TAG, "sign-in requested")
            _state.value = AuthUiState.Loading
            repository.signIn()
                .onSuccess {
                    Log.d(TAG, "state -> Success")
                    _state.value = AuthUiState.Success(it.hasHousehold)
                }
                .onFailure {
                    Log.w(TAG, "state -> Error", it)
                    _state.value = AuthUiState.Error(
                        message = it.message ?: "Sign-in failed",
                        messageRes = ErrorMapper.toMessage(it, fallback = R.string.error_sign_in_failed)
                    )
                }
        }
    }
}
