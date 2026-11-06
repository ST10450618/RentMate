package com.rentmate.app.ui.screens

import android.util.Log
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter

private const val TAG = "HouseholdSetupVM"

sealed interface HouseholdSetupUiState {
    data object Idle : HouseholdSetupUiState
    data object Loading : HouseholdSetupUiState
    /** Created shows the invite code (S2's "generate code with share action") before continuing. */
    data class Created(val inviteCode: String) : HouseholdSetupUiState
    data object Joined : HouseholdSetupUiState
}

/** The step the household setup screen shows. The screen asks one thing at a time (RM-0061). */
enum class SetupStep { CHOOSE, JOIN, CREATE }

/**
 * Cleans what the user types or pastes into the invite code field. It keeps
 * only the letters A to Z and the digits, makes them capitals and stops at the
 * code length. So "k7q 2mx" and "K7Q-2MX" from a chat message both become "K7Q2MX".
 */
fun cleanInviteCodeInput(raw: String): String =
    raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        .uppercase()
        .take(Limits.INVITE_CODE_LENGTH)

/** True when the Join button can be tapped: the code is complete and no request is running. */
fun canJoin(code: String, busy: Boolean): Boolean = !busy && validateInviteCode(code) == null

/** True when the Create button can be tapped: the name is valid and no request is running. */
fun canCreate(name: String, busy: Boolean): Boolean =
    !busy && validateRequiredText(name, Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN) == null

/**
 * The first word of a full name, for the "Welcome, Nomsa" eyebrow.
 * Returns null when there is no name, or when the value is an email address.
 */
fun firstNameOf(fullName: String?): String? =
    fullName?.trim()
        ?.split(' ', '\t')
        ?.firstOrNull { it.isNotEmpty() }
        ?.takeUnless { '@' in it }

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

    /** The signed-in user's first name from their Google profile, or null when it is not known. */
    val firstName: String? by lazy {
        val user = runCatching { supabase.auth.currentUserOrNull() }.getOrNull()
        val fullName = listOf("full_name", "name").firstNotNullOfOrNull { key ->
            runCatching { user?.userMetadata?.get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
        }
        firstNameOf(fullName)
    }

    /** US-5: create_household RPC generates the 6-character invite code. */
    fun create(name: String) {
        // Last guard behind the screen's inline validation (RM-0036).
        if (validateRequiredText(name, Limits.HOUSEHOLD_NAME_MAX, Limits.HOUSEHOLD_NAME_MIN) != null) return
        Log.d(TAG, "create household")
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("create_household", CreateHouseholdParams(name).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                // RM-0032: the Firebase token must list the new household.
                firebaseSession.refreshClaims()
                Log.d(TAG, "household created")
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
        Log.d(TAG, "join household")
        _state.value = HouseholdSetupUiState.Loading
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("join_household", JoinHouseholdParams(inviteCode).toRpcParams())
                    .decodeAs<HouseholdRow>()
            }.onSuccess {
                currentHousehold.set(it.id)
                // RM-0032: the Firebase token must list the new household.
                firebaseSession.refreshClaims()
                Log.d(TAG, "household joined")
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
