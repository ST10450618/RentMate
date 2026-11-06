package com.rentmate.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A bill together with its shares. */
data class BillWithShares(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "id", entityColumn = "bill_id")
    val shares: List<BillShareEntity>
)

/** Access to cached bills and their shares. Reads hide PENDING_DELETE rows. */
@Dao
abstract class BillDao {

    /** Observes a household's bills (newest due date first) with shares. */
    @Transaction
    @Query(
        "SELECT * FROM bills WHERE household_id = :householdId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY due_date DESC"
    )
    abstract fun observeBills(householdId: String): Flow<List<BillWithShares>>

    /** Inserts or replaces bills. */
    @Upsert
    abstract suspend fun upsertBills(bills: List<BillEntity>)

    /** Inserts or replaces bill shares. */
    @Upsert
    abstract suspend fun upsertShares(shares: List<BillShareEntity>)

    /** Bills in a household that are not SYNCED. */
    @Query("SELECT * FROM bills WHERE household_id = :householdId AND syncState != 'SYNCED'")
    abstract suspend fun getUnsyncedBills(householdId: String): List<BillEntity>

    /** Shares in a household that are not SYNCED. */
    @Query("SELECT * FROM bill_shares WHERE household_id = :householdId AND syncState != 'SYNCED'")
    abstract suspend fun getUnsyncedShares(householdId: String): List<BillShareEntity>

    /** Sets the sync state of one bill. */
    @Query("UPDATE bills SET syncState = :state WHERE id = :id")
    abstract suspend fun setBillSyncState(id: String, state: SyncState)

    /** Sets the sync state of one bill share. */
    @Query("UPDATE bill_shares SET syncState = :state WHERE id = :id")
    abstract suspend fun setShareSyncState(id: String, state: SyncState)

    /** Marks a bill SYNCED. */
    @Query("UPDATE bills SET syncState = 'SYNCED' WHERE id = :id")
    abstract suspend fun markBillSynced(id: String)

    /** Marks a bill share SYNCED. */
    @Query("UPDATE bill_shares SET syncState = 'SYNCED' WHERE id = :id")
    abstract suspend fun markShareSynced(id: String)

    /** One bill by id, or null. */
    @Query("SELECT * FROM bills WHERE id = :id")
    abstract suspend fun getBill(id: String): BillEntity?

    /** Deletes a bill (its shares cascade). */
    @Query("DELETE FROM bills WHERE id = :id")
    abstract suspend fun deleteBill(id: String)

    /** Deletes one bill share. */
    @Query("DELETE FROM bill_shares WHERE id = :id")
    abstract suspend fun deleteShare(id: String)

    /**
     * Deletes a household's SYNCED bills (shares cascade), except bills that still
     * have a non-SYNCED share, so the cascade can never remove an offline edit.
     * Prefer [replaceForHousehold].
     */
    @Query(
        "DELETE FROM bills WHERE household_id = :householdId AND syncState = 'SYNCED' " +
            "AND id NOT IN (SELECT bill_id FROM bill_shares WHERE syncState != 'SYNCED')"
    )
    abstract suspend fun clearSyncedBills(householdId: String)

    /** Deletes SYNCED shares that belong to SYNCED bills of the household. */
    @Query(
        "DELETE FROM bill_shares WHERE household_id = :householdId AND syncState = 'SYNCED' " +
            "AND bill_id IN (SELECT id FROM bills WHERE syncState = 'SYNCED')"
    )
    abstract suspend fun clearSyncedShares(householdId: String)

    /**
     * Replaces the household's SYNCED data with fresh server data without losing
     * offline edits. Pending bills and pending shares are never deleted or
     * overwritten. A SYNCED bill that still has a pending share is kept and
     * refreshed in place by the upsert (no delete, so no FK cascade). Stale SYNCED
     * shares of SYNCED bills are removed; server shares are then inserted unless
     * they match a pending share or belong to a pending bill.
     */
    @Transaction
    open suspend fun replaceForHousehold(
        householdId: String,
        bills: List<BillEntity>,
        shares: List<BillShareEntity>
    ) {
        clearSyncedBills(householdId)
        clearSyncedShares(householdId)
        val pendingBills = getUnsyncedBills(householdId).map { it.id }.toSet()
        val pendingShares = getUnsyncedShares(householdId).map { it.id }.toSet()
        upsertBills(bills.filter { it.id !in pendingBills })
        upsertShares(shares.filter { it.bill_id !in pendingBills && it.id !in pendingShares })
    }
}

/** Access to cached chores. */
@Dao
abstract class ChoreDao {

    /** Observes a household's chores, hiding PENDING_DELETE rows. */
    @Query(
        "SELECT * FROM chores WHERE household_id = :householdId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY created_at"
    )
    abstract fun observeChores(householdId: String): Flow<List<ChoreEntity>>

    /** Inserts or replaces chores. */
    @Upsert
    abstract suspend fun upsert(chores: List<ChoreEntity>)

    /** Chores in a household that are not SYNCED. */
    @Query("SELECT * FROM chores WHERE household_id = :householdId AND syncState != 'SYNCED'")
    abstract suspend fun getUnsynced(householdId: String): List<ChoreEntity>

    /** Sets the sync state of one chore. */
    @Query("UPDATE chores SET syncState = :state WHERE id = :id")
    abstract suspend fun setSyncState(id: String, state: SyncState)

    /** Marks a chore SYNCED. */
    @Query("UPDATE chores SET syncState = 'SYNCED' WHERE id = :id")
    abstract suspend fun markSynced(id: String)

    /** Deletes a chore. */
    @Query("DELETE FROM chores WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** Deletes a household's SYNCED chores. Prefer [replaceForHousehold]. */
    @Query("DELETE FROM chores WHERE household_id = :householdId AND syncState = 'SYNCED'")
    abstract suspend fun clearSynced(householdId: String)

    /** Replaces SYNCED chores of the household with [chores], keeping PENDING_* rows. */
    @Transaction
    open suspend fun replaceForHousehold(householdId: String, chores: List<ChoreEntity>) {
        clearSynced(householdId)
        val pending = getUnsynced(householdId).map { it.id }.toSet()
        upsert(chores.filter { it.id !in pending })
    }
}

/** Access to cached shopping items. */
@Dao
abstract class ShoppingItemDao {

    /** Observes a household's shopping items, hiding PENDING_DELETE rows. */
    @Query(
        "SELECT * FROM shopping_items WHERE household_id = :householdId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY created_at"
    )
    abstract fun observeItems(householdId: String): Flow<List<ShoppingItemEntity>>

    /** Inserts or replaces shopping items. */
    @Upsert
    abstract suspend fun upsert(items: List<ShoppingItemEntity>)

    /** Items in a household that are not SYNCED. */
    @Query("SELECT * FROM shopping_items WHERE household_id = :householdId AND syncState != 'SYNCED'")
    abstract suspend fun getUnsynced(householdId: String): List<ShoppingItemEntity>

    /** Sets the sync state of one item. */
    @Query("UPDATE shopping_items SET syncState = :state WHERE id = :id")
    abstract suspend fun setSyncState(id: String, state: SyncState)

    /** Marks an item SYNCED. */
    @Query("UPDATE shopping_items SET syncState = 'SYNCED' WHERE id = :id")
    abstract suspend fun markSynced(id: String)

    /** One item by id, or null. */
    @Query("SELECT * FROM shopping_items WHERE id = :id")
    abstract suspend fun getById(id: String): ShoppingItemEntity?

    /** Deletes an item. */
    @Query("DELETE FROM shopping_items WHERE id = :id")
    abstract suspend fun delete(id: String)

    /** Deletes a household's SYNCED items. Prefer [replaceForHousehold]. */
    @Query("DELETE FROM shopping_items WHERE household_id = :householdId AND syncState = 'SYNCED'")
    abstract suspend fun clearSynced(householdId: String)

    /** Replaces SYNCED items of the household with [items], keeping PENDING_* rows. */
    @Transaction
    open suspend fun replaceForHousehold(householdId: String, items: List<ShoppingItemEntity>) {
        clearSynced(householdId)
        val pending = getUnsynced(householdId).map { it.id }.toSet()
        upsert(items.filter { it.id !in pending })
    }
}

/** Queue of offline mutations for the sync worker (RM-0018). */
@Dao
interface OutboxDao {

    /** Adds an entry to the queue and returns its generated id. */
    @Insert
    suspend fun enqueue(entry: OutboxEntry): Long

    /**
     * Pending entries, oldest first. Every row in the outbox is pending: an entry
     * is deleted as soon as the server accepted it. Replay in this order.
     */
    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOldestFirst(): List<OutboxEntry>

    /** Number of pending entries, updated live (RM-0019 shows it). */
    @Query("SELECT COUNT(*) FROM outbox")
    fun observePendingCount(): Flow<Int>

    /** Records a failed replay of entry [id]: bumps its attempt count and keeps [error]. */
    @Query("UPDATE outbox SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, error: String?)

    /** Removes the entry with [id] (after it was uploaded). */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** RM-0018: number of entries still queued for the local row [entityId]. */
    @Query("SELECT COUNT(*) FROM outbox WHERE entityId = :entityId")
    suspend fun countForEntity(entityId: String): Int

    /** RM-0018: number of pending entries right now (not a Flow). */
    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int
}

/** RM-0018: changes the server refused for good (RM-0019 reads them). */
@Dao
interface SyncRejectionDao {

    /** Records a dropped change. */
    @Insert
    suspend fun insert(rejection: SyncRejectionEntity): Long

    /** Every recorded rejection, newest first, updated live. */
    @Query("SELECT * FROM sync_rejections ORDER BY rejectedAt DESC, id DESC")
    fun observeAll(): Flow<List<SyncRejectionEntity>>

    /** Removes one rejection, e.g. after the user saw it. */
    @Query("DELETE FROM sync_rejections WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** RM-0019: removes the rejections the user has seen, so each one is shown once. */
    @Query("DELETE FROM sync_rejections WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Removes every rejection. */
    @Query("DELETE FROM sync_rejections")
    suspend fun clear()
}

/** Access to the cached household roster (RM-0017). */
@Dao
abstract class MemberDao {

    /** Observes the members of a household. */
    @Query("SELECT * FROM household_members WHERE household_id = :householdId ORDER BY display_name, user_id")
    abstract fun observeMembers(householdId: String): Flow<List<HouseholdMemberEntity>>

    /** One member of a household, or null if not cached. */
    @Query("SELECT * FROM household_members WHERE household_id = :householdId AND user_id = :userId")
    abstract suspend fun getMember(householdId: String, userId: String): HouseholdMemberEntity?

    /** Inserts or replaces members. */
    @Upsert
    abstract suspend fun upsert(members: List<HouseholdMemberEntity>)

    /** Deletes every cached member of a household. */
    @Query("DELETE FROM household_members WHERE household_id = :householdId")
    abstract suspend fun clearHousehold(householdId: String)

    /** Replaces the household's roster with [members]. */
    @Transaction
    open suspend fun replaceForHousehold(householdId: String, members: List<HouseholdMemberEntity>) {
        clearHousehold(householdId)
        upsert(members)
    }
}
