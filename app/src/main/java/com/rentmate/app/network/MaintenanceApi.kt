package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
enum class MaintenanceCategoryDto { Plumbing, Electrical, Structural, Appliance, Other }

@Serializable
enum class MaintenanceUrgencyDto { Low, Medium, High }

@Serializable
enum class MaintenanceStatusDto { Open, Sent, Acknowledged, Resolved }

@Serializable
data class CreateMaintenanceRequestDto(
    val title: String,
    val description: String,
    val photoUrl: String? = null,
    val category: MaintenanceCategoryDto,
    val urgency: MaintenanceUrgencyDto
)

@Serializable
data class UpdateMaintenanceStatusRequest(val status: MaintenanceStatusDto)

@Serializable
data class MaintenanceRequestResponseDto(
    val id: String,
    val householdId: String,
    val raisedByUserId: String,
    val raisedByDisplayName: String,
    val title: String,
    val description: String,
    val photoUrl: String? = null,
    val category: MaintenanceCategoryDto,
    val urgency: MaintenanceUrgencyDto,
    val status: MaintenanceStatusDto,
    val createdAt: String,
    val sentToLandlordAt: String? = null,
    val acknowledgedAt: String? = null,
    val resolvedAt: String? = null
)

@Serializable
data class SendToLandlordResponse(val requestsIncluded: Int, val sentAt: String, val letterPreview: String)

interface MaintenanceApi {
    @POST("api/households/{householdId}/maintenance")
    suspend fun create(
        @Path("householdId") householdId: String,
        @Body request: CreateMaintenanceRequestDto
    ): MaintenanceRequestResponseDto

    @GET("api/households/{householdId}/maintenance")
    suspend fun list(@Path("householdId") householdId: String): List<MaintenanceRequestResponseDto>

    @PUT("api/maintenance/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body request: UpdateMaintenanceStatusRequest
    ): MaintenanceRequestResponseDto

    @POST("api/households/{householdId}/maintenance/send-to-landlord")
    suspend fun sendToLandlord(@Path("householdId") householdId: String): SendToLandlordResponse
}
