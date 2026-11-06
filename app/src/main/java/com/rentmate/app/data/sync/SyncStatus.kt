package com.rentmate.app.data.sync

import android.util.Log
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.SyncRejectionDao
import com.rentmate.app.data.local.SyncRejectionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncStatus"

/** RM-0018: where the outbox upload stands right now. */
sealed interface SyncPhase {
    /** No run in progress, and the last one (if any) finished. */
    data object Idle : SyncPhase

    /** The worker is uploading. */
    data object Syncing : SyncPhase

    /** The last run stopped early and will retry. [lastError] is the reason, for logs. */
    data class Failed(val lastError: String?) : SyncPhase
}

/**
 * RM-0018: everything RM-0019 needs to show sync state. [pendingCount] comes
 * from the outbox, [rejected] lists changes the server refused for good.
 */
data class SyncStatus(
    val phase: SyncPhase,
    /** End of the last run that emptied the queue, epoch millis, or null if none yet in this process. */
    val lastSyncedAt: Long?,
    val pendingCount: Int,
    val rejected: List<SyncRejectionEntity>
)

/** RM-0019: where the UI reads sync state from. An interface so ViewModel tests can fake it. */
interface SyncStatusSource {
    /** Phase, last sync time, pending count and rejections, updated live. */
    val status: Flow<SyncStatus>
}

/**
 * RM-0018: holds the worker's run state and combines it with the outbox count
 * and the rejection log into [status]. The run state is in memory only, so it
 * starts as Idle after a cold start.
 */
@Singleton
class SyncStatusTracker @Inject constructor(
    outbox: OutboxQueue,
    rejectionDao: SyncRejectionDao
) : SyncStatusSource {
    private data class RunState(val phase: SyncPhase = SyncPhase.Idle, val lastSyncedAt: Long? = null)

    private val run = MutableStateFlow(RunState())

    override val status: Flow<SyncStatus> = combine(run, outbox.pendingCount, rejectionDao.observeAll()) { r, count, rejected ->
        SyncStatus(r.phase, r.lastSyncedAt, count, rejected)
    }

    /** The phase right now. */
    val phase: SyncPhase get() = run.value.phase

    /** End of the last run that emptied the queue, or null. */
    val lastSyncedAt: Long? get() = run.value.lastSyncedAt

    /** Called when a run starts. */
    fun started() = run.update { it.copy(phase = SyncPhase.Syncing) }

    /** Called when a run emptied the queue at [at], epoch millis. */
    fun succeeded(at: Long) = run.update { it.copy(phase = SyncPhase.Idle, lastSyncedAt = at) }

    /** Called when a run stopped early and will retry. */
    fun failed(error: String?) {
        Log.d(TAG, "sync stopped: $error")
        run.update { it.copy(phase = SyncPhase.Failed(error)) }
    }
}
