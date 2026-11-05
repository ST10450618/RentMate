package com.rentmate.app.data.local

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalCache"

/**
 * The on-device copy of household data (RM-0015).
 *
 * PRIVACY.md promises the cached copy is removed on sign-out, so on a shared
 * phone the next user never sees the previous household's bills.
 */
@Singleton
class LocalCache @Inject constructor(private val database: RentMateDatabase) {

    /**
     * Deletes every cached row, including the outbox. Offline changes that were
     * never uploaded are lost; sign-out is the user's explicit choice to leave.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        Log.d(TAG, "local cache cleared")
    }
}
