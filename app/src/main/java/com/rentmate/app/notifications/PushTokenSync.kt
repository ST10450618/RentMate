package com.rentmate.app.notifications

import android.util.Log
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

private const val TAG = "PushTokenSync"
private const val SIGNED_IN = "signed-in"

/**
 * Saves the FCM token each time a user becomes signed in (RM-0021): after
 * Google sign-in and when a saved session loads at app start.
 *
 * It follows the Supabase session in the same way as FirebaseSession (RM-0032),
 * so AuthRepository does not change. A session refresh for the same user does
 * not save the token again.
 */
@Singleton
class PushTokenSync @Inject constructor(
    private val supabase: SupabaseClient,
    private val registrar: DeviceTokenRegistrar
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Call from MainActivity.onCreate. Later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "start: following the Supabase session")
        scope.launch {
            try {
                supabase.auth.sessionStatus
                    // A session restored at start-up can lack its user until it is
                    // fetched, so "signed in" stands in for the id until then.
                    .map { (it as? SessionStatus.Authenticated)?.let { auth -> auth.session.user?.id ?: SIGNED_IN } }
                    .distinctUntilChanged()
                    .collect { userId ->
                        Log.d(TAG, "session changed: signedIn=${userId != null}")
                        if (userId != null) registrar.syncCurrent()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not follow the Supabase session", e)
            }
        }
    }
}
