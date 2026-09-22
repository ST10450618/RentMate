package com.rentmate.app.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.authDataStore by preferencesDataStore(name = "rentmate_auth")

private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

/**
 * Holds the access/refresh token pair from US-1. This is prototype-scope
 * storage (DataStore, not the Keystore-backed EncryptedSharedPreferences a
 * production build would use) - biometric unlock, which is what actually
 * guards this token on-device, is deferred to the final POE.
 */
class TokenStore(private val context: Context) {

    val accessToken: Flow<String?> = context.authDataStore.data.map { it[ACCESS_TOKEN_KEY] }
    val refreshToken: Flow<String?> = context.authDataStore.data.map { it[REFRESH_TOKEN_KEY] }

    /** Blocking read for OkHttp's interceptor, which runs off the main thread. */
    fun currentAccessTokenBlocking(): String? = runBlocking { accessToken.first() }

    suspend fun save(accessToken: String, refreshToken: String) {
        context.authDataStore.edit {
            it[ACCESS_TOKEN_KEY] = accessToken
            it[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    suspend fun clear() {
        context.authDataStore.edit {
            it.remove(ACCESS_TOKEN_KEY)
            it.remove(REFRESH_TOKEN_KEY)
        }
    }
}
