package com.rentmate.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local copy of a row in the Supabase `bills` table. */
@Entity(tableName = "bills", indices = [Index("household_id")])
data class BillEntity(
    @PrimaryKey val id: String,
    val household_id: String,
    val title: String,
    val amount: Double,
    val due_date: String,
    val split_method: String,
    val issued_by_user_id: String,
    val created_at: String,
    /** Server `updated_at` text, as received. */
    val server_updated_at: String,
    val syncState: SyncState = SyncState.SYNCED,
    /** Local last-modified time, epoch millis. */
    val updatedAt: Long = 0L
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
    val amount_owed: Double,
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

/** A queued offline mutation awaiting upload (consumed by the RM-0017 sync worker). */
@Entity(tableName = "outbox")
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Operation, e.g. "INSERT", "UPDATE", "DELETE". */
    val op: String,
    /** Target Supabase table name. */
    val tableName: String,
    /** JSON body of the mutation. */
    val payloadJson: String,
    /** Enqueue time, epoch millis. */
    val createdAt: Long
)
