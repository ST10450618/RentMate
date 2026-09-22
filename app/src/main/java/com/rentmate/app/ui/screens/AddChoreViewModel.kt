package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.ChoreRecurrence
import com.rentmate.app.network.ChoresApi
import com.rentmate.app.network.CreateChoreRequest
import com.rentmate.app.network.HouseholdMemberResponse
import com.rentmate.app.network.HouseholdsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddChoreUiState {
    data object Idle : AddChoreUiState
    data object Loading : AddChoreUiState
    data object Saved : AddChoreUiState
    data class Error(val message: String) : AddChoreUiState
}

@HiltViewModel
class AddChoreViewModel @Inject constructor(
    private val choresApi: ChoresApi,
    private val householdsApi: HouseholdsApi,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _members = MutableStateFlow<List<HouseholdMemberResponse>>(emptyList())
    val members: StateFlow<List<HouseholdMemberResponse>> = _members

    private val _state = MutableStateFlow<AddChoreUiState>(AddChoreUiState.Idle)
    val state: StateFlow<AddChoreUiState> = _state

    init {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching { householdsApi.getById(householdId) }
                .onSuccess { _members.value = it.members }
        }
    }

    /** US-9/US-12: rotation order is every household member, in the order they're listed. */
    fun create(title: String, recurrence: ChoreRecurrence, pointValue: Int) {
        if (title.isBlank()) {
            _state.value = AddChoreUiState.Error("Title can't be blank.")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = AddChoreUiState.Loading
            runCatching {
                choresApi.create(
                    householdId,
                    CreateChoreRequest(
                        title = title,
                        recurrence = recurrence,
                        pointValue = pointValue,
                        rotationOrder = _members.value.map { it.userId }
                    )
                )
            }.onSuccess { _state.value = AddChoreUiState.Saved }
                .onFailure { _state.value = AddChoreUiState.Error(it.message ?: "Could not create chore") }
        }
    }
}
