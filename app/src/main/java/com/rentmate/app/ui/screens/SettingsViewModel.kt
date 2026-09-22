package com.rentmate.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.UserSettings
import com.rentmate.app.data.applyLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val supabase: SupabaseClient
) : ViewModel() {

    private val repository = SettingsRepository(context)

    val state: StateFlow<UserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    fun setToggle(toggle: SettingToggle, value: Boolean) {
        Log.d(TAG, "setToggle ${toggle.name} = $value")
        viewModelScope.launch {
            repository.setToggle(toggle, value)
            syncBoolean(TOGGLE_COLUMN.getValue(toggle), value)
        }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        viewModelScope.launch {
            repository.setLanguage(language)
            applyLanguage(context, language)
            syncString("preferred_language", language.tag)
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

    private suspend fun syncString(column: String, value: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        runCatching {
            supabase.from("profiles").update({ set(column, value) }) {
                filter { eq("id", userId) }
            }
        }.onFailure { Log.w(TAG, "settings sync failed: ${it.message}") }
    }
}
