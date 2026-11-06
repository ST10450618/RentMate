package com.rentmate.app.auth

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

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val hasHousehold: Boolean) : AuthUiState
    /** [message] is the raw failure for logs only; show [messageRes] to the user. */
    data class Error(
        val message: String,
        @StringRes val messageRes: Int = R.string.error_sign_in_failed
    ) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state

    fun signIn() {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            repository.signIn()
                .onSuccess { _state.value = AuthUiState.Success(it.hasHousehold) }
                .onFailure {
                    _state.value = AuthUiState.Error(
                        message = it.message ?: "Sign-in failed",
                        messageRes = ErrorMapper.toMessage(it, fallback = R.string.error_sign_in_failed)
                    )
                }
        }
    }
}
