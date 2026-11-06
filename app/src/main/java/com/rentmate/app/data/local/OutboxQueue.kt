package com.rentmate.app.data.local

import android.util.Log
import androidx.room.withTransaction
import com.rentmate.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OutboxQueue"

/**
 * RM-0017: writes an offline change to Room. The local row (written by the
 * caller's block) and its [OutboxEntry] go in one transaction, so a pending row
 * never exists without the entry that will upload it, and the other way round.
 * RM-0018: after each enqueue it asks [scheduler] for a sync, which runs as soon
 * as there is a network.
 */
@Singleton
class OutboxQueue @Inject constructor(
    private val database: RentMateDatabase,
    private val scheduler: SyncScheduler
) {
    /** For tests: a queue that schedules nothing. */
    constructor(database: RentMateDatabase) : this(database, SyncScheduler.None)

    /** Number of changes waiting to be uploaded (RM-0019 shows it). */
    val pendingCount: Flow<Int> = database.outboxDao().observePendingCount()

    /**
     * Runs [writeLocalRows] and enqueues [entry] in a single Room transaction.
     * If either part fails, neither is stored. Then requests a sync. Returns the
     * entry's id.
     */
    suspend fun enqueue(entry: OutboxEntry, writeLocalRows: suspend () -> Unit): Long {
        val id = database.withTransaction {
            writeLocalRows()
            database.outboxDao().enqueue(entry)
        }
        Log.d(TAG, "queued ${entry.op} ${entry.tableName} entity=${entry.entityId} as #$id")
        scheduler.requestSync()
        return id
    }
}
