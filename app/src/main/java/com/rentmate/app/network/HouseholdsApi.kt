package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class JoinHouseholdRequest(val inviteCode: String)

@Serializable
data class HouseholdMemberResponse(val userId: String, val displayName: String, val email: String)

@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val inviteCode: String,
    val landlordName: String? = null,
    val landlordEmail: String? = null,
    val members: List<HouseholdMemberResponse> = emptyList()
)

/** Matches RentMate.Api's HouseholdsController (US-5). */
interface HouseholdsApi {
    @POST("api/households")
    suspend fun create(@Body request: CreateHouseholdRequest): HouseholdResponse

    @POST("api/households/join")
    suspend fun join(@Body request: JoinHouseholdRequest): HouseholdResponse

    @GET("api/households/mine")
    suspend fun mine(): List<HouseholdResponse>
}
