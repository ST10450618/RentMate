package com.rentmate.app.auth

import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.AuthApi
import com.rentmate.app.network.GoogleSignInRequest
import com.rentmate.app.network.HouseholdsApi
import javax.inject.Inject

/** Whether the signed-in user already belongs to a household, decides S1's next screen (S2 vs S3). */
data class SignInResult(val hasHousehold: Boolean)

class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val authApi: AuthApi,
    private val householdsApi: HouseholdsApi,
    private val tokenStore: TokenStore,
    private val currentHousehold: CurrentHousehold
) {
    suspend fun signIn(): Result<SignInResult> = runCatching {
        val idToken = googleAuthManager.signIn()
        val auth = authApi.googleSignIn(GoogleSignInRequest(idToken))
        tokenStore.save(auth.accessToken, auth.refreshToken)

        val households = householdsApi.mine()
        households.firstOrNull()?.let { currentHousehold.set(it.id) }
        SignInResult(hasHousehold = households.isNotEmpty())
    }

    suspend fun signOut() {
        tokenStore.clear()
    }
}
