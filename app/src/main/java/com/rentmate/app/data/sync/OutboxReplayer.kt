package com.rentmate.app.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.rentmate.app.data.BillsRemoteSource
import com.rentmate.app.data.BillsRepository
import com.rentmate.app.data.CreateBillParams
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.OutboxJson
import com.rentmate.app.data.ShoppingItemInsert
import com.rentmate.app.data.ShoppingItemPurchase
import com.rentmate.app.data.ShoppingRemoteSource
import com.rentmate.app.data.ShoppingRepository
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository
import com.rentmate.app.data.local.OutboxEntry
import com.rentmate.app.data.local.OutboxOp
import com.rentmate.app.data.local.RentMateDatabase
import com.rentmate.app.data.local.SyncRejectionEntity
import com.rentmate.app.data.local.SyncState
import com.rentmate.app.ui.error.AppError
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

private const val TAG = "OutboxReplayer"

/** Outbox table name of shopping item inserts and updates. */
private const val SHOPPING_ITEMS = "shopping_items"

/** Outbox RPC name of a queued bill. */
private const val CREATE_BILL = "create_bill"

/** RM-0018: how a replay run ended. */
enum class ReplayResult {
    /** The queue is empty (every entry was sent or dropped). */
    DONE,

    /** The run stopped at an entry that can still succeed later. Nothing was lost. */
    RETRY
}

/**
 * RM-0018: sends the outbox to Supabase, oldest entry first, and applies
 * [ConflictRules] to each result. A plain class so tests can run it with an
 * in-memory Room database and fake remotes, without WorkManager.
 *
 * Every local change for one entry (deleting the entry, settling or removing the
 * local row, recording a rejection) runs in one Room transaction. Activity
 * events and photo links run after that and can never fail or stop the run.
 */
@Singleton
class OutboxReplayer @Inject constructor(
    private val database: RentMateDatabase,
    private val billsRemote: BillsRemoteSource,
    private val shoppingRemote: ShoppingRemoteSource,
    private val billsRepository: BillsRepository,
    private val shoppingRepository: ShoppingRepository,
    private val activity: ActivityLogger,
    private val attachments: AttachmentRepository,
    private val currentHousehold: CurrentHousehold,
    private val status: SyncStatusTracker
) {
    /** Replaced in tests. */
    internal var now: () -> Long = System::currentTimeMillis

    private val outboxDao get() = database.outboxDao()
    private val billDao get() = database.billDao()
    private val shoppingDao get() = database.shoppingItemDao()

    /**
     * Replays until the queue is empty or an entry must wait. The queue is read
     * again after each entry, so changes queued during the run are sent too.
     * Room failures are thrown; the worker treats them as a retry.
     */
    suspend fun run(): ReplayResult {
        status.started()
        currentHousehold.awaitRestored()
        val touched = mutableSetOf<String>()
        try {
            repeat(MAX_ENTRIES_PER_RUN) {
                val queue = outboxDao.getAllOldestFirst()
                val entry = queue.firstOrNull() ?: return finish(touched)
                val outcome = attempt(entry)
                val decision = ConflictRules.decide(entry, outcome)
                Log.d(TAG, "#${entry.id} ${entry.op} ${entry.tableName}: $outcome -> $decision")
                when (decision) {
                    is Decision.Applied -> {
                        val name = shoppingDao.getById(entry.entityId)?.name
                        applySuccess(entry)
                        touched += entry.tableName
                        followUp(entry, decision.serverId, name)
                    }
                    Decision.StopAndRetry -> return stop(touched, outcome.toString())
                    is Decision.RecordFailureAndRetry -> {
                        outboxDao.recordFailure(entry.id, decision.message)
                        return stop(touched, decision.message)
                    }
                    is Decision.Drop -> {
                        applyDrop(entry, decision, queue)
                        touched += entry.tableName
                    }
                }
            }
            // Very long queue: the rest goes in the next run.
            return stop(touched, "entry limit reached")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "replay failed locally", e)
            status.failed(e.message)
            throw e
        }
    }

    private suspend fun finish(touched: Set<String>): ReplayResult {
        refresh(touched)
        status.succeeded(now())
        return ReplayResult.DONE
    }

    private suspend fun stop(touched: Set<String>, reason: String?): ReplayResult {
        refresh(touched)
        status.failed(reason)
        return ReplayResult.RETRY
    }

    /** Sends one entry. Never throws except for cancellation. */
    private suspend fun attempt(entry: OutboxEntry): ReplayOutcome {
        val request = try {
            decode(entry)
        } catch (e: IllegalArgumentException) {
            // Includes SerializationException. Sending again cannot fix a bad payload.
            Log.e(TAG, "#${entry.id} has a payload that cannot be read", e)
            return ReplayOutcome.Rejected(AppError.GENERIC, "unreadable payload: ${e.message}")
        } ?: return ReplayOutcome.Rejected(AppError.GENERIC, "unknown entry ${entry.op} ${entry.tableName}")

        return try {
            when (request) {
                is ShoppingItemInsert -> ReplayOutcome.Success(shoppingRemote.insertItem(request))
                is ShoppingItemPurchase ->
                    if (shoppingRemote.replayPurchase(entry.entityId, request) == 0) ReplayOutcome.TargetMissing
                    else ReplayOutcome.Success(entry.entityId)
                is CreateBillParams -> ReplayOutcome.Success(sendBill(entry, request))
                else -> ReplayOutcome.Rejected(AppError.GENERIC, "unknown request")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "#${entry.id} replay failed", e)
            ConflictRules.outcomeOf(e)
        }
    }

    /** The request object stored in [entry], or null if this worker does not know the entry type. */
    private fun decode(entry: OutboxEntry): Any? = when {
        entry.op == OutboxOp.INSERT && entry.tableName == SHOPPING_ITEMS ->
            OutboxJson.decodeFromString(ShoppingItemInsert.serializer(), entry.payloadJson)
        entry.op == OutboxOp.UPDATE && entry.tableName == SHOPPING_ITEMS ->
            OutboxJson.decodeFromString(ShoppingItemPurchase.serializer(), entry.payloadJson)
        entry.op == OutboxOp.RPC && entry.tableName == CREATE_BILL ->
            OutboxJson.decodeFromString(CreateBillParams.serializer(), entry.payloadJson)
        else -> null
    }

    /**
     * Duplicate guard: create_bill is not idempotent, so before calling it the
     * worker asks the server for a bill in the same household with the same
     * issuer, title, amount and due date, created no earlier than
     * [DUPLICATE_GUARD_MARGIN] before the entry was queued. If there is one, an
     * earlier attempt reached the server (for example the app died before it
     * could delete the entry) and that bill is used instead of creating another.
     *
     * This is a heuristic. Two identical bills entered on purpose within that
     * window would be merged into one. The proper fix is a client-generated id
     * parameter on create_bill, which needs a Supabase migration.
     */
    private suspend fun sendBill(entry: OutboxEntry, params: CreateBillParams): String {
        val issuer = billDao.getBill(entry.entityId)?.issued_by_user_id
        val since = Instant.ofEpochMilli(entry.createdAt - DUPLICATE_GUARD_MARGIN.inWholeMilliseconds).toString()
        billsRemote.findMatchingBill(params, issuer, since)?.let { existing ->
            Log.d(TAG, "#${entry.id} bill already on the server as $existing, not creating it again")
            return existing
        }
        return billsRemote.createBill(params)
    }

    /** Deletes the entry and settles its local row, in one transaction. */
    private suspend fun applySuccess(entry: OutboxEntry) = database.withTransaction {
        outboxDao.deleteById(entry.id)
        when (entry.tableName) {
            SHOPPING_ITEMS -> {
                // A later change to the same item is still queued: the row stays pending,
                // so a refresh cannot overwrite it before that change is sent.
                val left = if (entry.entityId.isBlank()) 0 else outboxDao.countForEntity(entry.entityId)
                if (left > 0) shoppingDao.setSyncState(entry.entityId, SyncState.PENDING_UPDATE)
                else shoppingDao.markSynced(entry.entityId)
            }
            // The server made its own bill and share ids. Remove the local copy
            // (shares cascade); the refresh after the run brings the server copy.
            CREATE_BILL -> billDao.deleteBill(entry.entityId)
        }
    }

    /** Drops the entry and its dependants, changes the local row and records why, in one transaction. */
    private suspend fun applyDrop(entry: OutboxEntry, decision: Decision.Drop, queue: List<OutboxEntry>) {
        val dependants = ConflictRules.dependants(entry, queue)
        Log.e(TAG, "dropping #${entry.id} (${decision.reason}: ${decision.message}) and ${dependants.size} dependant(s)")
        database.withTransaction {
            val at = now()
            outboxDao.deleteById(entry.id)
            database.syncRejectionDao().insert(rejection(entry, decision.reason, decision.message, false, at))
            dependants.forEach {
                outboxDao.deleteById(it.id)
                database.syncRejectionDao().insert(rejection(it, DropReason.DEPENDS_ON_DROPPED, null, true, at))
            }
            if (entry.entityId.isBlank()) return@withTransaction
            when (entry.tableName) {
                SHOPPING_ITEMS -> when (decision.local) {
                    LocalChange.DELETE_ROW -> shoppingDao.delete(entry.entityId)
                    LocalChange.REVERT_ROW -> shoppingDao.markSynced(entry.entityId)
                }
                CREATE_BILL -> billDao.deleteBill(entry.entityId)
            }
        }
    }

    private fun rejection(entry: OutboxEntry, reason: DropReason, message: String?, dependant: Boolean, at: Long) =
        SyncRejectionEntity(
            op = entry.op, tableName = entry.tableName, entityId = entry.entityId,
            reason = reason.name, message = message, dependant = dependant, rejectedAt = at
        )

    /**
     * Posts the activity event the screen skipped while offline and links queued
     * bill photos. Skipped when the entry belongs to another household than the
     * current one, since both the logger and the link write to the current one.
     */
    private suspend fun followUp(entry: OutboxEntry, serverId: String?, itemName: String?) {
        try {
            val current = currentHousehold.id.value
            when (val request = decode(entry)) {
                is ShoppingItemInsert ->
                    if (request.household_id == current) activity.shoppingAdded(request.name, serverId ?: entry.entityId)
                    else Log.d(TAG, "#${entry.id} not in the current household, no event")
                is ShoppingItemPurchase ->
                    if (itemName != null) activity.shoppingBought(itemName, entry.entityId)
                    else Log.d(TAG, "#${entry.id} item not cached, no event")
                is CreateBillParams -> {
                    if (request.household_id_in != current || serverId == null) {
                        Log.d(TAG, "#${entry.id} not in the current household, no event or photo link")
                        return
                    }
                    activity.billAdded(request.title_in, request.amount_in, serverId)
                    linkPhotos(serverId, entry.attachmentPaths)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "#${entry.id} uploaded, but its follow-up failed", e)
        }
    }

    /**
     * Links photos to the server bill. Photos no longer in Storage are logged and
     * skipped. If the existence check itself fails, every path is tried.
     */
    private suspend fun linkPhotos(billId: String, paths: List<String>) {
        if (paths.isEmpty()) return
        val present = try {
            attachments.signedUrls(paths, PHOTO_CHECK_EXPIRY).keys
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not check photos for bill $billId, linking all", e)
            paths.toSet()
        }
        val (keep, gone) = paths.partition { it in present }
        gone.forEach { Log.e(TAG, "photo $it for bill $billId is no longer in Storage, skipping it") }
        try {
            attachments.link(AttachmentParent.Bill(billId), keep)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // One bad path (for example already linked) fails the whole insert, so try each one.
            Log.w(TAG, "linking ${keep.size} photo(s) to bill $billId failed, trying one by one", e)
            keep.forEach { path ->
                try {
                    attachments.link(AttachmentParent.Bill(billId), listOf(path))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "could not link photo $path to bill $billId, skipping it", e)
                }
            }
        }
    }

    /** Refreshes the caches of the tables this run changed. A failure only means they update later. */
    private suspend fun refresh(touched: Set<String>) {
        if (CREATE_BILL in touched) refreshQuietly("bills") { billsRepository.refresh() }
        if (SHOPPING_ITEMS in touched) refreshQuietly("shopping") { shoppingRepository.refresh() }
    }

    private suspend fun refreshQuietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what refresh after sync failed", e)
        }
    }

    companion object {
        /** How far before an entry's enqueue time the duplicate guard looks, to allow for clock skew. */
        val DUPLICATE_GUARD_MARGIN = 10.minutes

        /** Upper bound on entries per run, so a bug can never loop forever. */
        const val MAX_ENTRIES_PER_RUN = 500

        private val PHOTO_CHECK_EXPIRY = 1.minutes
    }
}
