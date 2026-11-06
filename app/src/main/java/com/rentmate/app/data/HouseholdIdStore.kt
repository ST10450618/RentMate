package com.rentmate.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved household selection. Its own DataStore file (not rentmate_settings) so
 * it can be kept out of Auto Backup: a restored id on another phone would point
 * the cache at a household the new user may not belong to.
 */
private val Context.householdDataStore by preferencesDataStore(name = "rentmate_household")

private val HOUSEHOLD_ID_KEY = stringPreferencesKey("current_household_id")

/** Where [CurrentHousehold] keeps the selected household between launches. */
interface HouseholdIdStore {
    /** The saved id, or null if none was saved. */
    suspend fun read(): String?

    /** Saves [id], or removes the saved id when [id] is null. */
    suspend fun write(id: String?)
}

/**
 * [HouseholdIdStore] backed by DataStore Preferences.
 *
 * Based on: Android Developers, DataStore,
 * https://developer.android.com/topic/libraries/architecture/datastore (accessed 2026-11-06)
 */
@Singleton
class DataStoreHouseholdIdStore @Inject constructor(
    @ApplicationContext private val context: Context
) : HouseholdIdStore {

    /** Reads the first value of the DataStore, which waits for the file to load. */
    override suspend fun read(): String? = context.householdDataStore.data.first()[HOUSEHOLD_ID_KEY]

    /** Saves or removes the id in one DataStore transaction. */
    override suspend fun write(id: String?) {
        context.householdDataStore.edit { prefs ->
            if (id == null) prefs.remove(HOUSEHOLD_ID_KEY) else prefs[HOUSEHOLD_ID_KEY] = id
        }
    }
}
