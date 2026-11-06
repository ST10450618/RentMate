package com.rentmate.app.data.activity

/** Longest value the Firestore rules accept for `actorName` and `summary`. */
internal const val EVENT_TEXT_MAX = 200

/**
 * Builds the Firestore document for one event, and the summary texts.
 * Pure Kotlin so unit tests can check it without Firebase.
 *
 * The field set must match `firestore.rules`: type, actorName, summary, refId, createdAt.
 */
object ActivityEventBuilder {

    /** [createdAt] is `FieldValue.serverTimestamp()` in the app; tests pass a marker. */
    fun toMap(type: ActivityType, actorName: String, summary: String, refId: String?, createdAt: Any): Map<String, Any?> =
        mapOf(
            "type" to type.wireName,
            "actorName" to bounded(actorName, "Someone"),
            "summary" to bounded(summary, type.wireName),
            "refId" to refId?.takeIf { it.isNotBlank() }?.take(EVENT_TEXT_MAX),
            "createdAt" to createdAt
        )

    /** Trims, falls back when blank, and cuts to the length the rules allow. */
    internal fun bounded(value: String, fallback: String): String {
        val text = value.trim().ifEmpty { fallback }
        return if (text.length <= EVENT_TEXT_MAX) text else text.take(EVENT_TEXT_MAX - 1) + "…"
    }

    fun billAdded(title: String, amount: String) = "added a bill: $title $amount"
    fun sharePaid(title: String, amount: String, payerName: String?) =
        if (payerName.isNullOrBlank()) "marked a share of $title $amount as paid"
        else "marked ${payerName}'s share of $title $amount as paid"
    fun settlementConfirmed(fromName: String, amount: String) = "confirmed $amount received from $fromName"
    fun choreCompleted(title: String) = "completed: $title"
    fun shoppingAdded(name: String) = "added to the shopping list: $name"
    fun shoppingBought(name: String) = "bought: $name"
    fun maintenanceLogged(title: String) = "logged a fault: $title"
    fun maintenanceStatus(title: String, status: String) = "marked $title as $status"
}
