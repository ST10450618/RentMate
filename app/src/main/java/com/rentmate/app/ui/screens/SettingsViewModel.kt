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
import com.rentmate.app.network.AuthApi
import com.rentmate.app.network.UpdateSettingsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApi: AuthApi
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
            syncToAccount(toggle, value)
        }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        viewModelScope.launch {
            repository.setLanguage(language)
            applyLanguage(context, language)
            runCatching { authApi.updateSettings(UpdateSettingsRequest(preferredLanguage = language.tag)) }
                .onFailure { Log.w(TAG, "settings sync failed: ${it.message}") }
        }
    }

    /** US-3: preferences follow the user to a new device via PUT /api/auth/me/settings. */
    private suspend fun syncToAccount(toggle: SettingToggle, value: Boolean) {
        val request = when (toggle) {
            SettingToggle.NOTIFY_BILLS -> UpdateSettingsRequest(notifyBills = value)
            SettingToggle.NOTIFY_CHORES -> UpdateSettingsRequest(notifyChores = value)
            SettingToggle.NOTIFY_SHOPPING -> UpdateSettingsRequest(notifyShoppingList = value)
            SettingToggle.NOTIFY_MAINTENANCE -> UpdateSettingsRequest(notifyMaintenance = value)
            SettingToggle.BIOMETRIC_UNLOCK -> UpdateSettingsRequest(biometricEnabled = value)
            SettingToggle.SHOW_LEADERBOARD -> UpdateSettingsRequest(showLeaderboard = value)
        }
        runCatching { authApi.updateSettings(request) }
            .onFailure { Log.w(TAG, "settings sync failed: ${it.message}") }
    }
}
