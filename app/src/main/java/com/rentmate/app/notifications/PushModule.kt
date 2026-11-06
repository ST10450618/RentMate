package com.rentmate.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.rentmate.app.network.toRpcParams
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import javax.inject.Singleton

private const val TAG = "PushModule"

/** Arguments for the `register_device_token` RPC. */
@Serializable
private data class RegisterTokenParams(val p_token: String, val p_platform: String = "android")

/** Arguments for the `unregister_device_token` RPC. */
@Serializable
private data class UnregisterTokenParams(val p_token: String)

/**
 * Reads the token from Firebase Cloud Messaging. Returns null if Firebase did not start.
 *
 * Based on: Firebase docs, Get started with Firebase Cloud Messaging in Android apps,
 * https://firebase.google.com/docs/cloud-messaging/android/get-started (accessed 2026-11-06)
 */
class FirebasePushTokenSource(private val context: Context) : PushTokenSource {
    /** Returns null in a build without google-services.json, where FirebaseApp never starts. */
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "Firebase not initialised; no FCM token")
            return null
        }
        return FirebaseMessaging.getInstance().token.await()
    }
}

/** Saves tokens through the RPCs in migration 0015_device_tokens.sql. */
class SupabaseDeviceTokenStore(private val supabase: SupabaseClient) : DeviceTokenStore {
    /** Calls `register_device_token`. When signed out, it skips the call and returns false. */
    override suspend fun register(token: String): Boolean {
        if (supabase.auth.currentSessionOrNull() == null) return false
        supabase.postgrest.rpc("register_device_token", RegisterTokenParams(token).toRpcParams())
        Log.i(TAG, "device token registered")
        return true
    }

    /** Calls `unregister_device_token`. Does nothing when signed out. */
    override suspend fun unregister(token: String) {
        if (supabase.auth.currentSessionOrNull() == null) return
        supabase.postgrest.rpc("unregister_device_token", UnregisterTokenParams(token).toRpcParams())
        Log.i(TAG, "device token unregistered")
    }
}

/** Wires the RM-0021 push classes. Tests build [DeviceTokenRegistrar] with fakes instead. */
@Module
@InstallIn(SingletonComponent::class)
object PushModule {

    /** Provides one registrar for the app. Its own IO scope lets a save finish after the messaging service stops. */
    @Provides
    @Singleton
    fun provideDeviceTokenRegistrar(
        @ApplicationContext context: Context,
        supabase: SupabaseClient
    ): DeviceTokenRegistrar = DeviceTokenRegistrar(
        source = FirebasePushTokenSource(context),
        store = SupabaseDeviceTokenStore(supabase),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
}
