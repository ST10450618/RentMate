package com.rentmate.app.data

import android.util.Log
import com.rentmate.app.data.local.BillDao
import com.rentmate.app.data.local.ChoreDao
import com.rentmate.app.data.local.ShoppingItemDao
import com.rentmate.app.data.local.toEntity
import com.rentmate.app.data.local.toRow
import com.rentmate.app.data.local.toShareEntities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CachedRepositories"

/**
 * RM-0016: bills read from Room. [bills] always shows the cache for the current
 * household; [refresh] pulls from Supabase and writes through the DAO. A failed
 * refresh throws and leaves the cache (and so the screen) as it was.
 */
interface BillsRepository {
    /** Cached bills (with shares) of the current household, newest due date first. */
    val bills: Flow<List<BillWithSharesRow>>

    /** Reloads the current household's bills from the server into the cache. */
    suspend fun refresh()
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
}

/** [BillsRepository] backed by [BillDao] and a [BillsRemoteSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CachedBillsRepository @Inject constructor(
    private val remote: BillsRemoteSource,
    private val dao: BillDao,
    private val currentHousehold: CurrentHousehold
) : BillsRepository {

    override val bills: Flow<List<BillWithSharesRow>> = currentHousehold.id.flatMapLatest { householdId ->
        if (householdId == null) flowOf(emptyList())
        else dao.observeBills(householdId).map { list -> list.map { it.toRow() } }
    }

    override suspend fun refresh() {
        currentHousehold.awaitRestored()
        val householdId = currentHousehold.id.value ?: return
        val rows = remote.fetchBills(householdId)
        // replaceForHousehold keeps PENDING_* bills and shares (offline edits).
        dao.replaceForHousehold(householdId, rows.map { it.toEntity() }, rows.flatMap { it.toShareEntities() })
        Log.d(TAG, "bills refreshed: ${rows.size}")
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

/** [ShoppingRepository] backed by [ShoppingItemDao] and a [ShoppingRemoteSource]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CachedShoppingRepository @Inject constructor(
    private val remote: ShoppingRemoteSource,
    private val dao: ShoppingItemDao,
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
}
