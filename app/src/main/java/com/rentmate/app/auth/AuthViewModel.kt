package com.rentmate.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val hasHousehold: Boolean) : AuthUiState
    data class Error(val message: String) : AuthUiState
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
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Sign-in failed") }
        }
    }
}
