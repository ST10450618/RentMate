package com.rentmate.app.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.local.SyncRejectionDao
import com.rentmate.app.data.local.SyncRejectionEntity
import com.rentmate.app.data.sync.ConnectivityMonitor
import com.rentmate.app.data.sync.DropReason
import com.rentmate.app.data.sync.SyncPhase
import com.rentmate.app.data.sync.SyncScheduler
import com.rentmate.app.data.sync.SyncStatusSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SyncBanner"

/** RM-0019: what the app-wide sync banner shows. */
sealed interface BannerState {
    /** Online and nothing waiting. No banner. */
    data object Hidden : BannerState

    /** No internet. [pending] changes wait in the outbox. */
    data class Offline(val pending: Int) : BannerState

    /** Online with [pending] changes on their way up. */
    data class Syncing(val pending: Int) : BannerState

    /** Online, the last run failed and [pending] changes still wait. The banner offers Retry. */
    data class Failed(val pending: Int) : BannerState

    /** The queue just emptied. Shown for [SyncBannerViewModel.SYNCED_MS], then [Hidden]. */
    data object Synced : BannerState
}

/** RM-0019: the user-facing reason for a rejected change. */
enum class RejectionKind {
    /** The server said this user may not make the change. */
    NOT_ALLOWED,

    /** The row was deleted on the server. */
    GONE,

    /** Any other permanent failure. */
    NOT_SAVED
}

/**
 * RM-0019: a notice about changes the server refused for good. It describes the
 * oldest rejected change by [kind]; [others] counts the other rejected changes.
 * Dismissing it deletes [ids], so each rejection is shown until seen once.
 */
data class RejectionNotice(val kind: RejectionKind, val others: Int, val ids: List<Long>)

/** RM-0019: the banner plus an optional rejection notice. */
data class SyncBannerUiState(
    val banner: BannerState = BannerState.Hidden,
    val notice: RejectionNotice? = null
)

/**
 * RM-0019: combines the network state from [ConnectivityMonitor] with the
 * outbox state from [SyncStatusSource] into one [SyncBannerUiState] for the
 * app scaffold. The rules are in [bannerFor] and [noticeFor].
 *
 * When the queue empties while online, the banner shows [BannerState.Synced]
 * for [SYNCED_MS] and then hides. It skips that step when the queue emptied
 * because changes were rejected, since the notice explains what happened.
 */
@HiltViewModel
class SyncBannerViewModel @Inject constructor(
    connectivity: ConnectivityMonitor,
    syncStatus: SyncStatusSource,
    private val rejectionDao: SyncRejectionDao,
    private val scheduler: SyncScheduler
) : ViewModel() {

    private data class Inputs(val online: Boolean, val phase: SyncPhase, val pending: Int, val retrying: Boolean, val rejected: Boolean)

    /** True from a Retry tap until the worker picks it up, so the banner shows progress at once. */
    private val retrying = MutableStateFlow(false)
    private var retryTimeout: Job? = null

    private var hadPending = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val banner = combine(connectivity.isOnline, syncStatus.status, retrying) { online, status, retry ->
        Inputs(online, status.phase, status.pendingCount, retry, status.rejected.isNotEmpty())
    }
        .onEach { if (it.retrying && it.phase !is SyncPhase.Failed) retrying.value = false }
        .distinctUntilChanged()
        .transformLatest { inputs ->
            val next = bannerFor(inputs.online, inputs.phase, inputs.pending, inputs.retrying)
            val justEmptied = next == BannerState.Hidden && hadPending
            hadPending = inputs.pending > 0
            if (justEmptied && !inputs.rejected) {
                Log.d(TAG, "all changes synced")
                emit(BannerState.Synced)
                delay(SYNCED_MS)
                emit(BannerState.Hidden)
            } else {
                emit(next)
            }
        }
        .distinctUntilChanged()

    private val notice = syncStatus.status.map { noticeFor(it.rejected) }.distinctUntilChanged()

    /** The banner and notice, for the app scaffold. */
    val state: StateFlow<SyncBannerUiState> = combine(banner, notice, ::SyncBannerUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncBannerUiState())

    /** The banner's Retry button: asks for an upload now. */
    fun retry() {
        Log.d(TAG, "retry tapped")
        retrying.value = true
        scheduler.syncNow()
        retryTimeout?.cancel()
        retryTimeout = viewModelScope.launch {
            // If the worker never starts (no network after all), offer Retry again.
            delay(RETRY_FEEDBACK_MS)
            retrying.value = false
        }
    }

    /** The user saw [notice]: forget its rejections so they do not show again. */
    fun dismiss(notice: RejectionNotice) {
        viewModelScope.launch {
            try {
                rejectionDao.deleteByIds(notice.ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "could not clear seen rejections", e)
            }
        }
    }

    companion object {
        /** How long "All changes synced" stays before the banner hides. */
        const val SYNCED_MS = 3_000L

        /** How long a Retry tap shows "Syncing" before Retry is offered again, if the worker did not start. */
        const val RETRY_FEEDBACK_MS = 15_000L

        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The banner rules:
         * - offline: [BannerState.Offline], with any pending count;
         * - online, nothing pending: [BannerState.Hidden], even while a run
         *   finishes, so an empty run does not flash the banner;
         * - online, pending, last run failed: [BannerState.Failed], unless the
         *   user just tapped Retry;
         * - online, pending, otherwise: [BannerState.Syncing].
         */
        fun bannerFor(online: Boolean, phase: SyncPhase, pending: Int, retrying: Boolean = false): BannerState = when {
            !online -> BannerState.Offline(pending)
            pending <= 0 -> BannerState.Hidden
            phase is SyncPhase.Failed && !retrying -> BannerState.Failed(pending)
            else -> BannerState.Syncing(pending)
        }

        /**
         * The notice for [rejected] (newest first), or null when it is empty.
         * Rows that were dropped only because an earlier change to the same row
         * was dropped do not count as separate changes.
         */
        fun noticeFor(rejected: List<SyncRejectionEntity>): RejectionNotice? {
            if (rejected.isEmpty()) return null
            val primary = rejected.filterNot { it.dependant }.ifEmpty { rejected }
            return RejectionNotice(
                kind = kindOf(primary.last().reason),
                others = primary.size - 1,
                ids = rejected.map { it.id }
            )
        }

        /** Maps a stored [DropReason] name to what the user is told. */
        fun kindOf(reason: String): RejectionKind = when (reason) {
            DropReason.NOT_ALLOWED.name -> RejectionKind.NOT_ALLOWED
            DropReason.TARGET_MISSING.name -> RejectionKind.GONE
            else -> RejectionKind.NOT_SAVED
        }
    }
}
