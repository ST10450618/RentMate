package com.rentmate.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.data.UserSettings
import com.rentmate.app.data.applyLanguage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "SettingsViewModel"

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val state: StateFlow<UserSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    fun setToggle(toggle: SettingToggle, value: Boolean) {
        Log.d(TAG, "setToggle ${toggle.name} = $value")
        viewModelScope.launch { repository.setToggle(toggle, value) }
    }

    fun setLanguage(language: AppLanguage) {
        Log.d(TAG, "setLanguage ${language.tag}")
        viewModelScope.launch {
            repository.setLanguage(language)
            applyLanguage(getApplication(), language)
        }
    }
}
