package co.za.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class AuthResponse(val jwt: String, val userId: String, val expiresAt: String)

interface AuthApi {
    @POST("auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleAuthRequest): AuthResponse
}
