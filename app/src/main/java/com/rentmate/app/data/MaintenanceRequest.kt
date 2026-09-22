package com.rentmate.app.data

import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.rentmate.app.R
import java.util.UUID

/**
 * Maintenance requests are RentMate's differentiator: the Part 1 comparison
 * matrix found no equivalent in Splitwise, Flatastic or Tricount. This is the
 * one feature that serves the tenant's relationship with a landlord rather than
 * the housemates' relationship with each other.
 *
 * Fields mirror the MaintenanceRequest entity in Section 7 of the design document.
 */
enum class MaintenanceCategory(@StringRes val labelRes: Int) {
    PLUMBING(R.string.category_plumbing),
    ELECTRICAL(R.string.category_electrical),
    STRUCTURAL(R.string.category_structural),
    APPLIANCE(R.string.category_appliance),
    OTHER(R.string.category_other)
}

enum class Urgency(@StringRes val labelRes: Int) {
    LOW(R.string.urgency_low),
    MEDIUM(R.string.urgency_medium),
    HIGH(R.string.urgency_high)
}

/**
 * Status moves in one direction only. A request cannot go back to open once it
 * has been sent, because the tenant's record of when the landlord was told is
 * the point of keeping it.
 */
enum class RequestStatus(@StringRes val labelRes: Int) {
    OPEN(R.string.status_open),
    SENT(R.string.status_sent),
    ACKNOWLEDGED(R.string.status_acknowledged),
    RESOLVED(R.string.status_resolved);

    fun next(): RequestStatus? = when (this) {
        OPEN -> SENT
        SENT -> ACKNOWLEDGED
        ACKNOWLEDGED -> RESOLVED
        RESOLVED -> null
    }

    val canAdvance: Boolean get() = next() != null
}

data class MaintenanceRequest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val category: MaintenanceCategory = MaintenanceCategory.OTHER,
    val urgency: Urgency = Urgency.MEDIUM,
    val status: RequestStatus = RequestStatus.OPEN,
    val photo: Bitmap? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null
) {
    /** The timestamp recorded when the request entered the given status, if it has. */
    fun timestampFor(status: RequestStatus): Long? = when (status) {
        RequestStatus.OPEN -> createdAt
        RequestStatus.SENT -> sentAt
        RequestStatus.ACKNOWLEDGED -> acknowledgedAt
        RequestStatus.RESOLVED -> resolvedAt
    }

    /** Advances one step and stamps the transition. Returns this if already resolved. */
    fun advanced(now: Long = System.currentTimeMillis()): MaintenanceRequest =
        when (status.next()) {
            RequestStatus.SENT -> copy(status = RequestStatus.SENT, sentAt = now)
            RequestStatus.ACKNOWLEDGED -> copy(status = RequestStatus.ACKNOWLEDGED, acknowledgedAt = now)
            RequestStatus.RESOLVED -> copy(status = RequestStatus.RESOLVED, resolvedAt = now)
            RequestStatus.OPEN -> this   // unreachable: next() never returns OPEN
            null -> this
        }
}

enum class TitleError { BLANK, TOO_LONG }

/**
 * A request with no title is useless to a landlord, and an overlong one breaks
 * the email subject line the request is compiled into.
 */
fun validateTitle(title: String): TitleError? = when {
    title.isBlank() -> TitleError.BLANK
    title.trim().length > 80 -> TitleError.TOO_LONG
    else -> null
}