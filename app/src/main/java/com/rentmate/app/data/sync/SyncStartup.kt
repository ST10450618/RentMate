package com.rentmate.app.data.sync

import android.util.Log
import com.rentmate.app.data.local.OutboxDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncStartup"

/**
 * RM-0018: requests a sync when the app starts with changes still queued, and
 * again whenever a user becomes signed in. The second case covers a sync that
 * stopped on an expired session: signing in again sends the queue.
 */
@Singleton
class SyncStartup @Inject constructor(
    private val supabase: SupabaseClient,
    private val outboxDao: OutboxDao,
    private val scheduler: SyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Call from MainActivity.onCreate. Later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "start")
        scope.launch {
            requestIfPending("app start")
            try {
                supabase.auth.sessionStatus
                    .map { it is SessionStatus.Authenticated }
                    .distinctUntilChanged()
                    .collect { signedIn -> if (signedIn) requestIfPending("signed in") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not follow the Supabase session", e)
            }
        }
    }

    /** Requests a sync only when the outbox holds entries; [why] goes in the log line. */
    private suspend fun requestIfPending(why: String) {
        try {
            val pending = outboxDao.count()
            Log.d(TAG, "$why: $pending change(s) queued")
            if (pending > 0) scheduler.requestSync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "could not read the outbox", e)
        }
    }
}
