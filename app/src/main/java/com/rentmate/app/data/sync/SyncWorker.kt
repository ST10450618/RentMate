package com.rentmate.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "SyncWorker"

/**
 * RM-0018: uploads the offline outbox. Scheduled by [WorkManagerSyncScheduler]
 * with a network constraint. All the logic is in [OutboxReplayer]; this class
 * only maps its result to a WorkManager result.
 *
 * Based on: Android Developers, Threading in CoroutineWorker,
 * https://developer.android.com/develop/background-work/background-tasks/persistent/threading/coroutineworker (accessed 2026-11-06)
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val replayer: OutboxReplayer
) : CoroutineWorker(context, params) {

    /** Runs one replay. [ReplayResult.RETRY] or a local failure asks WorkManager to try again later. */
    override suspend fun doWork(): Result {
        Log.d(TAG, "sync run $runAttemptCount")
        return try {
            when (replayer.run()) {
                ReplayResult.DONE -> Result.success().also { Log.i(TAG, "sync run $runAttemptCount: success") }
                ReplayResult.RETRY -> Result.retry().also { Log.i(TAG, "sync run $runAttemptCount: retry later") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sync run failed", e)
            Result.retry()
        }
    }
}
