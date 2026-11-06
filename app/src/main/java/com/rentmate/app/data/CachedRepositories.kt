package com.rentmate.app.data

import android.util.Log
import com.rentmate.app.data.local.BillDao
import com.rentmate.app.data.local.BillEntity
import com.rentmate.app.data.local.BillShareEntity
import com.rentmate.app.data.local.ChoreDao
import com.rentmate.app.data.local.HouseholdMemberEntity
import com.rentmate.app.data.local.MemberDao
import com.rentmate.app.data.local.OutboxEntry
import com.rentmate.app.data.local.OutboxOp
import com.rentmate.app.data.local.OutboxQueue
import com.rentmate.app.data.local.ShoppingItemDao
import com.rentmate.app.data.local.ShoppingItemEntity
import com.rentmate.app.data.local.SyncState
import com.rentmate.app.data.local.toEntity
import com.rentmate.app.data.local.toRow
import com.rentmate.app.data.local.toShareEntities
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CachedRepositories"

/** RM-0017: what happened to a write. [id] is the server id, or the local UUID while queued. */
sealed interface WriteResult {
    /** Id of the row the write created or changed. */
    val id: String

    /** Supabase accepted the write. */
    data class Sent(override val id: String) : WriteResult

    /** No connection: the change is stored locally with an outbox entry that SyncWorker uploads (RM-0018). */
    data class Queued(override val id: String) : WriteResult
}

/**
 * Tries [send]. Only when it fails because there is no connection, runs [queue]
 * instead. Any other failure (403, validation, 409) is rethrown and nothing is
 * queued, so the caller shows the error as before.
 */
private suspend fun sendOrQueue(what: String, send: suspend () -> String, queue: suspend () -> String): WriteResult {
    val id = try {
        send()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (ErrorMapper.classify(e) != AppError.NO_CONNECTION) throw e
        Log.d(TAG, "$what: no connection, queueing (${e.javaClass.simpleName})")
        return WriteResult.Queued(queue())
    }
    return WriteResult.Sent(id)
}

/** Runs a refresh after a successful write; a failure only means the cache updates later. */
private suspend fun refreshQuietly(what: String, refresh: suspend () -> Unit) {
    try {
        refresh()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "$what saved but the refresh failed", e)
    }
}

/**
 * A new bill as entered on Add Bill (US-6). [shares] come from BillSplit.compute;
 * only their user ids and raw values are used, the cents are computed again here.
 */
data class NewBill(
    val householdId: String,
    val title: String,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val mode: SplitMode,
    val shares: List<SplitShare>,
    val repeat: RepeatMode,
    val issuedByUserId: String,
    /**
     * RM-0018: Storage paths of photos already uploaded for this bill. Online the
     * caller links them; when the bill is queued, the outbox keeps them and the
     * sync worker links them once the bill exists on the server.
     */
    val photoPaths: List<String> = emptyList()
)

/**
 * RM-0016: bills read from Room. [bills] always shows the cache for the current
 * household; [refresh] pulls from Supabase and writes through the DAO. A failed
 * refresh throws and leaves the cache (and so the screen) as it was.
 */
interface BillsRepository {
    /** Cached bills (with shares) of the current household, newest due date first. */
    val bills: Flow<List<BillWithSharesRow>>

    /** RM-0017: cached members of the current household, so Add Bill works offline. */
    val members: Flow<List<HouseholdMemberRow>>

    /** Reloads the current household's bills and members from the server into the cache. */
    suspend fun refresh()

    /** Reloads only the current household's members. */
    suspend fun refreshMembers()

    /**
     * RM-0017: creates a bill with create_bill. Offline, stores the bill and its
     * shares as PENDING_CREATE plus an outbox entry and returns [WriteResult.Queued].
     * Other server errors are thrown.
     */
    suspend fun createBill(bill: NewBill): WriteResult
}

/** RM-0016: chores read from Room; see [BillsRepository] for the contract. */
interface ChoresRepository {
    /** Cached chores of the current household, oldest first. */
    val chores: Flow<List<ChoreRow>>

    /** Reloads the current household's chores from the server into the cache. */
    suspend fun refresh()
}

/** RM-0016: shopping items read from Room; see [BillsRepository] for the contract. */
interface ShoppingRepository {
    /** Cached shopping items of the current household, oldest first. */
    val items: Flow<List<ShoppingItemRow>>

    /** Reloads the current household's items from the server into the cache. */
    suspend fun refresh()

    /**
     * RM-0017: adds an item to [householdId]'s list. Offline, stores it as
     * PENDING_CREATE plus an outbox entry. Other server errors are thrown.
     */
    suspend fun addItem(householdId: String, name: String, addedByUserId: String): WriteResult

    /**
     * RM-0017: marks item [id] purchased by [userId]. Offline, or while the item
     * still has a change waiting in the outbox, the change is queued instead.
     */
    suspend fun markPurchased(id: String, userId: String): WriteResult
}

/** [BillsRepository] backed by Room and a [BillsRemoteSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CachedBillsRepository @Inject constructor(
    private val remote: BillsRemoteSource,
    private val dao: BillDao,
    private val memberDao: MemberDao,
    private val outbox: OutboxQueue,
    private val currentHousehold: CurrentHousehold
) : BillsRepository {

    override val bills: Flow<List<BillWithSharesRow>> = currentHousehold.id.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else dao.observeBills(householdId).map { list -> list.map { it.toRow() } }
    }

    override val members: Flow<List<HouseholdMemberRow>> = currentHousehold.id.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else memberDao.observeMembers(householdId).map { list -> list.map { HouseholdMemberRow(it.user_id, it.display_name) } }
    }

    override suspend fun refresh() {
        currentHousehold.awaitRestored()
        val householdId = currentHousehold.id.value ?: return
        val rows = remote.fetchBills(householdId)
        // replaceForHousehold keeps PENDING_* bills and shares (offline edits).
        dao.replaceForHousehold(householdId, rows.map { it.toEntity() }, rows.flatMap { it.toShareEntities() })
        Log.d(TAG, "bills refreshed: ${rows.size}")
        refreshMembers()
    }

    override suspend fun refreshMembers() {
        currentHousehold.awaitRestored()
        val householdId = currentHousehold.id.value ?: return
        val rows = remote.fetchMembers(householdId)
        memberDao.replaceForHousehold(householdId, rows.map { HouseholdMemberEntity(householdId, it.user_id, it.display_name) })
        Log.d(TAG, "members refreshed: ${rows.size}")
    }

    override suspend fun createBill(bill: NewBill): WriteResult {
        // Same rules as create_bill (BillSplit mirrors it), so offline shares match what the server will store.
        val split = BillSplit.compute(
            bill.mode, bill.amount, bill.shares.map { it.userId },
            bill.shares.associate { it.userId to it.value.toPlainString() }
        )
        val shares = (split as? SplitResult.Valid)?.shares
            ?: throw IllegalArgumentException("Bill split is not valid: $split")
        val params = CreateBillParams(
            household_id_in = bill.householdId,
            title_in = bill.title,
            amount_in = bill.amount.toDouble(),
            due_date_in = "${bill.dueDate}T00:00:00Z",
            split_method_in = bill.mode.wire,
            shares_in = shares.map { CreateBillShareParam(it.userId, it.value.toDouble()) },
            repeat_in = bill.repeat.wire
        )
        val result = sendOrQueue(
            "create_bill",
            send = { remote.createBill(params) },
            queue = { queueBill(bill, params, shares) }
        )
        if (result is WriteResult.Sent) refreshQuietly("bill") { refresh() }
        return result
    }

    private suspend fun queueBill(bill: NewBill, params: CreateBillParams, shares: List<SplitShare>): String {
        val now = System.currentTimeMillis()
        val billId = UUID.randomUUID().toString()
        val entity = BillEntity(
            id = billId,
            household_id = bill.householdId,
            title = bill.title,
            amount_cents = bill.amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(),
            due_date = params.due_date_in,
            split_method = params.split_method_in,
            issued_by_user_id = bill.issuedByUserId,
            created_at = Instant.ofEpochMilli(now).toString(),
            server_updated_at = "",
            syncState = SyncState.PENDING_CREATE,
            updatedAt = now,
            repeat = params.repeat_in
        )
        val shareEntities = shares.map { share ->
            BillShareEntity(
                id = UUID.randomUUID().toString(),
                bill_id = billId,
                household_id = bill.householdId,
                user_id = share.userId,
                amount_owed_cents = share.cents,
                is_paid = false,
                display_name = memberDao.getMember(bill.householdId, share.userId)?.display_name,
                syncState = SyncState.PENDING_CREATE,
                updatedAt = now
            )
        }
        val entry = OutboxEntry(
            op = OutboxOp.RPC,
            tableName = "create_bill",
            payloadJson = OutboxJson.encodeToString(CreateBillParams.serializer(), params),
            createdAt = now,
            entityId = billId,
            attachmentPaths = bill.photoPaths
        )
        outbox.enqueue(entry) {
            dao.upsertBills(listOf(entity))
            dao.upsertShares(shareEntities)
        }
        return billId
    }
}

/** [ChoresRepository] backed by [ChoreDao] and a [ChoresRemoteSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CachedChoresRepository @Inject constructor(
    private val remote: ChoresRemoteSource,
    private val dao: ChoreDao,
    private val currentHousehold: CurrentHousehold
) : ChoresRepository {

    override val chores: Flow<List<ChoreRow>> = currentHousehold.id.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else dao.observeChores(householdId).map { list -> list.map { it.toRow() } }
    }

    override suspend fun refresh() {
        currentHousehold.awaitRestored()
        val householdId = currentHousehold.id.value ?: return
        val rows = remote.fetchChores(householdId)
        dao.replaceForHousehold(householdId, rows.map { it.toEntity() })
        Log.d(TAG, "chores refreshed: ${rows.size}")
    }
}

/** [ShoppingRepository] backed by Room and a [ShoppingRemoteSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CachedShoppingRepository @Inject constructor(
    private val remote: ShoppingRemoteSource,
    private val dao: ShoppingItemDao,
    private val memberDao: MemberDao,
    private val outbox: OutboxQueue,
    private val currentHousehold: CurrentHousehold
) : ShoppingRepository {

    override val items: Flow<List<ShoppingItemRow>> = currentHousehold.id.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else dao.observeItems(householdId).map { list -> list.map { it.toRow() } }
    }

    override suspend fun refresh() {
        currentHousehold.awaitRestored()
        val householdId = currentHousehold.id.value ?: return
        val rows = remote.fetchItems(householdId)
        dao.replaceForHousehold(householdId, rows.map { it.toEntity() })
        Log.d(TAG, "shopping items refreshed: ${rows.size}")
    }

    override suspend fun addItem(householdId: String, name: String, addedByUserId: String): WriteResult {
        val insert = ShoppingItemInsert(
            id = UUID.randomUUID().toString(),
            household_id = householdId,
            name = name.trim(),
            added_by_user_id = addedByUserId
        )
        val result = sendOrQueue(
            "shopping insert",
            send = { remote.insertItem(insert) },
            queue = { queueInsert(insert) }
        )
        if (result is WriteResult.Sent) refreshQuietly("shopping item") { refresh() }
        return result
    }

    private suspend fun queueInsert(insert: ShoppingItemInsert): String {
        val now = System.currentTimeMillis()
        val entity = ShoppingItemEntity(
            id = insert.id,
            household_id = insert.household_id,
            name = insert.name,
            added_by_name = memberDao.getMember(insert.household_id, insert.added_by_user_id)?.display_name,
            is_purchased = false,
            created_at = Instant.ofEpochMilli(now).toString(),
            syncState = SyncState.PENDING_CREATE,
            updatedAt = now
        )
        val entry = OutboxEntry(
            op = OutboxOp.INSERT,
            tableName = "shopping_items",
            payloadJson = OutboxJson.encodeToString(ShoppingItemInsert.serializer(), insert),
            createdAt = now,
            entityId = insert.id
        )
        outbox.enqueue(entry) { dao.upsert(listOf(entity)) }
        return insert.id
    }

    override suspend fun markPurchased(id: String, userId: String): WriteResult {
        val update = ShoppingItemPurchase(purchased_by_user_id = userId, purchased_at = Instant.now().toString())
        val local = dao.getById(id)
        if (local != null && local.syncState != SyncState.SYNCED) {
            // The item (or an earlier edit) is not on the server yet. Sending now would
            // update nothing or jump the queue, so this change waits behind it.
            Log.d(TAG, "item $id has a pending change, queueing purchase")
            return WriteResult.Queued(queuePurchase(id, local, update))
        }
        val result = sendOrQueue(
            "shopping purchase",
            send = { remote.markPurchased(id, update); id },
            queue = { queuePurchase(id, local, update) }
        )
        if (result is WriteResult.Sent) refreshQuietly("purchase") { refresh() }
        return result
    }

    private suspend fun queuePurchase(id: String, local: ShoppingItemEntity?, update: ShoppingItemPurchase): String {
        val now = System.currentTimeMillis()
        val entry = OutboxEntry(
            op = OutboxOp.UPDATE,
            tableName = "shopping_items",
            payloadJson = OutboxJson.encodeToString(ShoppingItemPurchase.serializer(), update),
            createdAt = now,
            entityId = id
        )
        val updated = local?.copy(
            is_purchased = true,
            purchased_by_name = memberDao.getMember(local.household_id, update.purchased_by_user_id)?.display_name,
            purchased_at = update.purchased_at,
            // Still a create if the item itself never reached the server.
            syncState = if (local.syncState == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE else SyncState.PENDING_UPDATE,
            updatedAt = now
        )
        outbox.enqueue(entry) { updated?.let { dao.upsert(listOf(it)) } }
        return id
    }
}
