package com.rentmate.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "SettingsRepository"

/** The DataStore file that holds every Settings screen value. */
private val Context.settingsDataStore by preferencesDataStore(name = "rentmate_settings")

private val LANGUAGE_KEY = stringPreferencesKey("language")

/** Immutable snapshot of everything on the Settings screen. */
data class UserSettings(
    val toggles: Map<SettingToggle, Boolean> = SettingToggle.entries.associateWith { it.default },
    val language: AppLanguage = AppLanguage.ENGLISH
) {
    /** The stored value of [toggle], or its default when it was never set. */
    fun isOn(toggle: SettingToggle): Boolean = toggles[toggle] ?: toggle.default
}

/**
 * Preferences are stored locally with DataStore so they survive restart.
 *
 * US-3 also requires them to follow the user to a new device: SettingsViewModel
 * writes each change to the `profiles` row, and [ProfileSettingsSync] reads it
 * back on sign-in (RM-0005). Storing locally first is the offline-first pattern
 * the whole app follows - the UI never waits on the network.
 *
 * Based on: Android Developers, DataStore,
 * https://developer.android.com/topic/libraries/architecture/datastore (accessed 2026-11-06)
 */
class SettingsRepository(private val context: Context) {

    /** Every setting, re-emitted on each change; unset values use their defaults. */
    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            toggles = SettingToggle.entries.associateWith { prefs[it.key] ?: it.default },
            language = AppLanguage.fromTag(prefs[LANGUAGE_KEY])
        )
    }

    /** RM-0026: the language tag the user (or their profile) chose, or null if never set. */
    val storedLanguageTag: Flow<String?> = context.settingsDataStore.data.map { it[LANGUAGE_KEY] }

    /** Saves one toggle on this device. Syncing it to the profile is the caller's job. */
    suspend fun setToggle(toggle: SettingToggle, value: Boolean) {
        context.settingsDataStore.edit { it[toggle.key] = value }
        Log.d(TAG, "toggle ${toggle.name} = $value")
    }

    /** Saves the language choice on this device; [applyLanguage] switches the UI. */
    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[LANGUAGE_KEY] = language.tag }
        Log.d(TAG, "language = ${language.tag}")
    }

    /** RM-0005: overwrite local values with the ones read from the account, in one write. */
    suspend fun seed(toggles: Map<SettingToggle, Boolean>, language: AppLanguage) {
        context.settingsDataStore.edit { prefs ->
            toggles.forEach { (toggle, value) -> prefs[toggle.key] = value }
            prefs[LANGUAGE_KEY] = language.tag
        }
        Log.d(TAG, "seeded ${toggles.size} toggles from the profile")
    }
}
