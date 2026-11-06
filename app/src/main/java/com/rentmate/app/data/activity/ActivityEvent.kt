package com.rentmate.app.data.activity

import java.time.Instant

/**
 * The kind of household event an [ActivityEvent] records. [wireName] is the exact
 * string stored in the Firestore `type` field.
 */
enum class ActivityType(val wireName: String) {
    /** A housemate created a bill. */
    BILL_ADDED("bill_added"),
    /** A housemate marked a share paid or confirmed a settlement. */
    BILL_PAID("bill_paid"),
    /** A housemate finished a chore. */
    CHORE_COMPLETED("chore_completed"),
    /** A housemate added an item to the shopping list. */
    SHOPPING_ADDED("shopping_added"),
    /** A housemate bought an item on the shopping list. */
    SHOPPING_BOUGHT("shopping_bought"),
    /** A housemate logged a maintenance fault. */
    MAINTENANCE_LOGGED("maintenance_logged"),
    /** A maintenance request changed status. */
    MAINTENANCE_STATUS("maintenance_status");

    companion object {
        /** Maps a Firestore `type` string to an [ActivityType], or null for an unknown (newer) type. */
        fun fromWireName(value: String?): ActivityType? = entries.firstOrNull { it.wireName == value }
    }
}

/**
 * One entry in the household activity feed.
 *
 * Firestore location: `households/{householdId}/events/{eventId}`; [id] is the document id.
 *
 * Document schema:
 * - `type` (string): one of the [ActivityType.wireName] values.
 * - `actorName` (string): display name of the housemate who did it.
 * - `summary` (string): ready-to-show text, e.g. "added a bill: Electricity R1 200".
 * - `refId` (string): id of the bill, chore, shopping item or maintenance request concerned.
 * - `createdAt` (timestamp): server time; the feed is ordered by it, newest first.
 *
 * @param summary the text after the actor's name, so the row can show the name separately.
 */
data class ActivityEvent(
    val id: String,
    val type: ActivityType,
    val actorName: String,
    val summary: String,
    val refId: String?,
    val createdAt: Instant
)
