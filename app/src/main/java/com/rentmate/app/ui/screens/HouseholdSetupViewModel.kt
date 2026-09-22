package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface HouseholdSetupUiState {
    data object Idle : HouseholdSetupUiState
    data object Loading : HouseholdSetupUiState
    /** Created shows the invite code (S2's "generate code with share action") before continuing. */
    data class Created(val inviteCode: String) : HouseholdSetupUiState
    data object Joined : HouseholdSetupUiState
    data class Error(val message: String) : HouseholdSetupUiState
}

@Serializable
private data class CreateHouseholdParams(val household_name: String)

@Serializable
private data class JoinHouseholdParams(val code: String)

@HiltViewModel
class HouseholdSetupViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow<HouseholdSetupUiState>(HouseholdSetupUiState.Idle)
    val state: StateFlow<HouseholdSetupUiState> = _state

    /** US-5: create_household RPC generates the 6-character invite code. */
    fun create(name: String) {
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("create_household", CreateHouseholdParams(name).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                _state.value = HouseholdSetupUiState.Created(it.invite_code)
            }.onFailure {
                _state.value = HouseholdSetupUiState.Error(it.message ?: "Could not create household")
            }
        }
    }

    /** US-5: join_household RPC fails without creating a membership row for an invalid code. */
    fun join(inviteCode: String) {
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("join_household", JoinHouseholdParams(inviteCode).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                _state.value = HouseholdSetupUiState.Joined
            }.onFailure {
                _state.value = HouseholdSetupUiState.Error(it.message ?: "Invalid invite code")
            }
        }
    }
}
