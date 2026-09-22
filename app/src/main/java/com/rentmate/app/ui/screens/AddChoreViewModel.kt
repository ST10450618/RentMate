package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface AddChoreUiState {
    data object Idle : AddChoreUiState
    data object Loading : AddChoreUiState
    data object Saved : AddChoreUiState
    data class Error(val message: String) : AddChoreUiState
}

@Serializable
private data class MemberIdRow(val user_id: String)

@Serializable
private data class ChoreInsert(
    val household_id: String,
    val title: String,
    val recurrence: String,
    val point_value: Int,
    val rotation_order: List<String>
)

@HiltViewModel
class AddChoreViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _members = MutableStateFlow<List<String>>(emptyList())
    val members: StateFlow<List<String>> = _members

    private val _state = MutableStateFlow<AddChoreUiState>(AddChoreUiState.Idle)
    val state: StateFlow<AddChoreUiState> = _state

    init {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching {
                supabase.from("household_members")
                    .select { filter { eq("household_id", householdId) } }
                    .decodeList<MemberIdRow>()
            }.onSuccess { _members.value = it.map { m -> m.user_id } }
        }
    }

    /** US-9/US-12: rotation order is every household member, in the order they're listed. */
    fun create(title: String, recurrence: String, pointValue: Int) {
        if (title.isBlank()) {
            _state.value = AddChoreUiState.Error("Title can't be blank.")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = AddChoreUiState.Loading
            runCatching {
                supabase.from("chores").insert(
                    ChoreInsert(householdId, title, recurrence, pointValue, _members.value)
                )
            }.onSuccess { _state.value = AddChoreUiState.Saved }
                .onFailure { _state.value = AddChoreUiState.Error(it.message ?: "Could not create chore") }
        }
    }
}
