package com.rentmate.app.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CurrentHousehold"

/**
 * The household the signed-in user is currently acting in. Every screen reads it from here.
 *
 * RM-0016: the id is also saved through [HouseholdIdStore], so after a cold start
 * offline the repositories still know which household's cache to show.
 */
@Singleton
class CurrentHousehold internal constructor(
    private val store: HouseholdIdStore,
    dispatcher: CoroutineDispatcher
) {
    /** Hilt constructor: saves on the IO dispatcher. */
    @Inject
    constructor(store: HouseholdIdStore) : this(store, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val writeLock = Mutex()
    private val lock = Any()

    /** True once [set] or [clear] ran, so a slow restore never overwrites a newer choice. */
    private var changed = false

    private val _id = MutableStateFlow<String?>(null)

    /** The active household id, or null when none is selected (yet). */
    val id: StateFlow<String?> = _id

    /** Loads the saved id once, as soon as the object is built. */
    private val restored: Deferred<Unit> = scope.async {
        val saved = try {
            store.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "could not read the saved household", e)
            null
        }
        synchronized(lock) {
            if (!changed && saved != null) {
                _id.value = saved
                Log.d(TAG, "restored saved household")
            }
        }
    }

    /** Suspends until the saved id has been loaded from disk (or failed to load). */
    suspend fun awaitRestored() = restored.await()

    /** Selects [householdId] and saves it for the next launch. */
    fun set(householdId: String) {
        synchronized(lock) {
            changed = true
            _id.value = householdId
        }
        Log.d(TAG, "household selected")
        scope.launch { persist() }
    }

    /** Forgets the selection in memory and on disk, without waiting for the disk write. */
    fun clear() {
        synchronized(lock) {
            changed = true
            _id.value = null
        }
        Log.d(TAG, "household cleared")
        scope.launch { persist() }
    }

    /** Like [clear], but returns only after the saved id is gone. Used on sign-out. */
    suspend fun forget() {
        synchronized(lock) {
            changed = true
            _id.value = null
        }
        persist()
        Log.d(TAG, "household forgotten on sign-out")
    }

    /** Writes the latest value, so writes that finish out of order still end correct. */
    private suspend fun persist() {
        try {
            writeLock.withLock { store.write(_id.value) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "could not save the household", e)
        }
    }
}
