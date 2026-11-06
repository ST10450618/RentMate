package com.rentmate.app.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.HouseholdMemberRow
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.SettleUpSource
import com.rentmate.app.data.localSettleUp
import com.rentmate.app.data.sync.SyncPhase
import com.rentmate.app.data.sync.SyncStatus
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SettleUpCoordinator"

/** How often a provisional plan asks the server again while the dialog is open. */
internal const val SETTLE_UP_RETRY_MS = 20_000L

/**
 * RM-0031: what the Settle-up dialog shows.
 *
 * [provisional] is true when [rows] were computed on this phone from the Room
 * ledger. The server has not checked them, so the screen labels them and
 * blocks Confirm received.
 */
data class SettleUpState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val rows: List<SettleUpRow> = emptyList(),
    val provisional: Boolean = false
)

/**
 * RM-0031: loads the settle-up plan and decides whether it is the server's
 * plan or a local preview.
 *
 * - If a bill on this phone still waits in the outbox, the server does not
 *   know it yet, so a local preview is shown without asking the server.
 * - Otherwise the server's settle_up is used. If that call fails with no
 *   connection, a local preview is shown instead.
 * - While a preview is shown it follows the Room ledger, and the server is
 *   asked again when a sync finishes ([syncStatus]), when the bills refresh
 *   ([onBillsRefreshed]) and every [retryMs]. The first server answer replaces
 *   the preview; the server wins if they differ.
 *
 * [onError] reports a server failure that is not a lost connection, when no
 * preview is on screen. It is the same snackbar the screen showed before.
 */
class SettleUpCoordinator(
    private val scope: CoroutineScope,
    private val source: SettleUpSource,
    private val householdId: suspend () -> String?,
    private val bills: Flow<List<BillWithSharesRow>>,
    private val members: Flow<List<HouseholdMemberRow>>,
    private val syncStatus: Flow<SyncStatus>,
    private val onError: (Throwable) -> Unit,
    private val retryMs: Long = SETTLE_UP_RETRY_MS
) {
    private val _state = MutableStateFlow(SettleUpState())

    /** The dialog's state. */
    val state: StateFlow<SettleUpState> = _state

    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var watchJob: Job? = null

    /** Opens the dialog and loads the plan. */
    fun open() {
        Log.d(TAG, "open")
        _state.update { it.copy(visible = true, loading = !it.provisional) }
        load()
    }

    /** Closes the dialog and stops watching for a sync. */
    fun dismiss() {
        Log.d(TAG, "dismiss")
        loadJob?.cancel()
        stopPreview()
        _state.value = SettleUpState()
    }

    /** The bills were refreshed from the server, so a preview can be replaced now. */
    fun onBillsRefreshed() {
        if (_state.value.visible && _state.value.provisional) {
            Log.d(TAG, "bills refreshed, asking the server again")
            load()
        }
    }

    /**
     * Why Confirm received cannot run now, or null if it can. A preview is not
     * the server's plan, so confirming from it could record the wrong amount.
     */
    @StringRes
    fun confirmBlockedReason(): Int? =
        if (_state.value.provisional) R.string.settle_up_confirm_needs_sync else null

    private fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val id = householdId()
            if (id == null) {
                Log.w(TAG, "no household selected")
                dismiss()
                return@launch
            }
            if (bills.first().any { it.pending }) {
                Log.d(TAG, "bills wait in the outbox, showing a local preview")
                showPreview()
                return@launch
            }
            try {
                val rows = source.settleUp(id)
                Log.d(TAG, "server plan: ${rows.size} payments")
                stopPreview()
                _state.value = SettleUpState(visible = true, rows = rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when {
                    ErrorMapper.classify(e) == AppError.NO_CONNECTION -> {
                        Log.d(TAG, "no connection, showing a local preview (${e.javaClass.simpleName})")
                        showPreview()
                    }
                    _state.value.provisional -> Log.w(TAG, "server plan failed, keeping the preview", e)
                    else -> {
                        dismiss()
                        onError(e)
                    }
                }
            }
        }
    }

    private fun showPreview() {
        if (previewJob?.isActive != true) {
            previewJob = scope.launch {
                combine(bills, members) { b, m -> localSettleUp(b, m) }.collect { rows ->
                    _state.value = SettleUpState(visible = true, rows = rows, provisional = true)
                }
            }
        }
        if (watchJob?.isActive != true) watchJob = scope.launch { watchForSync() }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        watchJob?.cancel()
        watchJob = null
    }

    /** Asks the server again after a sync, and every [retryMs] in case the network came back without one. */
    private suspend fun watchForSync() = coroutineScope {
        launch {
            while (true) {
                delay(retryMs)
                Log.d(TAG, "retrying the server plan")
                load()
            }
        }
        var lastSyncedAt: Long? = null
        var sawPending = false
        var first = true
        syncStatus.collect { s ->
            if (first) {
                first = false
                lastSyncedAt = s.lastSyncedAt
                sawPending = s.pendingCount > 0
                return@collect
            }
            if (s.pendingCount > 0) sawPending = true
            val drained = sawPending && s.phase == SyncPhase.Idle && s.pendingCount == 0
            if (s.lastSyncedAt != lastSyncedAt || drained) {
                Log.d(TAG, "sync finished, asking the server again")
                lastSyncedAt = s.lastSyncedAt
                sawPending = false
                load()
            }
        }
    }
}
