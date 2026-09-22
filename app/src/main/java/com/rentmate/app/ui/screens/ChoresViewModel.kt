package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.network.ChoreResponse
import com.rentmate.app.network.ChoresApi
import com.rentmate.app.network.CompleteChoreRequest
import com.rentmate.app.network.LeaderboardResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Context
import com.rentmate.app.data.SettingToggle
import javax.inject.Inject

data class ChoresUiState(
    val chores: List<ChoreResponse> = emptyList(),
    val leaderboard: LeaderboardResponse? = null,
    val showLeaderboard: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val api: ChoresApi,
    private val currentHousehold: CurrentHousehold,
    @ApplicationContext context: Context
) : ViewModel() {
    private val settingsRepository = SettingsRepository(context)
    private val _state = MutableStateFlow(ChoresUiState())
    val state: StateFlow<ChoresUiState> = _state

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(showLeaderboard = settings.isOn(SettingToggle.SHOW_LEADERBOARD))
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.list(householdId) }
                .onSuccess { _state.value = _state.value.copy(loading = false, chores = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }

            runCatching { api.leaderboard(householdId) }
                .onSuccess { _state.value = _state.value.copy(leaderboard = it) }
        }
    }

    /** US-9: records who completed the chore; the server advances the rotation. */
    fun complete(choreId: String) {
        viewModelScope.launch {
            runCatching { api.complete(choreId, CompleteChoreRequest()) }
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}
