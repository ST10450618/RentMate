package com.rentmate.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncScheduler"

/** RM-0018: asks for the outbox to be uploaded. An interface so tests can count requests. */
interface SyncScheduler {
    /** Schedules an upload of the outbox for as soon as there is a network. Safe to call often. */
    fun requestSync()

    /** Does nothing. For tests and code paths without WorkManager. */
    object None : SyncScheduler {
        /** Ignores the request. */
        override fun requestSync() = Unit
    }
}

/**
 * RM-0018: schedules [SyncWorker] as unique one-time work [WORK_NAME] that waits
 * for a connected network.
 *
 * [ExistingWorkPolicy.KEEP]: if a sync is already waiting or running, a new
 * request is ignored. That is safe because the worker reads the queue again
 * after each entry, so a change queued while it runs is still sent. The only
 * gap is a change queued in the moment after the worker found the queue empty
 * and before it finished; the next request or next sign-in picks it up.
 *
 * When the network comes back, WorkManager starts waiting work within seconds,
 * which gives the "within a minute" target. A retry backs off exponentially
 * from [BACKOFF_SECONDS].
 *
 * Based on: Android Developers, Define work requests,
 * https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work (accessed 2026-11-06)
 * Based on: Android Developers, Managing work,
 * https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work (accessed 2026-11-06)
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncScheduler {

    /** Enqueues [SyncWorker] as unique work; a sync already waiting or running is kept. */
    override fun requestSync() {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, buildRequest())
            Log.d(TAG, "sync requested")
        } catch (e: IllegalStateException) {
            // WorkManager not initialised (should not happen in the app). The entry stays queued.
            Log.e(TAG, "could not schedule sync", e)
        }
    }

    companion object {
        /** Unique work name of the outbox upload. */
        const val WORK_NAME = "outbox-sync"

        /** First retry delay. WorkManager's minimum is 10 seconds. */
        const val BACKOFF_SECONDS = 15L

        /** The work request: needs a connected network, retries with exponential backoff. */
        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .build()
    }
}
