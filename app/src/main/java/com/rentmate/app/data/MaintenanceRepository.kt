package com.rentmate.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
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
    val resolved_at: String? = null
)

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
private data class MaintenanceStatusUpdate(val status: String)

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

    /** Inserts a new request and prepends it to [requests]. */
    suspend fun add(request: MaintenanceRequest)

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    suspend fun advance(id: String)

    /** Sends every open request to the landlord; returns how many were sent. */
    suspend fun sendOpenRequests(): Int
}

/** Supabase-backed [MaintenanceRepository] used by the app. */
@Singleton
class SupabaseMaintenanceRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : MaintenanceRepository {
    private val _requests = MutableStateFlow<List<MaintenanceRequest>>(emptyList())
    override val requests: StateFlow<List<MaintenanceRequest>> = _requests.asStateFlow()

    override suspend fun refresh() {
        val householdId = currentHousehold.id.first() ?: return
        val rows = supabase.from("maintenance_requests")
            .select { filter { eq("household_id", householdId) } }
            .decodeList<MaintenanceRow>()
        _requests.value = rows.map { it.toLocal() }
    }

    override suspend fun add(request: MaintenanceRequest) {
        val householdId = currentHousehold.id.first() ?: return
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val created = supabase.from("maintenance_requests").insert(
            MaintenanceInsert(
                household_id = householdId,
                raised_by_user_id = userId,
                title = request.title,
                description = request.description,
                category = request.category.toDto(),
                urgency = request.urgency.toDto()
            )
        ) { select() }.decodeSingle<MaintenanceRow>()
        _requests.value = listOf(created.toLocal()) + _requests.value
    }

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    override suspend fun advance(id: String) {
        val next = _requests.value.firstOrNull { it.id == id }?.status?.next() ?: return
        val updated = supabase.from("maintenance_requests").update(
            MaintenanceStatusUpdate(next.toDto())
        ) {
            filter { eq("id", id) }
            select()
        }.decodeSingle<MaintenanceRow>()
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
    photo = null,
    createdAt = parseMillis(created_at) ?: System.currentTimeMillis(),
    sentAt = parseMillis(sent_to_landlord_at),
    acknowledgedAt = parseMillis(acknowledged_at),
    resolvedAt = parseMillis(resolved_at)
)
