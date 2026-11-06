package com.rentmate.app.data.activity

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreActivityRepo"

/**
 * Reads the feed from `households/{householdId}/events` (RM-0032). The rules only let
 * members of the household read it, so the user must be signed in to Firebase
 * (see [FirebaseSession]); until then the listener reports a permission error.
 *
 * Based on: Firebase docs, Get realtime updates with Cloud Firestore,
 * https://firebase.google.com/docs/firestore/query-data/listen (accessed 2026-11-06)
 * Based on: Android Developers, Kotlin flows on Android (callbackFlow),
 * https://developer.android.com/kotlin/flow (accessed 2026-11-06)
 */
@Singleton
class FirestoreActivityRepository @Inject constructor(
    private val firebaseSession: FirebaseSession
) : ActivityRepository {

    /** Opens a snapshot listener for the newest [limit] events; it is removed when the flow is cancelled. */
    override fun observe(householdId: String, limit: Int): Flow<List<ActivityEvent>> = callbackFlow {
        firebaseSession.ensureSignedIn()
        Log.d(TAG, "listening to the activity feed (limit=$limit)")
        val registration = FirebaseFirestore.getInstance()
            .collection("households").document(householdId).collection("events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "activity feed listener failed", error)
                    close(error)
                    return@addSnapshotListener
                }
                val now = Instant.now()
                Log.d(TAG, "activity snapshot: ${snapshot?.size() ?: 0} event(s)")
                trySend(snapshot?.documents.orEmpty().mapNotNull { toEvent(it, now) })
            }
        awaitClose {
            Log.d(TAG, "activity feed listener removed")
            registration.remove()
        }
    }

    /** Maps one document to an event; skips types this app version does not know. */
    private fun toEvent(doc: DocumentSnapshot, now: Instant): ActivityEvent? {
        val type = ActivityType.fromWireName(doc.getString("type")) ?: return null
        val createdAt = doc.getTimestamp("createdAt")?.toInstantOrNull() ?: now
        return ActivityEvent(
            id = doc.id,
            type = type,
            actorName = doc.getString("actorName").orEmpty(),
            summary = doc.getString("summary").orEmpty(),
            refId = doc.getString("refId"),
            createdAt = createdAt
        )
    }

    /** Converts a Firestore [Timestamp] to a [java.time.Instant] without losing nanoseconds. */
    private fun Timestamp.toInstantOrNull(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
}
