package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.BillShareInput
import com.rentmate.app.network.BillsApi
import com.rentmate.app.network.CreateBillRequest
import com.rentmate.app.network.HouseholdMemberResponse
import com.rentmate.app.network.HouseholdsApi
import com.rentmate.app.network.SplitMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface AddBillUiState {
    data object Idle : AddBillUiState
    data object Loading : AddBillUiState
    data object Saved : AddBillUiState
    data class Error(val message: String) : AddBillUiState
}

@HiltViewModel
class AddBillViewModel @Inject constructor(
    private val billsApi: BillsApi,
    private val householdsApi: HouseholdsApi,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _members = MutableStateFlow<List<HouseholdMemberResponse>>(emptyList())
    val members: StateFlow<List<HouseholdMemberResponse>> = _members

    private val _state = MutableStateFlow<AddBillUiState>(AddBillUiState.Idle)
    val state: StateFlow<AddBillUiState> = _state

    init {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching { householdsApi.getById(householdId) }
                .onSuccess { _members.value = it.members }
        }
    }

    /** US-6: equal split across every household member; amount must be greater than 0. */
    fun createEqualSplit(title: String, amount: Double, dueDate: LocalDate) {
        if (amount <= 0) {
            _state.value = AddBillUiState.Error("Amount must be greater than 0.")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = AddBillUiState.Loading
            runCatching {
                billsApi.create(
                    householdId,
                    CreateBillRequest(
                        title = title,
                        amount = amount,
                        dueDate = dueDate.toIsoMidnight(),
                        splitMethod = SplitMethod.Equal,
                        shares = _members.value.map { BillShareInput(it.userId) }
                    )
                )
            }.onSuccess { _state.value = AddBillUiState.Saved }
                .onFailure { _state.value = AddBillUiState.Error(it.message ?: "Could not create bill") }
        }
    }
}

private fun LocalDate.toIsoMidnight(): String = "${this}T00:00:00Z"
