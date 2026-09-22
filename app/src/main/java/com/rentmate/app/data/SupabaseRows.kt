package com.rentmate.app.data

import kotlinx.serialization.Serializable

// Row shapes match Postgres columns (snake_case) from supabase/migrations/0001_init.sql.

@Serializable
data class HouseholdRow(
    val id: String,
    val name: String,
    val invite_code: String,
    val landlord_name: String? = null,
    val landlord_email: String? = null,
    val created_at: String
)

@Serializable
data class HouseholdMemberRow(
    val user_id: String,
    val display_name: String? = null
)

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

@Serializable
data class BillShareRow(
    val id: String,
    val bill_id: String,
    val user_id: String,
    val amount_owed: Double,
    val is_paid: Boolean,
    val paid_at: String? = null
)

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
    val updated_at: String
)

@Serializable
data class ProfileNameRow(val display_name: String)

@Serializable
data class BillShareWithProfileRow(
    val id: String,
    val user_id: String,
    val amount_owed: Double,
    val is_paid: Boolean,
    val paid_at: String? = null,
    val profiles: ProfileNameRow? = null
)

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
    val bill_shares: List<BillShareWithProfileRow> = emptyList()
)

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

@Serializable
data class ChoreForecastRow(val cycle_number: Int, val assignee_user_id: String)

@Serializable
data class SettleUpRow(
    val from_user_id: String,
    val from_display_name: String,
    val to_user_id: String,
    val to_display_name: String,
    val amount: Double
)

@Serializable
data class LeaderboardRow(
    val user_id: String,
    val display_name: String,
    val month_points: Long,
    val total_points: Long
)

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
    val created_at: String
)

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
