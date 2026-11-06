package com.rentmate.app.data.activity

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.rentmate.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirebaseSession"

/** Body of the `firebase-token` Edge Function response. */
@Serializable
private data class FirebaseTokenResponse(val token: String)

/**
 * Keeps a Firebase sign-in in step with the Supabase one (RM-0032).
 *
 * The app signs users in with Supabase, not Firebase Auth. After a Supabase sign-in this
 * class asks the `firebase-token` Edge Function for a custom token. The token carries the
 * user's household ids as a claim, and the Firestore rules read that claim. When the
 * Supabase session ends, the Firebase user signs out too.
 *
 * Every failure is logged and swallowed. Firestore being unavailable must never break
 * bills, chores or maintenance.
 *
 * Based on: Firebase docs, Authenticate with Firebase on Android Using a Custom Authentication System,
 * https://firebase.google.com/docs/auth/android/custom-auth (accessed 2026-11-06)
 */
@Singleton
class FirebaseSession @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    /** Starts following the Supabase session. Call once from Application.onCreate. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                supabase.auth.sessionStatus.collect { status ->
                    Log.d(TAG, "Supabase session status: ${status::class.simpleName}")
                    when (status) {
                        is SessionStatus.Authenticated -> ensureSignedIn()
                        is SessionStatus.NotAuthenticated -> signOut()
                        else -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not follow the Supabase session", e)
            }
        }
    }

    /** Signs in to Firebase if nobody is signed in as the current Supabase user. Never throws. */
    suspend fun ensureSignedIn() {
        try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            if (FirebaseAuth.getInstance().currentUser?.uid == userId) return
            mint()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-in failed", e)
        }
    }

    /**
     * Gets a new custom token so the `households` claim includes a household the user
     * just created or joined. Never throws.
     */
    fun refreshClaims() {
        Log.d(TAG, "refreshing household claims")
        scope.launch {
            try {
                if (supabase.auth.currentUserOrNull() != null) mint()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Firebase claim refresh failed", e)
            }
        }
    }

    /** Signs the Firebase user out so Firestore stops serving the old household. */
    private fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
            Log.d(TAG, "signed out of Firebase")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-out failed", e)
        }
    }

    /** Exchanges the Supabase access token for a Firebase custom token and signs in with it. */
    private suspend fun mint() {
        mutex.withLock {
            val accessToken = supabase.auth.currentAccessTokenOrNull() ?: return@withLock
            val token = fetchCustomToken(accessToken)
            FirebaseAuth.getInstance().signInWithCustomToken(token).await()
            Log.d(TAG, "signed in to Firebase")
        }
    }

    /** Calls the `firebase-token` Edge Function. Throws on a non-200 response. */
    private suspend fun fetchCustomToken(accessToken: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "requesting a Firebase custom token")
        val conn = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/firebase-token")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            check(conn.responseCode == 200) { "firebase-token returned ${conn.responseCode}" }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<FirebaseTokenResponse>(body).token
        } finally {
            conn.disconnect()
        }
    }
}
