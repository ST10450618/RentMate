package com.rentmate.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileSettingsSync
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.UserSettings
import com.rentmate.app.data.applyLanguage
import com.rentmate.app.data.local.LocalCache
import com.rentmate.app.notifications.DeviceTokenRegistrar
import com.rentmate.app.ui.launchSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

private val TOGGLE_COLUMN = mapOf(
    SettingToggle.NOTIFY_BILLS to "notify_bills",
    SettingToggle.NOTIFY_CHORES to "notify_chores",
    SettingToggle.NOTIFY_SHOPPING to "notify_shopping_list",
    SettingToggle.NOTIFY_MAINTENANCE to "notify_maintenance",
    SettingToggle.BIOMETRIC_UNLOCK to "biometric_enabled",
    SettingToggle.SHOW_LEADERBOARD to "show_leaderboard"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val localCache: LocalCache,
    private val profileSettingsSync: ProfileSettingsSync,
    private val deviceTokenRegistrar: DeviceTokenRegistrar
) : ViewModel() {

    private val repository = SettingsRepository(context)

    init {
        // RM-0005: pick up changes made on another device since sign-in.
        launchSafely(TAG) { profileSettingsSync.pull() }
    }

    val state: StateFlow<UserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    fun setToggle(toggle: SettingToggle, value: Boolean) {
        Log.d(TAG, "setToggle ${toggle.name} = $value")
        launchSafely(TAG) {
            repository.setToggle(toggle, value)
            syncBoolean(TOGGLE_COLUMN.getValue(toggle), value)
            if (toggle == SettingToggle.SHOW_LEADERBOARD) syncHouseholdLeaderboard(!value)
        }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        launchSafely(TAG) {
            // RM-0026: save, then switch the UI straight away (this recreates the
            // activity; the ViewModel and this coroutine survive that), then write
            // it to the profile so it follows the user to other devices.
            repository.setLanguage(language)
            applyLanguage(language)
            syncString("preferred_language", language.tag)
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            // RM-0021: stop this device getting the account's pushes. This must run
            // while the session is still valid. Offline it gives up after 5 s and
            // sign-out goes ahead.
            withTimeoutOrNull(5_000) { deviceTokenRegistrar.unregisterCurrent() }
            runCatching { supabase.auth.signOut() }
                .onFailure { Log.w(TAG, "sign-out failed: ${it.message}") }
            // RM-0016: also removes the saved household id from disk.
            currentHousehold.forget()
            // PRIVACY.md: the cached household data is removed on sign-out.
            runCatching { localCache.clear() }
                .onFailure { Log.e(TAG, "could not clear local cache on sign-out", it) }
            onSignedOut()
        }
    }

    /** US-3: preferences follow the user to a new device via the profiles table. */
    private suspend fun syncBoolean(column: String, value: Boolean) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        runCatching {
            supabase.from("profiles").update({ set(column, value) }) {
                filter { eq("id", userId) }
            }
        }.onFailure { Log.w(TAG, "settings sync failed: ${it.message}") }
    }

    /** RM-0008: leaderboard visibility is shared by the whole household. */
    private suspend fun syncHouseholdLeaderboard(hide: Boolean) {
        val householdId = currentHousehold.id.first() ?: return
        runCatching {
            supabase.from("households").update({ set("hide_leaderboard", hide) }) {
                filter { eq("id", householdId) }
            }
        }.onFailure { Log.w(TAG, "household leaderboard sync failed: ${it.message}") }
    }

    private suspend fun syncString(column: String, value: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        runCatching {
            supabase.from("profiles").update({ set(column, value) }) {
                filter { eq("id", userId) }
            }
        }.onFailure { Log.w(TAG, "settings sync failed: ${it.message}") }
    }
}
