package com.rentmate.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// RM-0017: the request bodies of the writes that can be queued offline. The same
// classes are sent online and stored as OutboxEntry.payloadJson offline, so
// RM-0018's worker can decode an entry and send exactly what the app would have
// sent. Field names match the Postgres columns and RPC parameters.

/** JSON used for outbox payloads. Keeps defaults so a payload never loses a field. */
val OutboxJson: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/**
 * Insert body for `shopping_items` (US-13). [id] is generated on the device, so a
 * replay that already reached the server fails with a duplicate key instead of
 * adding the item twice.
 */
@Serializable
data class ShoppingItemInsert(
    /** Client-generated UUID of the new item. */
    val id: String,
    val household_id: String,
    val name: String,
    val added_by_user_id: String
)

/** Update body that marks a `shopping_items` row purchased. */
@Serializable
data class ShoppingItemPurchase(
    val is_purchased: Boolean = true,
    val purchased_by_user_id: String,
    val purchased_at: String
)

/** One entry of `create_bill`'s `shares_in`: a percentage or rand amount, or 0 for Equal. */
@Serializable
data class CreateBillShareParam(val user_id: String, val value: Double)

/** Parameters of the `create_bill` RPC (migration 0014). */
@Serializable
data class CreateBillParams(
    val household_id_in: String,
    val title_in: String,
    val amount_in: Double,
    val due_date_in: String,
    val split_method_in: String,
    val shares_in: List<CreateBillShareParam>,
    val repeat_in: String
)

/** Just the id column of an inserted or returned row. */
@Serializable
data class IdRow(val id: String)
