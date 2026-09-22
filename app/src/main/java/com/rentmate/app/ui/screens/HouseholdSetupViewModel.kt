package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.CreateHouseholdRequest
import com.rentmate.app.network.HouseholdsApi
import com.rentmate.app.network.JoinHouseholdRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HouseholdSetupUiState {
    data object Idle : HouseholdSetupUiState
    data object Loading : HouseholdSetupUiState
    /** Created shows the invite code (S2's "generate code with share action") before continuing. */
    data class Created(val inviteCode: String) : HouseholdSetupUiState
    data object Joined : HouseholdSetupUiState
    data class Error(val message: String) : HouseholdSetupUiState
}

@HiltViewModel
class HouseholdSetupViewModel @Inject constructor(
    private val householdsApi: HouseholdsApi,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow<HouseholdSetupUiState>(HouseholdSetupUiState.Idle)
    val state: StateFlow<HouseholdSetupUiState> = _state

    /** US-5: creates a household; the server returns its 6-character invite code. */
    fun create(name: String) = run {
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching { householdsApi.create(CreateHouseholdRequest(name)) }
                .onSuccess {
                    currentHousehold.set(it.id)
                    _state.value = HouseholdSetupUiState.Created(it.inviteCode)
                }
                .onFailure { _state.value = HouseholdSetupUiState.Error(it.message ?: "Could not create household") }
        }
    }

    /** US-5: joins by invite code; an invalid/invalidated code fails without a membership record. */
    fun join(inviteCode: String) = run {
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching { householdsApi.join(JoinHouseholdRequest(inviteCode)) }
                .onSuccess {
                    currentHousehold.set(it.id)
                    _state.value = HouseholdSetupUiState.Joined
                }
                .onFailure { _state.value = HouseholdSetupUiState.Error(it.message ?: "Invalid invite code") }
        }
    }
}
