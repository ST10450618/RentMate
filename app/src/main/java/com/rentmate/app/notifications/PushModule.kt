package com.rentmate.app.notifications

import android.content.Context
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

@Serializable
private data class RegisterTokenParams(val p_token: String, val p_platform: String = "android")

@Serializable
private data class UnregisterTokenParams(val p_token: String)

/** Reads the token from Firebase Cloud Messaging. Returns null if Firebase did not start. */
class FirebasePushTokenSource(private val context: Context) : PushTokenSource {
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return FirebaseMessaging.getInstance().token.await()
    }
}

/** Saves tokens through the RPCs in migration 0014_device_tokens.sql. */
class SupabaseDeviceTokenStore(private val supabase: SupabaseClient) : DeviceTokenStore {
    override suspend fun register(token: String): Boolean {
        if (supabase.auth.currentSessionOrNull() == null) return false
        supabase.postgrest.rpc("register_device_token", RegisterTokenParams(token).toRpcParams())
        return true
    }

    override suspend fun unregister(token: String) {
        if (supabase.auth.currentSessionOrNull() == null) return
        supabase.postgrest.rpc("unregister_device_token", UnregisterTokenParams(token).toRpcParams())
    }
}

/** Wires the RM-0021 push classes. Tests build [DeviceTokenRegistrar] with fakes instead. */
@Module
@InstallIn(SingletonComponent::class)
object PushModule {

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
