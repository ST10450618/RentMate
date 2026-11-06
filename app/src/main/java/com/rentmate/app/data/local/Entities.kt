package com.rentmate.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local copy of a row in the Supabase `bills` table.
 *
 * Money is stored as whole cents ([amount_cents]) so sums never drift the way
 * Double arithmetic can. Convert to Rand only when mapping to a UI row.
 */
@Entity(tableName = "bills", indices = [Index("household_id")])
data class BillEntity(
    @PrimaryKey val id: String,
    val household_id: String,
    val title: String,
    val amount_cents: Long,
    val due_date: String,
    val split_method: String,
    val issued_by_user_id: String,
    val created_at: String,
    /** Server `updated_at` text, as received. */
    val server_updated_at: String,
    val syncState: SyncState = SyncState.SYNCED,
    /** Local last-modified time, epoch millis. */
    val updatedAt: Long = 0L,
    /** RM-0049 repeat mode as sent by the server ("Never", "Weekly", "Monthly"). */
    val repeat: String = "Never",
    /** RM-0049: true once the issuer stopped the bill repeating. */
    val repeat_stopped: Boolean = false
)

/**
 * Local copy of a `bill_shares` row. [household_id] is denormalised from the
 * parent bill so shares can be scoped per household. Deleted with its bill.
 */
@Entity(
    tableName = "bill_shares",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bill_id"), Index("household_id")]
)
data class BillShareEntity(
    @PrimaryKey val id: String,
    val bill_id: String,
    val household_id: String,
    val user_id: String,
    /** This housemate's share in whole cents. */
    val amount_owed_cents: Long,
    val is_paid: Boolean,
    val paid_at: String? = null,
    /** Joined `profiles.display_name` of the debtor, if known. */
    val display_name: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = 0L
)

/** Local copy of a `chores` row; [rotation_order] is stored as JSON. */
@Entity(tableName = "chores", indices = [Index("household_id")])
data class ChoreEntity(
    @PrimaryKey val id: String,
    val household_id: String,
    val title: String,
    val recurrence: String,
    val point_value: Int,
    val rotation_order: List<String>,
    val current_rotation_index: Int,
    val created_at: String,
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = 0L
)

/** Local copy of a `shopping_items` row (user references flattened to names). */
@Entity(tableName = "shopping_items", indices = [Index("household_id")])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val household_id: String,
    val name: String,
    val added_by_name: String? = null,
    val is_purchased: Boolean,
    val purchased_by_name: String? = null,
    val purchased_at: String? = null,
    val converted_to_bill_id: String? = null,
    val created_at: String,
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = 0L
)

/**
 * Operation names stored in [OutboxEntry.op]. RM-0018's sync worker switches on
 * these to decide how to replay an entry.
 */
object OutboxOp {
    /** Insert [OutboxEntry.payloadJson] into the table [OutboxEntry.tableName]. */
    const val INSERT = "INSERT"

    /**
     * Update the row with id [OutboxEntry.entityId] in [OutboxEntry.tableName],
     * setting the columns in [OutboxEntry.payloadJson].
     */
    const val UPDATE = "UPDATE"

    /** Call the Postgres function [OutboxEntry.tableName] with [OutboxEntry.payloadJson] as its params. */
    const val RPC = "RPC"
}

/**
 * A queued offline mutation awaiting upload (RM-0017). The local row it belongs
 * to is written in the same Room transaction, so a pending row never exists
 * without its entry. RM-0018's worker replays entries oldest first and deletes
 * each one once the server accepted it.
 */
@Entity(tableName = "outbox")
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** One of [OutboxOp]. */
    val op: String,
    /** Target Supabase table, or the RPC function name when [op] is [OutboxOp.RPC]. */
    val tableName: String,
    /** JSON body: the exact insert row, update columns or RPC params that were sent online. */
    val payloadJson: String,
    /** Enqueue time, epoch millis. */
    val createdAt: Long,
    /** Id of the local row this change belongs to (client-generated UUID for creates). */
    @ColumnInfo(defaultValue = "''")
    val entityId: String = "",
    /** How many replays failed so far. RM-0018 increments it. */
    @ColumnInfo(defaultValue = "0")
    val attemptCount: Int = 0,
    /** Message of the last failed replay, if any. */
    val lastError: String? = null
)

/**
 * Cached household roster (RM-0017), so Add Bill can list who to split with and
 * name the offline shares when there is no connection.
 */
@Entity(tableName = "household_members", primaryKeys = ["household_id", "user_id"])
data class HouseholdMemberEntity(
    val household_id: String,
    val user_id: String,
    val display_name: String? = null
)
