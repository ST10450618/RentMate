package com.rentmate.app.data.local

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OutboxQueue"

/**
 * RM-0017: writes an offline change to Room. The local row (written by the
 * caller's block) and its [OutboxEntry] go in one transaction, so a pending row
 * never exists without the entry that will upload it, and the other way round.
 */
@Singleton
class OutboxQueue @Inject constructor(private val database: RentMateDatabase) {

    /** Number of changes waiting to be uploaded (RM-0019 shows it). */
    val pendingCount: Flow<Int> = database.outboxDao().observePendingCount()

    /**
     * Runs [writeLocalRows] and enqueues [entry] in a single Room transaction.
     * If either part fails, neither is stored. Returns the entry's id.
     */
    suspend fun enqueue(entry: OutboxEntry, writeLocalRows: suspend () -> Unit): Long =
        database.withTransaction {
            writeLocalRows()
            database.outboxDao().enqueue(entry)
        }.also { Log.d(TAG, "queued ${entry.op} ${entry.tableName} entity=${entry.entityId} as #$it") }
}
