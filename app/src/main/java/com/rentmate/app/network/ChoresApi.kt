package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

@Serializable
enum class ChoreRecurrence { Once, Weekly, Monthly }

@Serializable
data class CreateChoreRequest(
    val title: String,
    val recurrence: ChoreRecurrence,
    val pointValue: Int,
    val rotationOrder: List<String>
)

@Serializable
data class ChoreForecastEntry(val cycleNumber: Int, val assigneeUserId: String, val assigneeDisplayName: String)

@Serializable
data class ChoreResponse(
    val id: String,
    val householdId: String,
    val title: String,
    val recurrence: ChoreRecurrence,
    val pointValue: Int,
    val currentAssigneeUserId: String? = null,
    val currentAssigneeDisplayName: String? = null,
    val nextFourCycles: List<ChoreForecastEntry> = emptyList(),
    val createdAt: String
)

@Serializable
data class CompleteChoreRequest(val offlineTimestamp: String? = null)

@Serializable
data class LeaderboardEntry(val userId: String, val displayName: String, val monthPoints: Int, val totalPoints: Int)

@Serializable
data class LeaderboardResponse(val year: Int, val month: Int, val entries: List<LeaderboardEntry>)

interface ChoresApi {
    @POST("api/households/{householdId}/chores")
    suspend fun create(@Path("householdId") householdId: String, @Body request: CreateChoreRequest): ChoreResponse

    @GET("api/households/{householdId}/chores")
    suspend fun list(@Path("householdId") householdId: String): List<ChoreResponse>

    @POST("api/chores/{id}/complete")
    suspend fun complete(@Path("id") choreId: String, @Body request: CompleteChoreRequest)

    @GET("api/households/{householdId}/leaderboard")
    suspend fun leaderboard(@Path("householdId") householdId: String): LeaderboardResponse
}
