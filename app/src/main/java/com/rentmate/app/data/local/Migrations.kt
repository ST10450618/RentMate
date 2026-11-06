package com.rentmate.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (RM-0016): money moves from REAL Rand to INTEGER cents, and bills get
 * the RM-0049 repeat columns.
 *
 * SQLite cannot change a column type in place, so both bill tables are rebuilt.
 * The old tables are renamed out of the way first and dropped child-first at the
 * end. That order means dropping an old `bills` row can never cascade into the
 * new `bill_shares` table, so offline edits (any syncState) are kept as they are.
 * `ROUND(x * 100)` turns 123.45 (stored as 123.4499...) into 12345, not 12344.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bills` RENAME TO `bills_v1`")
        db.execSQL("ALTER TABLE `bill_shares` RENAME TO `bill_shares_v1`")
        // The renamed tables keep their index names, which the new tables need.
        db.execSQL("DROP INDEX IF EXISTS `index_bills_household_id`")
        db.execSQL("DROP INDEX IF EXISTS `index_bill_shares_bill_id`")
        db.execSQL("DROP INDEX IF EXISTS `index_bill_shares_household_id`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bills` (`id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `due_date` TEXT NOT NULL, " +
                "`split_method` TEXT NOT NULL, `issued_by_user_id` TEXT NOT NULL, `created_at` TEXT NOT NULL, " +
                "`server_updated_at` TEXT NOT NULL, `syncState` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`repeat` TEXT NOT NULL, `repeat_stopped` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `bills` (id, household_id, title, amount_cents, due_date, split_method, " +
                "issued_by_user_id, created_at, server_updated_at, syncState, updatedAt, repeat, repeat_stopped) " +
                "SELECT id, household_id, title, CAST(ROUND(amount * 100) AS INTEGER), due_date, split_method, " +
                "issued_by_user_id, created_at, server_updated_at, syncState, updatedAt, 'Never', 0 FROM `bills_v1`"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bill_shares` (`id` TEXT NOT NULL, `bill_id` TEXT NOT NULL, " +
                "`household_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `amount_owed_cents` INTEGER NOT NULL, " +
                "`is_paid` INTEGER NOT NULL, `paid_at` TEXT, `display_name` TEXT, `syncState` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `bill_shares` (id, bill_id, household_id, user_id, amount_owed_cents, is_paid, " +
                "paid_at, display_name, syncState, updatedAt) " +
                "SELECT id, bill_id, household_id, user_id, CAST(ROUND(amount_owed * 100) AS INTEGER), is_paid, " +
                "paid_at, display_name, syncState, updatedAt FROM `bill_shares_v1`"
        )

        db.execSQL("DROP TABLE `bill_shares_v1`")
        db.execSQL("DROP TABLE `bills_v1`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_household_id` ON `bills` (`household_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_shares_bill_id` ON `bill_shares` (`bill_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_shares_household_id` ON `bill_shares` (`household_id`)")
    }
}

/**
 * v2 -> v3 (RM-0017): the outbox gets the fields RM-0018's worker needs to replay
 * an entry (local entity id, attempt count, last error), and the household roster
 * gets its own table so Add Bill works offline. Columns are only added, so every
 * queued entry and cached row is kept.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `entityId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `attemptCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `lastError` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `household_members` (`household_id` TEXT NOT NULL, " +
                "`user_id` TEXT NOT NULL, `display_name` TEXT, PRIMARY KEY(`household_id`, `user_id`))"
        )
    }
}

/**
 * v3 -> v4 (RM-0018): the outbox keeps the Storage paths of photos picked for a
 * queued bill, so the worker can link them once the bill exists on the server.
 * A new table records changes the server refused, for RM-0019 to show. Columns
 * and tables are only added, so every queued entry and cached row is kept.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `attachmentPaths` TEXT NOT NULL DEFAULT '[]'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_rejections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`op` TEXT NOT NULL, `tableName` TEXT NOT NULL, `entityId` TEXT NOT NULL, `reason` TEXT NOT NULL, " +
                "`message` TEXT, `dependant` INTEGER NOT NULL, `rejectedAt` INTEGER NOT NULL)"
        )
    }
}

/** Every migration, oldest first. Pass these to every database builder. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
