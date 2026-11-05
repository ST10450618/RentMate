package com.rentmate.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.ChoreForecastRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.LeaderboardRow
import com.rentmate.app.R
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper

data class ChoresUiState(
    val chores: List<ChoreRow> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    /** chore id -> next cycles, from the server's forecast_chore RPC. */
    val forecasts: Map<String, List<ChoreForecastRow>> = emptyMap(),
    val leaderboard: List<LeaderboardRow>? = null,
    val showLeaderboard: Boolean = true,
    val loading: Boolean = false,
    @StringRes val error: Int? = null
)

fun ChoreRow.currentAssignee(): String? =
    if (rotation_order.isEmpty()) null else rotation_order[current_rotation_index % rotation_order.size]

private const val TAG = "ChoresViewModel"
private const val FORECAST_CYCLES = 4

@Serializable
private data class ForecastParams(val chore_id_in: String, val cycles: Int = FORECAST_CYCLES)

@Serializable
private data class CompleteChoreParams(val chore_id_in: String)

@Serializable
private data class HouseholdLeaderboardRow(val hide_leaderboard: Boolean = false)

/**
 * RM-0008: complete_chore raises 42501 for a non-assignee and 23505 for a
 * second completion in the same cycle (see supabase/migrations/0009).
 */
internal fun completeChoreErrorRes(code: String?, message: String?): Int? = when {
    code == "42501" || message?.contains("assigned housemate") == true -> R.string.chore_error_not_assignee
    code == "23505" || message?.contains("already completed") == true -> R.string.chore_error_already_done
    else -> null
}

@Serializable
private data class LeaderboardParams(val household_id_in: String)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val settingsRepository = SettingsRepository(context)
    private val _state = MutableStateFlow(ChoresUiState())
    val state: StateFlow<ChoresUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                supabase.from("chores").select { filter { eq("household_id", householdId) } }.decodeList<ChoreRow>()
            }.onSuccess { _state.value = _state.value.copy(loading = false, chores = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = ErrorMapper.toMessage(it)) }

            // Names come from the household roster, not the leaderboard, so members who hide
            // themselves from the leaderboard still show a name instead of a raw user ID.
            runCatching {
                supabase.from("household_members")
                    .select(columns = Columns.raw("user_id, profiles(display_name)")) { filter { eq("household_id", householdId) } }
                    .decodeList<HouseholdMemberWithProfile>()
            }.onSuccess { members ->
                _state.value = _state.value.copy(
                    memberNames = members.mapNotNull { m -> m.profiles?.display_name?.let { m.user_id to it } }.toMap()
                )
            }

            // RM-0007: the rotation forecast is computed by the server (forecast_chore).
            val chores = _state.value.chores
            val forecasts = runCatching {
                coroutineScope {
                    chores.map { chore ->
                        async {
                            Log.d(TAG, "rpc forecast_chore chore=${chore.id} cycles=$FORECAST_CYCLES")
                            chore.id to supabase.postgrest
                                .rpc("forecast_chore", ForecastParams(chore.id).toRpcParams())
                                .decodeList<ChoreForecastRow>()
                        }
                    }.awaitAll().toMap()
                }
            }.onFailure { Log.w(TAG, "forecast_chore failed: ${it.message}") }.getOrDefault(emptyMap())
            _state.value = _state.value.copy(forecasts = forecasts)

            // RM-0008: leaderboard visibility is household-wide (households.hide_leaderboard).
            runCatching {
                supabase.from("households").select {
                    filter { eq("id", householdId) }
                }.decodeSingle<HouseholdLeaderboardRow>()
            }.onSuccess {
                _state.value = _state.value.copy(showLeaderboard = !it.hide_leaderboard)
                // Keep the Settings toggle (DataStore) in step with the shared value.
                settingsRepository.setToggle(SettingToggle.SHOW_LEADERBOARD, !it.hide_leaderboard)
            }

            runCatching {
                supabase.postgrest.rpc("leaderboard", LeaderboardParams(householdId).toRpcParams()).decodeList<LeaderboardRow>()
            }.onSuccess {
                _state.value = _state.value.copy(
                    leaderboard = it,
                    memberNames = it.associate { row -> row.user_id to row.display_name } + _state.value.memberNames
                )
            }
        }
    }

    /** US-9: records who completed the chore; the server advances the rotation. */
    fun complete(choreId: String) {
        viewModelScope.launch {
            runCatching { supabase.postgrest.rpc("complete_chore", CompleteChoreParams(choreId).toRpcParams()) }
                .onSuccess { refresh() }
                .onFailure {
                    // Seth's specific chore rules first (RM-0008), then the app-wide mapper (RM-0004).
                    val res = (it as? PostgrestRestException)
                        ?.let { e -> completeChoreErrorRes(e.code, e.message) }
                        ?: completeChoreErrorRes(null, it.message)
                    _state.value = _state.value.copy(error = res ?: ErrorMapper.toMessage(it))
                }
        }
    }
}
