package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper
import android.util.Log
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.attachments.THUMBNAIL_URL_EXPIRY
import com.rentmate.app.data.attachments.loadLinks
import kotlinx.coroutines.CancellationException

data class BillsUiState(
    val bills: List<BillWithSharesRow> = emptyList(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val settleUp: List<SettleUpRow>? = null,
    val settleUpLoading: Boolean = false,
    /** The signed-in user; only the creditor sees Mark paid / Confirm received (RM-0010). */
    val currentUserId: String? = null,
    /** RM-0029: thumbnails per bill ID, with short-lived signed URLs. */
    val photos: Map<String, List<PhotoLink>> = emptyMap()
)

@Serializable
private data class SettleUpParams(val household_id_in: String)

@Serializable
private data class MarkSharePaidParams(val share_id_in: String)

@Serializable
private data class ConfirmSettlementParams(val household_id_in: String, val from_user_id_in: String, val amount_in: Double)

private val BILL_WITH_SHARES_COLUMNS = Columns.raw("*, bill_shares(*, profiles(display_name))")

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val attachments: AttachmentRepository,
    private val activity: ActivityLogger
) : ViewModel() {
    private val _state = MutableStateFlow(BillsUiState(currentUserId = supabase.auth.currentUserOrNull()?.id))
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
                .onFailure { _state.value = _state.value.copy(loading = false, error = ErrorMapper.toMessage(it)) }
            loadPhotos()
        }
    }

    /** Photos are extra: if they fail to load, bills still show without thumbnails. */
    private suspend fun loadPhotos() {
        try {
            val links = attachments.loadLinks(THUMBNAIL_URL_EXPIRY)
            _state.value = _state.value.copy(
                photos = links.entries.filter { it.key is AttachmentParent.Bill }.associate { it.key.id to it.value }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BillsViewModel", "Could not load bill photos", e)
        }
    }

    fun payShare(shareId: String) {
        val bill = _state.value.bills.firstOrNull { b -> b.bill_shares.any { it.id == shareId } }
        val share = bill?.bill_shares?.firstOrNull { it.id == shareId }
        viewModelScope.launch {
            runCatching {
                // RM-0006: shares can't be updated directly any more (RLS has no
                // update policy); the RPC only lets the bill's issuer (the creditor) mark a share paid.
                supabase.postgrest.rpc("mark_share_paid", MarkSharePaidParams(shareId).toRpcParams())
            }.onSuccess {
                if (bill != null && share != null) {
                    activity.sharePaid(bill.title, share.amount_owed, share.profiles?.display_name, bill.id)
                }
                refresh()
            }.onFailure { _state.value = _state.value.copy(error = ErrorMapper.toMessage(it)) }
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
                .onFailure { _state.value = _state.value.copy(settleUpLoading = false, error = ErrorMapper.toMessage(it)) }
        }
    }

    /** RM-0010: only the creditor confirms; [fromUserId] is the person who paid the caller. */
    fun confirmSettlement(fromUserId: String, amount: Double) {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching {
                supabase.postgrest.rpc("confirm_settlement", ConfirmSettlementParams(householdId, fromUserId, amount).toRpcParams())
            }.onSuccess {
                val fromName = _state.value.settleUp?.firstOrNull { it.from_user_id == fromUserId }?.from_display_name
                activity.settlementConfirmed(fromName ?: "a housemate", amount)
                _state.value = _state.value.copy(settleUp = null)
                refresh()
            }.onFailure { _state.value = _state.value.copy(error = ErrorMapper.toMessage(it)) }
        }
    }

    fun dismissSettleUp() {
        _state.value = _state.value.copy(settleUp = null)
    }
}
