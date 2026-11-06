package com.rentmate.app.data.activity

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

/**
 * Reads the feed from `households/{householdId}/events` (RM-0032). The rules only let
 * members of the household read it, so the user must be signed in to Firebase
 * (see [FirebaseSession]); until then the listener reports a permission error.
 */
@Singleton
class FirestoreActivityRepository @Inject constructor(
    private val firebaseSession: FirebaseSession
) : ActivityRepository {

    override fun observe(householdId: String, limit: Int): Flow<List<ActivityEvent>> = callbackFlow {
        firebaseSession.ensureSignedIn()
        val registration = FirebaseFirestore.getInstance()
            .collection("households").document(householdId).collection("events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val now = Instant.now()
                trySend(snapshot?.documents.orEmpty().mapNotNull { toEvent(it, now) })
            }
        awaitClose { registration.remove() }
    }

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

    private fun Timestamp.toInstantOrNull(): Instant = Instant.ofEpochSecond(seconds, nanoseconds.toLong())
}
