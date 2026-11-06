package com.rentmate.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import com.rentmate.app.network.toRpcParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class MaintenanceRow(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val urgency: String,
    val status: String,
    val created_at: String,
    val sent_to_landlord_at: String? = null,
    val acknowledged_at: String? = null,
    val resolved_at: String? = null,
    val raised_by_user_id: String? = null,
    val sent_by_user_id: String? = null
)

@Serializable
private data class HouseholdLandlordRow(
    val name: String,
    val landlord_name: String? = null,
    val landlord_email: String? = null
)

@Serializable
private data class AdvanceStatusParams(val request_id_in: String)

@Serializable
private data class MaintenanceInsert(
    val household_id: String,
    val raised_by_user_id: String,
    val title: String,
    val description: String,
    val category: String,
    val urgency: String
)

@Serializable
private data class SendToLandlordParams(val household_id_in: String)

/**
 * US-10 data source for maintenance requests.
 *
 * Every function talks to the network and may throw (IOException when offline,
 * RestException when the server rejects the call). Callers must catch: see
 * [com.rentmate.app.ui.screens.MaintenanceViewModel]. Kept as an interface so
 * unit tests can swap in a fake that fails on purpose.
 */
interface MaintenanceRepository {
    /** The household's requests as last loaded from the server, newest first after an add. */
    val requests: StateFlow<List<MaintenanceRequest>>

    /** Reloads every request for the current household. */
    suspend fun refresh()

    /** Inserts a new request, prepends it to [requests] and returns it with its server ID. */
    suspend fun add(request: MaintenanceRequest): MaintenanceRequest?

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    suspend fun advance(id: String)

    /** Sends every open request to the landlord; returns how many were sent. */
    suspend fun sendOpenRequests(): Int

    /** The signed-in user's ID, used to decide who may change a request's status. */
    val currentUserId: String?

    /** RM-0009: the household's name and landlord, or null when no household is selected. */
    suspend fun landlordContact(): LandlordContact?

    /** RM-0009: stores the landlord's address on the household so every housemate can use it. */
    suspend fun saveLandlordEmail(email: String)
}

/** Supabase-backed [MaintenanceRepository] used by the app. */
@Singleton
class SupabaseMaintenanceRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : MaintenanceRepository {
    private val _requests = MutableStateFlow<List<MaintenanceRequest>>(emptyList())
    override val requests: StateFlow<List<MaintenanceRequest>> = _requests.asStateFlow()

    override val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    override suspend fun refresh() {
        val householdId = currentHousehold.id.first() ?: return
        val rows = supabase.from("maintenance_requests")
            .select { filter { eq("household_id", householdId) } }
            .decodeList<MaintenanceRow>()
        _requests.value = rows.map { it.toLocal() }
    }

    override suspend fun add(request: MaintenanceRequest): MaintenanceRequest? {
        val householdId = currentHousehold.id.first() ?: return null
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        val created = supabase.from("maintenance_requests").insert(
            MaintenanceInsert(
                household_id = householdId,
                raised_by_user_id = userId,
                title = request.title,
                description = request.description,
                category = request.category.toDto(),
                urgency = request.urgency.toDto()
            )
        ) { select() }.decodeSingle<MaintenanceRow>().toLocal()
        _requests.value = listOf(created) + _requests.value
        return created
    }

    /**
     * Advances one request through open -> sent -> acknowledged -> resolved.
     * RM-0009: goes through an RPC that refuses callers who neither logged nor sent it.
     */
    override suspend fun advance(id: String) {
        val updated = supabase.postgrest.rpc("advance_maintenance_status", AdvanceStatusParams(id).toRpcParams())
            .decodeAs<MaintenanceRow>()
        _requests.value = _requests.value.map { if (it.id == id) updated.toLocal() else it }
    }

    /** US-10: batches every open request into one dated, itemised message to the landlord. */
    override suspend fun sendOpenRequests(): Int {
        val householdId = currentHousehold.id.first() ?: return 0
        val count = supabase.postgrest.rpc("send_to_landlord", SendToLandlordParams(householdId).toRpcParams())
            .decodeAs<Int>()
        refresh()
        return count
    }

    override suspend fun landlordContact(): LandlordContact? {
        val householdId = currentHousehold.id.first() ?: return null
        val row = supabase.from("households")
            .select(columns = Columns.raw("name, landlord_name, landlord_email")) { filter { eq("id", householdId) } }
            .decodeSingle<HouseholdLandlordRow>()
        return LandlordContact(row.name, row.landlord_name, row.landlord_email)
    }

    override suspend fun saveLandlordEmail(email: String) {
        val householdId = currentHousehold.id.first() ?: return
        supabase.from("households").update({ set("landlord_email", email.trim()) }) {
            filter { eq("id", householdId) }
        }
    }
}

private fun MaintenanceCategory.toDto() = when (this) {
    MaintenanceCategory.PLUMBING -> "Plumbing"
    MaintenanceCategory.ELECTRICAL -> "Electrical"
    MaintenanceCategory.STRUCTURAL -> "Structural"
    MaintenanceCategory.APPLIANCE -> "Appliance"
    MaintenanceCategory.OTHER -> "Other"
}

private fun String.toLocalCategory() = when (this) {
    "Plumbing" -> MaintenanceCategory.PLUMBING
    "Electrical" -> MaintenanceCategory.ELECTRICAL
    "Structural" -> MaintenanceCategory.STRUCTURAL
    "Appliance" -> MaintenanceCategory.APPLIANCE
    else -> MaintenanceCategory.OTHER
}

private fun Urgency.toDto() = when (this) {
    Urgency.LOW -> "Low"
    Urgency.MEDIUM -> "Medium"
    Urgency.HIGH -> "High"
}

private fun String.toLocalUrgency() = when (this) {
    "Low" -> Urgency.LOW
    "High" -> Urgency.HIGH
    else -> Urgency.MEDIUM
}

private fun RequestStatus.toDto() = when (this) {
    RequestStatus.OPEN -> "Open"
    RequestStatus.SENT -> "Sent"
    RequestStatus.ACKNOWLEDGED -> "Acknowledged"
    RequestStatus.RESOLVED -> "Resolved"
}

private fun String.toLocalStatus() = when (this) {
    "Open" -> RequestStatus.OPEN
    "Sent" -> RequestStatus.SENT
    "Acknowledged" -> RequestStatus.ACKNOWLEDGED
    else -> RequestStatus.RESOLVED
}

private fun parseMillis(iso: String?): Long? = iso?.let {
    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
}

private fun MaintenanceRow.toLocal() = MaintenanceRequest(
    id = id,
    title = title,
    description = description,
    category = category.toLocalCategory(),
    urgency = urgency.toLocalUrgency(),
    status = status.toLocalStatus(),
    createdAt = parseMillis(created_at) ?: System.currentTimeMillis(),
    sentAt = parseMillis(sent_to_landlord_at),
    acknowledgedAt = parseMillis(acknowledged_at),
    resolvedAt = parseMillis(resolved_at),
    raisedBy = raised_by_user_id,
    sentBy = sent_by_user_id
)
