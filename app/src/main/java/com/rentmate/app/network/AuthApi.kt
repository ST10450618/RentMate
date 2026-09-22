package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
data class GoogleSignInRequest(val idToken: String, val fcmToken: String? = null)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val showLeaderboard: Boolean,
    val preferredLanguage: String,
    val biometricEnabled: Boolean,
    val notifyBills: Boolean,
    val notifyChores: Boolean,
    val notifyShoppingList: Boolean,
    val notifyMaintenance: Boolean
)

@Serializable
data class UpdateSettingsRequest(
    val preferredLanguage: String? = null,
    val showLeaderboard: Boolean? = null,
    val notifyBills: Boolean? = null,
    val notifyChores: Boolean? = null,
    val notifyShoppingList: Boolean? = null,
    val notifyMaintenance: Boolean? = null,
    val biometricEnabled: Boolean? = null,
    val fcmToken: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val user: UserResponse
)

/** Matches RentMate.Api's AuthController (POST /api/auth/google-signin, etc). */
interface AuthApi {
    @POST("api/auth/google-signin")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: RefreshTokenRequest)

    @GET("api/auth/me")
    suspend fun me(): UserResponse

    @PUT("api/auth/me/settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): UserResponse
}
