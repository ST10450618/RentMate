package com.rentmate.app.data

import com.rentmate.app.network.CreateMaintenanceRequestDto
import com.rentmate.app.network.MaintenanceApi
import com.rentmate.app.network.MaintenanceCategoryDto
import com.rentmate.app.network.MaintenanceRequestResponseDto
import com.rentmate.app.network.MaintenanceStatusDto
import com.rentmate.app.network.MaintenanceUrgencyDto
import com.rentmate.app.network.UpdateMaintenanceStatusRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceRepository @Inject constructor(
    private val api: MaintenanceApi,
    private val currentHousehold: CurrentHousehold
) {
    private val _requests = MutableStateFlow<List<MaintenanceRequest>>(emptyList())
    val requests: StateFlow<List<MaintenanceRequest>> = _requests.asStateFlow()

    suspend fun refresh() {
        val householdId = currentHousehold.id.first() ?: return
        _requests.value = api.list(householdId).map { it.toLocal() }
    }

    suspend fun add(request: MaintenanceRequest) {
        val householdId = currentHousehold.id.first() ?: return
        val created = api.create(
            householdId,
            CreateMaintenanceRequestDto(
                title = request.title,
                description = request.description,
                photoUrl = null,
                category = request.category.toDto(),
                urgency = request.urgency.toDto()
            )
        )
        _requests.value = listOf(created.toLocal()) + _requests.value
    }

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    suspend fun advance(id: String) {
        val next = _requests.value.firstOrNull { it.id == id }?.status?.next() ?: return
        val updated = api.updateStatus(id, UpdateMaintenanceStatusRequest(next.toDto()))
        _requests.value = _requests.value.map { if (it.id == id) updated.toLocal() else it }
    }

    /** US-10: batches every open request into one dated, itemised message to the landlord. */
    suspend fun sendOpenRequests(): Int {
        val householdId = currentHousehold.id.first() ?: return 0
        val result = api.sendToLandlord(householdId)
        refresh()
        return result.requestsIncluded
    }
}

private fun MaintenanceCategory.toDto() = when (this) {
    MaintenanceCategory.PLUMBING -> MaintenanceCategoryDto.Plumbing
    MaintenanceCategory.ELECTRICAL -> MaintenanceCategoryDto.Electrical
    MaintenanceCategory.STRUCTURAL -> MaintenanceCategoryDto.Structural
    MaintenanceCategory.APPLIANCE -> MaintenanceCategoryDto.Appliance
    MaintenanceCategory.OTHER -> MaintenanceCategoryDto.Other
}

private fun MaintenanceCategoryDto.toLocal() = when (this) {
    MaintenanceCategoryDto.Plumbing -> MaintenanceCategory.PLUMBING
    MaintenanceCategoryDto.Electrical -> MaintenanceCategory.ELECTRICAL
    MaintenanceCategoryDto.Structural -> MaintenanceCategory.STRUCTURAL
    MaintenanceCategoryDto.Appliance -> MaintenanceCategory.APPLIANCE
    MaintenanceCategoryDto.Other -> MaintenanceCategory.OTHER
}

private fun Urgency.toDto() = when (this) {
    Urgency.LOW -> MaintenanceUrgencyDto.Low
    Urgency.MEDIUM -> MaintenanceUrgencyDto.Medium
    Urgency.HIGH -> MaintenanceUrgencyDto.High
}

private fun MaintenanceUrgencyDto.toLocal() = when (this) {
    MaintenanceUrgencyDto.Low -> Urgency.LOW
    MaintenanceUrgencyDto.Medium -> Urgency.MEDIUM
    MaintenanceUrgencyDto.High -> Urgency.HIGH
}

private fun RequestStatus.toDto() = when (this) {
    RequestStatus.OPEN -> MaintenanceStatusDto.Open
    RequestStatus.SENT -> MaintenanceStatusDto.Sent
    RequestStatus.ACKNOWLEDGED -> MaintenanceStatusDto.Acknowledged
    RequestStatus.RESOLVED -> MaintenanceStatusDto.Resolved
}

private fun MaintenanceStatusDto.toLocal() = when (this) {
    MaintenanceStatusDto.Open -> RequestStatus.OPEN
    MaintenanceStatusDto.Sent -> RequestStatus.SENT
    MaintenanceStatusDto.Acknowledged -> RequestStatus.ACKNOWLEDGED
    MaintenanceStatusDto.Resolved -> RequestStatus.RESOLVED
}

private fun parseMillis(iso: String?): Long? = iso?.let {
    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
}

private fun MaintenanceRequestResponseDto.toLocal() = MaintenanceRequest(
    id = id,
    title = title,
    description = description,
    category = category.toLocal(),
    urgency = urgency.toLocal(),
    status = status.toLocal(),
    photo = null,
    createdAt = parseMillis(createdAt) ?: System.currentTimeMillis(),
    sentAt = parseMillis(sentToLandlordAt),
    acknowledgedAt = parseMillis(acknowledgedAt),
    resolvedAt = parseMillis(resolvedAt)
)
