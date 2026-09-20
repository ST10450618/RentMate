package co.za.rentmate.app.auth

import co.za.rentmate.app.network.AuthApi
import co.za.rentmate.app.network.GoogleAuthRequest
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val authApi: AuthApi
) {
    suspend fun signIn(): Result<String> = runCatching {
        val idToken = googleAuthManager.signIn()
        authApi.signInWithGoogle(GoogleAuthRequest(idToken)).jwt
    }
}
