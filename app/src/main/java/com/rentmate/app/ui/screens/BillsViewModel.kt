package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.BillsRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.SettleUpSource
import com.rentmate.app.data.sync.SyncStatusTracker
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper
import android.util.Log
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.ui.feedback.UndoQueue
import com.rentmate.app.ui.feedback.UndoableAction
import com.rentmate.app.ui.feedback.UserMessage
import com.rentmate.app.ui.components.formatRandShort
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
    /**
     * RM-0031: true when [settleUp] was computed on this phone (offline, or a
     * bill still waits to sync). Money labels it Preview and blocks mark received.
     */
    val settleUpProvisional: Boolean = false,
    /** The signed-in user; only the creditor sees Mark paid / Confirm received (RM-0010). */
    val currentUserId: String? = null,
    /** RM-0029: thumbnails per bill ID, with short-lived signed URLs. */
    val photos: Map<String, List<PhotoLink>> = emptyMap(),
    /**
     * RM-0056: shares marked paid that are still inside their Undo window. The
     * screen shows them as paid; the server call runs when the window closes.
     */
    val pendingPaidShares: Set<String> = emptySet(),
    /** RM-0056: the selected filter chip on Money. */
    val filter: BillFilter = BillFilter.Open,
    /**
     * RM-0056: payers whose payment the user marked received, still inside the
     * Undo window. Their settle-up row is hidden until the window closes.
     */
    val pendingReceived: Set<String> = emptySet()
) {
    /** True when [shareId] is paid on the server or waiting to be marked paid. */
    fun isShownPaid(shareId: String, isPaid: Boolean): Boolean = isPaid || shareId in pendingPaidShares
}

@Serializable
private data class StopRepeatParams(val bill_id_in: String)

@Serializable
private data class MarkSharePaidParams(val share_id_in: String)

@Serializable
private data class ConfirmSettlementParams(val household_id_in: String, val from_user_id_in: String, val amount_in: Double)

private const val TAG = "BillsViewModel"

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val attachments: AttachmentRepository,
    private val activity: ActivityLogger,
    private val billsRepository: BillsRepository,
    private val feedback: FeedbackCenter,
    private val undoQueue: UndoQueue,
    settleUpSource: SettleUpSource,
    syncStatus: SyncStatusTracker
) : ViewModel() {
    private val _state = MutableStateFlow(BillsUiState(currentUserId = supabase.auth.currentUserOrNull()?.id))
    val state: StateFlow<BillsUiState> = _state

    /** RM-0031: server plan when online, a labelled local preview when not. */
    private val settleUp: SettleUpCoordinator = SettleUpCoordinator(
        scope = viewModelScope,
        source = settleUpSource,
        householdId = { currentHousehold.id.first() },
        bills = billsRepository.bills,
        members = billsRepository.members,
        syncStatus = syncStatus.status,
        onError = { feedback.failure(it, R.string.feedback_settle_up_failed, retry = ::loadSettleUp) }
    )

    init {
        viewModelScope.launch {
            settleUp.state.collect { s ->
                _state.update {
                    it.copy(
                        settleUp = if (s.visible && !s.loading) s.rows else it.settleUp.takeIf { _ -> s.visible },
                        settleUpLoading = s.visible && s.loading,
                        settleUpProvisional = s.provisional
                    )
                }
            }
        }
        // RM-0016: the list always comes from the Room cache, so it survives a failed refresh.
        viewModelScope.launch {
            billsRepository.bills
                .catch { Log.e(TAG, "could not read cached bills", it) }
                .collect { bills -> _state.update { it.copy(bills = bills) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            Log.d(TAG, "refresh household=$householdId")
            runCatching { billsRepository.refresh() }
                .onSuccess {
                    _state.update { it.copy(loading = false) }
                    settleUp.onBillsRefreshed()
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = ErrorMapper.toMessage(e)) } }
            // RM-0056: settle-up sits on the Money screen, so it loads with the bills.
            // RM-0031: offline the coordinator shows a local preview instead.
            loadSettleUp()
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
            Log.w(TAG, "Could not load bill photos", e)
        }
    }

    /**
     * Marks a share paid. RM-0056: the share shows as paid at once and a
     * snackbar offers Undo for 5 s. The mark_share_paid call runs only after
     * that, so Undo needs no server call. See [UndoQueue].
     */
    fun payShare(shareId: String) {
        val bill = _state.value.bills.firstOrNull { b -> b.bill_shares.any { it.id == shareId } }
        val share = bill?.bill_shares?.firstOrNull { it.id == shareId }
        Log.d(TAG, "payShare $shareId, waiting for Undo")
        undoQueue.submit(
            UserMessage(R.string.undo_marked_paid),
            UndoableAction(
                key = "pay:$shareId",
                hide = { _state.update { it.copy(pendingPaidShares = it.pendingPaidShares + shareId) } },
                restore = { _state.update { it.copy(pendingPaidShares = it.pendingPaidShares - shareId) } },
                commit = {
                    // RM-0006: shares can't be updated directly any more (RLS has no
                    // update policy); the RPC only lets the bill's issuer (the creditor) mark a share paid.
                    supabase.postgrest.rpc("mark_share_paid", MarkSharePaidParams(shareId).toRpcParams())
                    if (bill != null && share != null) {
                        activity.sharePaid(bill.title, share.amount_owed, share.profiles?.display_name, bill.id)
                    }
                    refresh()
                },
                onCommitFailed = {
                    feedback.failure(
                        it, R.string.feedback_mark_paid_failed,
                        retry = { payShare(shareId) },
                        notAllowedRes = R.string.feedback_mark_paid_not_allowed
                    )
                }
            )
        )
    }

    /** RM-0049: only the issuer can stop a bill repeating; the server checks it too. */
    fun stopRepeat(billId: String) {
        viewModelScope.launch {
            runCatching {
                supabase.postgrest.rpc("stop_bill_repeat", StopRepeatParams(billId).toRpcParams())
            }.onSuccess { refresh() }
                .onFailure { feedback.failure(it, R.string.feedback_stop_repeat_failed, retry = { stopRepeat(billId) }) }
        }
    }

    /** RM-0056: selects a filter chip on Money. */
    fun setFilter(filter: BillFilter) {
        Log.d(TAG, "filter -> $filter")
        _state.update { it.copy(filter = filter) }
    }

    /**
     * US-11: the smallest set of payments that would settle the household right now.
     * RM-0031: offline this is a local preview, replaced by the server plan after a sync.
     */
    fun loadSettleUp() {
        settleUp.open()
    }

    /**
     * RM-0010: only the creditor confirms; [fromUserId] is the person who paid the caller.
     *
     * RM-0056: the row disappears at once and a snackbar ("Marked R 300 as
     * received") offers Undo for 5 s. confirm_settlement runs only after that,
     * so Undo needs no server call. See [UndoQueue].
     */
    fun confirmSettlement(fromUserId: String, amount: Double) {
        // RM-0031: a preview is not the server's plan, so do not confirm from it.
        settleUp.confirmBlockedReason()?.let { reason ->
            Log.d(TAG, "confirm blocked: the plan is a local preview")
            feedback.show(reason)
            return
        }
        val fromName = _state.value.settleUp?.firstOrNull { it.from_user_id == fromUserId }?.from_display_name
        Log.d(TAG, "confirmSettlement from=$fromUserId, waiting for Undo")
        undoQueue.submit(
            UserMessage(R.string.undo_marked_received, formatArgs = listOf(formatRandShort(amount))),
            UndoableAction(
                key = "received:$fromUserId",
                hide = { _state.update { it.copy(pendingReceived = it.pendingReceived + fromUserId) } },
                restore = { _state.update { it.copy(pendingReceived = it.pendingReceived - fromUserId) } },
                commit = {
                    val householdId = currentHousehold.id.first() ?: error("no household")
                    supabase.postgrest.rpc(
                        "confirm_settlement",
                        ConfirmSettlementParams(householdId, fromUserId, amount).toRpcParams()
                    )
                    activity.settlementConfirmed(fromName ?: "a housemate", amount)
                    _state.update { it.copy(pendingReceived = it.pendingReceived - fromUserId) }
                    refresh()
                },
                onCommitFailed = {
                    feedback.failure(
                        it, R.string.feedback_confirm_settlement_failed,
                        retry = { confirmSettlement(fromUserId, amount) }
                    )
                }
            )
        )
    }

}
