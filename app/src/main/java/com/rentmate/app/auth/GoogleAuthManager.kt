package com.rentmate.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rentmate.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "GoogleAuthManager"

/**
 * US-1: obtains a Google ID token on-device via Credential Manager. No password is ever shown.
 *
 * Based on: Android Developers, About Sign in with Google,
 * https://developer.android.com/identity/sign-in/credential-manager-siwg (accessed 2026-11-06)
 */
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Shows the Google account picker and returns the ID token for the chosen account.
     * Throws if the user cancels or the returned credential is not a Google ID token.
     */
    suspend fun signIn(): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        Log.d(TAG, "requesting Google credential")
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.d(TAG, "Google ID token received")
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        Log.w(TAG, "unexpected credential type ${credential.type}")
        error("Unexpected credential type")
    }
}
