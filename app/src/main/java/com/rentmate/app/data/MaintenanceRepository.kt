package com.rentmate.app.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MaintenanceRepository"

/**
 * In-memory store, held for the life of the process.
 *
 * This is deliberate rather than temporary laziness: the design document states
 * that all shared state passes through the REST API, so the API is the source
 * of truth and local persistence belongs to the offline-sync work scheduled for
 * the final POE. When Seth's MaintenanceController exists, the three methods
 * below become Retrofit calls and nothing above this class changes.
 */
object MaintenanceRepository {

    private val _requests = MutableStateFlow(seedData())
    val requests: StateFlow<List<MaintenanceRequest>> = _requests.asStateFlow()

    fun add(request: MaintenanceRequest) {
        Log.d(TAG, "add ${request.id} '${request.title}' ${request.urgency}")
        _requests.value = listOf(request) + _requests.value
    }

    /** Advances one request through open -> sent -> acknowledged -> resolved. */
    fun advance(id: String) {
        _requests.value = _requests.value.map { request ->
            if (request.id == id) {
                Log.d(TAG, "advance ${request.id} from ${request.status}")
                request.advanced()
            } else {
                request
            }
        }
    }

    /**
     * Compiles every open request into one message to the landlord and marks
     * them sent. Batching is the point: one dated, itemised message beats five
     * separate ones, and it gives the tenant a single record to refer back to.
     */
    fun sendOpenRequests(): Int {
        val now = System.currentTimeMillis()
        val open = _requests.value.count { it.status == RequestStatus.OPEN }
        if (open == 0) return 0
        Log.d(TAG, "sending $open open request(s) to landlord")
        _requests.value = _requests.value.map {
            if (it.status == RequestStatus.OPEN) it.copy(status = RequestStatus.SENT, sentAt = now) else it
        }
        return open
    }

    /** One worked example so the screen is not empty in the demo. Delete before submission. */
    private fun seedData(): List<MaintenanceRequest> {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        return listOf(
            MaintenanceRequest(
                title = "Front gate motor not responding",
                description = "Remote does nothing. Gate has to be pushed open by hand.",
                category = MaintenanceCategory.ELECTRICAL,
                urgency = Urgency.MEDIUM,
                status = RequestStatus.SENT,
                createdAt = now - 5 * day,
                sentAt = now - 3 * day
            )
        )
    }
}
