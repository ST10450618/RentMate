package com.rentmate.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.LeaderboardRow
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

data class ChoresUiState(
    val chores: List<ChoreRow> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val leaderboard: List<LeaderboardRow>? = null,
    val showLeaderboard: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null
)

/** Same modulo logic as ChoreRotationService.Forecast: next 4 cycles from the current index. */
fun ChoreRow.forecast(cycles: Int = 4): List<Pair<Int, String>> {
    if (rotation_order.isEmpty()) return emptyList()
    return (0 until cycles).map { i ->
        (i + 1) to rotation_order[(current_rotation_index + i) % rotation_order.size]
    }
}

fun ChoreRow.currentAssignee(): String? =
    if (rotation_order.isEmpty()) null else rotation_order[current_rotation_index % rotation_order.size]

@Serializable
private data class CompleteChoreParams(val chore_id_in: String)

@Serializable
private data class LeaderboardParams(val household_id_in: String)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val supabase: SupabaseClient,
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
            runCatching {
                supabase.from("chores").select { filter { eq("household_id", householdId) } }.decodeList<ChoreRow>()
            }.onSuccess { _state.value = _state.value.copy(loading = false, chores = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }

            runCatching {
                supabase.postgrest.rpc("leaderboard", LeaderboardParams(householdId).toRpcParams()).decodeList<LeaderboardRow>()
            }.onSuccess {
                _state.value = _state.value.copy(
                    leaderboard = it,
                    memberNames = it.associate { row -> row.user_id to row.display_name }
                )
            }
        }
    }

    /** US-9: records who completed the chore; the server advances the rotation. */
    fun complete(choreId: String) {
        viewModelScope.launch {
            runCatching { supabase.postgrest.rpc("complete_chore", CompleteChoreParams(choreId).toRpcParams()) }
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}
