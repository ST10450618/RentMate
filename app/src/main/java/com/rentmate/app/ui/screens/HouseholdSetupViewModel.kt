package com.rentmate.app.ui.screens

import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateInviteCode
import com.rentmate.app.ui.validation.validateRequiredText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.data.activity.FirebaseSession
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter

sealed interface HouseholdSetupUiState {
    data object Idle : HouseholdSetupUiState
    data object Loading : HouseholdSetupUiState
    /** Created shows the invite code (S2's "generate code with share action") before continuing. */
    data class Created(val inviteCode: String) : HouseholdSetupUiState
    data object Joined : HouseholdSetupUiState
}

@Serializable
private data class CreateHouseholdParams(val household_name: String)

@Serializable
private data class JoinHouseholdParams(val code: String)

@HiltViewModel
class HouseholdSetupViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val firebaseSession: FirebaseSession,
    private val feedback: FeedbackCenter
) : ViewModel() {
    private val _state = MutableStateFlow<HouseholdSetupUiState>(HouseholdSetupUiState.Idle)
    val state: StateFlow<HouseholdSetupUiState> = _state

    /** US-5: create_household RPC generates the 6-character invite code. */
    fun create(name: String) {
        // Last guard behind the screen's inline validation (RM-0036).
        if (validateRequiredText(name, Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN) != null) return
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("create_household", CreateHouseholdParams(name).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                // RM-0032: the Firebase token must list the new household.
                firebaseSession.refreshClaims()
                _state.value = HouseholdSetupUiState.Created(it.invite_code)
            }.onFailure {
                _state.value = HouseholdSetupUiState.Idle
                feedback.failure(it, R.string.feedback_create_household_failed, retry = { create(name) })
            }
        }
    }

    /** US-5: join_household RPC fails without creating a membership row for an invalid code. */
    fun join(inviteCode: String) {
        if (validateInviteCode(inviteCode) != null) return
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("join_household", JoinHouseholdParams(inviteCode).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                // RM-0032: the Firebase token must list the new household.
                firebaseSession.refreshClaims()
                _state.value = HouseholdSetupUiState.Joined
            }.onFailure {
                _state.value = HouseholdSetupUiState.Idle
                // A server rejection with no better cause most likely means a wrong code.
                feedback.failure(
                    it, R.string.feedback_join_household_failed,
                    retry = { join(inviteCode) },
                    unknownRes = R.string.error_invalid_invite_code
                )
            }
        }
    }
}
