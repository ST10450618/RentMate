package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject

data class BillsUiState(
    val bills: List<BillWithSharesRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val settleUp: List<SettleUpRow>? = null,
    val settleUpLoading: Boolean = false
)

@Serializable
private data class SettleUpParams(val household_id_in: String)

@Serializable
private data class ConfirmSettlementParams(val household_id_in: String, val to_user_id_in: String, val amount_in: Double)

private val BILL_WITH_SHARES_COLUMNS = Columns.raw("*, bill_shares(*, profiles(display_name))")

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
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
            runCatching {
                supabase.from("bills")
                    .select(columns = BILL_WITH_SHARES_COLUMNS) { filter { eq("household_id", householdId) } }
                    .decodeList<BillWithSharesRow>()
            }.onSuccess { _state.value = _state.value.copy(loading = false, bills = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun payShare(shareId: String) {
        viewModelScope.launch {
            runCatching {
                supabase.from("bill_shares").update({
                    set("is_paid", true)
                    set("paid_at", Instant.now().toString())
                }) {
                    filter { eq("id", shareId) }
                }
            }.onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** US-11: the smallest set of payments that would settle the household right now. */
    fun loadSettleUp() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(settleUpLoading = true)
            runCatching {
                supabase.postgrest.rpc("settle_up", SettleUpParams(householdId).toRpcParams()).decodeList<SettleUpRow>()
            }.onSuccess { _state.value = _state.value.copy(settleUpLoading = false, settleUp = it) }
                .onFailure { _state.value = _state.value.copy(settleUpLoading = false, error = it.message) }
        }
    }

    fun confirmSettlement(toUserId: String, amount: Double) {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching {
                supabase.postgrest.rpc("confirm_settlement", ConfirmSettlementParams(householdId, toUserId, amount).toRpcParams())
            }.onSuccess {
                _state.value = _state.value.copy(settleUp = null)
                refresh()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun dismissSettleUp() {
        _state.value = _state.value.copy(settleUp = null)
    }
}
