package com.rentmate.app.network

import android.util.Log
import com.rentmate.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

private const val TAG = "SupabaseModule"

/**
 * Hilt module that builds the one [SupabaseClient] the app uses, with Postgrest,
 * Storage and Auth installed. The URL and anon key come from BuildConfig.
 *
 * Based on: Supabase Kotlin reference, Initializing,
 * https://supabase.com/docs/reference/kotlin/initializing (accessed 2026-11-06)
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /** Creates the client once per process; Auth saves and refreshes the session on the device. */
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        // RM-0028: photo attachments; uploads carry the signed-in user's JWT.
        install(Storage)
        install(Auth) {
            // RM-0002: keep the session on the device and refresh it, so a
            // relaunch skips Login.
            autoSaveToStorage = true
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
    }.also { Log.d(TAG, "Supabase client created") }
}
