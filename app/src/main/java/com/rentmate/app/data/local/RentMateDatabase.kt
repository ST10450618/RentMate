package com.rentmate.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local offline cache of household data plus the sync outbox.
 *
 * Schemas are exported to app/schemas. No destructive-migration fallback: the
 * cache holds unsynced offline edits, so a future schema change must ship a
 * real Migration rather than silently wipe them. v2 (RM-0016) stores money as
 * integer cents and adds the bill repeat columns; see [MIGRATION_1_2]. v3
 * (RM-0017) adds outbox replay fields and the member roster; see [MIGRATION_2_3].
 */
@Database(
    entities = [
        BillEntity::class,
        BillShareEntity::class,
        ChoreEntity::class,
        ShoppingItemEntity::class,
        OutboxEntry::class,
        HouseholdMemberEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RentMateDatabase : RoomDatabase() {
    /** DAO for bills and shares. */
    abstract fun billDao(): BillDao

    /** DAO for chores. */
    abstract fun choreDao(): ChoreDao

    /** DAO for shopping items. */
    abstract fun shoppingItemDao(): ShoppingItemDao

    /** DAO for the outbox queue. */
    abstract fun outboxDao(): OutboxDao

    /** DAO for the cached household roster. */
    abstract fun memberDao(): MemberDao
}
