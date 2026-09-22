package com.rentmate.app.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT

@Serializable
data class AddShoppingItemRequest(val name: String)

@Serializable
data class PurchaseShoppingItemRequest(val clientTimestamp: String, val convertToBill: Boolean)

@Serializable
data class ShoppingItemResponse(
    val id: String,
    val householdId: String,
    val name: String,
    val addedByUserId: String,
    val addedByDisplayName: String,
    val isPurchased: Boolean,
    val purchasedByUserId: String? = null,
    val purchasedAt: String? = null,
    val convertedToBillId: String? = null,
    val createdAt: String
)

interface ShoppingListApi {
    @POST("api/households/{householdId}/shopping-items")
    suspend fun add(@Path("householdId") householdId: String, @Body request: AddShoppingItemRequest): ShoppingItemResponse

    @GET("api/households/{householdId}/shopping-items")
    suspend fun list(@Path("householdId") householdId: String): List<ShoppingItemResponse>

    @PUT("api/shopping-items/{id}/purchase")
    suspend fun purchase(@Path("id") id: String, @Body request: PurchaseShoppingItemRequest): ShoppingItemResponse
}
