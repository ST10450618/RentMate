package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
enum class SplitMethod { Equal, Percentage, ExactAmount }

@Serializable
data class BillShareInput(val userId: String, val amountOrPercentage: Double? = null)

@Serializable
data class CreateBillRequest(
    val title: String,
    val amount: Double,
    val dueDate: String,
    val splitMethod: SplitMethod,
    val shares: List<BillShareInput>
)

@Serializable
data class BillShareResponse(
    val id: String,
    val userId: String,
    val displayName: String,
    val amountOwed: Double,
    val isPaid: Boolean,
    val paidAt: String? = null
)

@Serializable
data class BillResponse(
    val id: String,
    val householdId: String,
    val title: String,
    val amount: Double,
    val dueDate: String,
    val splitMethod: SplitMethod,
    val issuedByUserId: String,
    val issuedByDisplayName: String,
    val createdAt: String,
    val updatedAt: String,
    val shares: List<BillShareResponse>
)

@Serializable
data class PayBillShareRequest(val clientTimestamp: String)

@Serializable
data class SettlementSuggestion(
    val fromUserId: String,
    val fromDisplayName: String,
    val toUserId: String,
    val toDisplayName: String,
    val amount: Double
)

@Serializable
data class SettleUpResponse(val isProvisional: Boolean, val suggestions: List<SettlementSuggestion>)

@Serializable
data class ConfirmSettlementRequest(val toUserId: String, val amount: Double)

/** Matches RentMate.Api's BillsController (US-6, US-7, US-11). */
interface BillsApi {
    @POST("api/households/{householdId}/bills")
    suspend fun create(@Path("householdId") householdId: String, @Body request: CreateBillRequest): BillResponse

    @GET("api/households/{householdId}/bills")
    suspend fun list(@Path("householdId") householdId: String): List<BillResponse>

    @PUT("api/bills/{billId}/shares/{shareId}/pay")
    suspend fun payShare(
        @Path("billId") billId: String,
        @Path("shareId") shareId: String,
        @Body request: PayBillShareRequest
    ): BillResponse

    /** US-11: the minimum set of payments that would settle the household right now. */
    @GET("api/households/{householdId}/settle-up")
    suspend fun settleUp(@Path("householdId") householdId: String): SettleUpResponse

    @POST("api/households/{householdId}/settlements")
    suspend fun confirmSettlement(@Path("householdId") householdId: String, @Body request: ConfirmSettlementRequest)
}
