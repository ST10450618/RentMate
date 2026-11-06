package com.rentmate.app.notifications

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "DeviceTokenRegistrar"

/** Where the device's FCM token comes from. An interface so tests can fake Firebase. */
interface PushTokenSource {
    /** The current token, or null when Firebase is not set up in this build. May throw. */
    suspend fun currentToken(): String?
}

/** Where the token is saved. The app uses the device_tokens table (migration 0015). */
interface DeviceTokenStore {
    /** Saves the token for the signed-in user. Returns false when nobody is signed in. May throw. */
    suspend fun register(token: String): Boolean

    /** Removes the token from the signed-in user. May throw. */
    suspend fun unregister(token: String)
}

/**
 * Keeps the signed-in user's FCM token in Supabase (RM-0021), so the RM-0022
 * Edge Function knows which devices to notify.
 *
 * - After sign-in and at app start, [PushTokenSync] calls [syncCurrent].
 * - When FCM issues a new token, [RentMateMessagingService] calls [registerInBackground].
 * - Before sign-out, [unregisterCurrent] removes it, so the device stops
 *   getting this account's pushes.
 *
 * None of these calls throw. Sign-in and sign-out must work offline and in a
 * build without google-services.json, so a failure is only logged. The next
 * sign-in or app start tries again.
 */
class DeviceTokenRegistrar(
    private val source: PushTokenSource,
    private val store: DeviceTokenStore,
    private val scope: CoroutineScope
) {
    /** Saves the current token. Returns true when it was saved. */
    suspend fun syncCurrent(): Boolean {
        val token = safely("read token") { source.currentToken() } ?: return false
        return register(token)
    }

    /**
     * Runs [register] on the registrar's app-wide scope. The messaging service
     * uses this because Android can stop the service before the save finishes.
     */
    fun registerInBackground(token: String) {
        scope.launch { register(token) }
    }

    /** Saves [token] for the signed-in user. Returns true when it was saved. */
    suspend fun register(token: String): Boolean {
        if (token.isBlank()) return false
        val saved = safely("register token") { store.register(token) } == true
        Log.d(TAG, if (saved) "token saved" else "token not saved (signed out, offline or no Firebase)")
        return saved
    }

    /** Removes this device's token from the signed-in user. Call before signing out. */
    suspend fun unregisterCurrent() {
        val token = safely("read token") { source.currentToken() } ?: return
        safely("unregister token") { store.unregister(token) }
    }

    private suspend fun <T> safely(action: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not $action: ${e.message}")
        null
    }
}
