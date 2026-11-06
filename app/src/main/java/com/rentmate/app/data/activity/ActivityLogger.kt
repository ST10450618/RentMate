package com.rentmate.app.data.activity

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileNameRow
import com.rentmate.app.ui.components.formatRand
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ActivityLogger"

/**
 * Writes one household event per action (RM-0032). Call it after the Supabase write
 * succeeded. Every call is fire-and-forget and never throws: if Firestore is down,
 * bills, chores and maintenance keep working and the event is only missing from the feed.
 */
interface ActivityLogger {
    fun billAdded(title: String, amount: Double, billId: String?)
    fun sharePaid(title: String, amount: Double, payerName: String?, billId: String?)
    fun settlementConfirmed(fromName: String, amount: Double)
    fun choreCompleted(title: String, choreId: String?)
    fun shoppingAdded(name: String, itemId: String?)
    fun shoppingBought(name: String, itemId: String?)
    fun maintenanceLogged(title: String, requestId: String?)
    fun maintenanceStatus(title: String, status: String, requestId: String?)
}

/** Does nothing. Used by unit tests. */
object NoOpActivityLogger : ActivityLogger {
    override fun billAdded(title: String, amount: Double, billId: String?) = Unit
    override fun sharePaid(title: String, amount: Double, payerName: String?, billId: String?) = Unit
    override fun settlementConfirmed(fromName: String, amount: Double) = Unit
    override fun choreCompleted(title: String, choreId: String?) = Unit
    override fun shoppingAdded(name: String, itemId: String?) = Unit
    override fun shoppingBought(name: String, itemId: String?) = Unit
    override fun maintenanceLogged(title: String, requestId: String?) = Unit
    override fun maintenanceStatus(title: String, status: String, requestId: String?) = Unit
}

@Singleton
class FirestoreActivityLogger @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val firebaseSession: FirebaseSession
) : ActivityLogger {

    // Outlives the screen that triggered the event, so leaving the screen does not drop it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedName: Pair<String, String>? = null

    override fun billAdded(title: String, amount: Double, billId: String?) =
        write(ActivityType.BILL_ADDED, ActivityEventBuilder.billAdded(title, formatRand(amount)), billId)

    override fun sharePaid(title: String, amount: Double, payerName: String?, billId: String?) =
        write(ActivityType.BILL_PAID, ActivityEventBuilder.sharePaid(title, formatRand(amount), payerName), billId)

    override fun settlementConfirmed(fromName: String, amount: Double) =
        write(ActivityType.BILL_PAID, ActivityEventBuilder.settlementConfirmed(fromName, formatRand(amount)), null)

    override fun choreCompleted(title: String, choreId: String?) =
        write(ActivityType.CHORE_COMPLETED, ActivityEventBuilder.choreCompleted(title), choreId)

    override fun shoppingAdded(name: String, itemId: String?) =
        write(ActivityType.SHOPPING_ADDED, ActivityEventBuilder.shoppingAdded(name), itemId)

    override fun shoppingBought(name: String, itemId: String?) =
        write(ActivityType.SHOPPING_BOUGHT, ActivityEventBuilder.shoppingBought(name), itemId)

    override fun maintenanceLogged(title: String, requestId: String?) =
        write(ActivityType.MAINTENANCE_LOGGED, ActivityEventBuilder.maintenanceLogged(title), requestId)

    override fun maintenanceStatus(title: String, status: String, requestId: String?) =
        write(ActivityType.MAINTENANCE_STATUS, ActivityEventBuilder.maintenanceStatus(title, status), requestId)

    private fun write(type: ActivityType, summary: String, refId: String?) {
        // Read now, on the caller's thread of execution, in case the user signs out before the coroutine runs.
        val householdId = currentHousehold.id.value ?: return
        scope.launch {
            try {
                firebaseSession.ensureSignedIn()
                val data = ActivityEventBuilder.toMap(type, actorName(), summary, refId, FieldValue.serverTimestamp())
                FirebaseFirestore.getInstance()
                    .collection("households").document(householdId).collection("events")
                    .add(data).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not write ${type.wireName} event", e)
            }
        }
    }

    private suspend fun actorName(): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return "Someone"
        cachedName?.takeIf { it.first == userId }?.let { return it.second }
        val name = runCatching {
            supabase.from("profiles").select { filter { eq("id", userId) } }
                .decodeSingle<ProfileNameRow>().display_name
        }.getOrNull()
        if (name != null) cachedName = userId to name
        return name ?: "Someone"
    }
}
