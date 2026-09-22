package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.BillResponse
import com.rentmate.app.network.BillsApi
import com.rentmate.app.network.ConfirmSettlementRequest
import com.rentmate.app.network.PayBillShareRequest
import com.rentmate.app.network.SettleUpResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BillsUiState(
    val bills: List<BillResponse> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val settleUp: SettleUpResponse? = null,
    val settleUpLoading: Boolean = false
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billsApi: BillsApi,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow(BillsUiState())
    val state: StateFlow<BillsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { billsApi.list(householdId) }
                .onSuccess { _state.value = _state.value.copy(loading = false, bills = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun payShare(billId: String, shareId: String) {
        viewModelScope.launch {
            runCatching {
                billsApi.payShare(billId, shareId, PayBillShareRequest(Instant.now().toString()))
            }.onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** US-11: the smallest set of payments that would settle the household right now. */
    fun loadSettleUp() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(settleUpLoading = true)
            runCatching { billsApi.settleUp(householdId) }
                .onSuccess { _state.value = _state.value.copy(settleUpLoading = false, settleUp = it) }
                .onFailure { _state.value = _state.value.copy(settleUpLoading = false, error = it.message) }
        }
    }

    fun confirmSettlement(toUserId: String, amount: Double) {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching { billsApi.confirmSettlement(householdId, ConfirmSettlementRequest(toUserId, amount)) }
                .onSuccess {
                    _state.value = _state.value.copy(settleUp = null)
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismissSettleUp() {
        _state.value = _state.value.copy(settleUp = null)
    }
}
