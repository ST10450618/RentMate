package com.rentmate.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Row shapes match Postgres columns (snake_case) from supabase/migrations/0001_init.sql.
// Each class is decoded by supabase-kt Postgrest with kotlinx.serialization.

/** One `households` row, including the landlord contact (RM-0009). */
@Serializable
data class HouseholdRow(
    val id: String,
    val name: String,
    val invite_code: String,
    val landlord_name: String? = null,
    val landlord_email: String? = null,
    val created_at: String
)

/** A household member with their profile name, from a join on `profiles`. */
@Serializable
data class HouseholdMemberRow(
    val user_id: String,
    val display_name: String? = null
)

/** One `profiles` row: the user's name and the settings that follow them between devices. */
@Serializable
data class ProfileRow(
    val id: String,
    val email: String,
    val display_name: String,
    val show_leaderboard: Boolean,
    val preferred_language: String,
    val notify_bills: Boolean,
    val notify_chores: Boolean,
    val notify_shopping_list: Boolean,
    val notify_maintenance: Boolean,
    val biometric_enabled: Boolean
)

/** One `bill_shares` row: what one member owes on one bill. */
@Serializable
data class BillShareRow(
    val id: String,
    val bill_id: String,
    val user_id: String,
    val amount_owed: Double,
    val is_paid: Boolean,
    val paid_at: String? = null
)

/** One `bills` row without its shares. */
@Serializable
data class BillRow(
    val id: String,
    val household_id: String,
    val title: String,
    val amount: Double,
    val due_date: String,
    val split_method: String,
    val issued_by_user_id: String,
    val created_at: String,
    val updated_at: String,
    val repeat: String = "Never",
    val repeat_stopped: Boolean = false
)

/** The embedded `profiles(display_name)` part of a join. */
@Serializable
data class ProfileNameRow(val display_name: String)

/** A bill share with the debtor's display name embedded. */
@Serializable
data class BillShareWithProfileRow(
    val id: String,
    val user_id: String,
    val amount_owed: Double,
    val is_paid: Boolean,
    val paid_at: String? = null,
    val profiles: ProfileNameRow? = null
)

/** A bill with its shares embedded; the row type the Bills and Dashboard screens use. */
@Serializable
data class BillWithSharesRow(
    val id: String,
    val household_id: String,
    val title: String,
    val amount: Double,
    val due_date: String,
    val split_method: String,
    val issued_by_user_id: String,
    val created_at: String,
    val updated_at: String,
    val repeat: String = "Never",
    val repeat_stopped: Boolean = false,
    val bill_shares: List<BillShareWithProfileRow> = emptyList(),
    /** RM-0017: true while this bill (or one of its shares) waits in the outbox. Never sent or decoded. */
    @Transient val pending: Boolean = false
)

/** One `chores` row; [rotation_order] lists user ids in turn order. */
@Serializable
data class ChoreRow(
    val id: String,
    val household_id: String,
    val title: String,
    val recurrence: String,
    val point_value: Int,
    val rotation_order: List<String>,
    val current_rotation_index: Int,
    val created_at: String
)

/** One row of the chore forecast RPC: who does the chore in a future cycle. */
@Serializable
data class ChoreForecastRow(val cycle_number: Int, val assignee_user_id: String)

/** One suggested payment returned by the `settle_up` RPC, in rand. */
@Serializable
data class SettleUpRow(
    val from_user_id: String,
    val from_display_name: String,
    val to_user_id: String,
    val to_display_name: String,
    val amount: Double
)

/** One member's chore points from the leaderboard RPC. */
@Serializable
data class LeaderboardRow(
    val user_id: String,
    val display_name: String,
    val month_points: Long,
    val total_points: Long
)

/** One `shopping_items` row with the adder's and buyer's names embedded. */
@Serializable
data class ShoppingItemRow(
    val id: String,
    val household_id: String,
    val name: String,
    val added_by_user_id: ProfileNameRow? = null,
    val is_purchased: Boolean,
    val purchased_by_user_id: ProfileNameRow? = null,
    val purchased_at: String? = null,
    val converted_to_bill_id: String? = null,
    val created_at: String,
    /** RM-0017: true while this item has a change waiting in the outbox. Never sent or decoded. */
    @Transient val pending: Boolean = false
)

/** One `maintenance_requests` row; times are ISO-8601 text. */
@Serializable
data class MaintenanceRequestRow(
    val id: String,
    val household_id: String,
    val raised_by_user_id: String,
    val title: String,
    val description: String,
    val photo_url: String? = null,
    val category: String,
    val urgency: String,
    val status: String,
    val created_at: String,
    val sent_to_landlord_at: String? = null,
    val acknowledged_at: String? = null,
    val resolved_at: String? = null
)
