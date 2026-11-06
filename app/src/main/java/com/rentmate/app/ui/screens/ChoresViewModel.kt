package com.rentmate.app.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.ChoreForecastRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.ChoresRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.LeaderboardRow
import com.rentmate.app.R
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.SettingsRepository
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.ui.feedback.UndoQueue
import com.rentmate.app.ui.feedback.UndoableAction
import com.rentmate.app.ui.feedback.UserMessage

data class ChoresUiState(
    val chores: List<ChoreRow> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    /** chore id -> next cycles, from the server's forecast_chore RPC. */
    val forecasts: Map<String, List<ChoreForecastRow>> = emptyMap(),
    val leaderboard: List<LeaderboardRow>? = null,
    val showLeaderboard: Boolean = true,
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /**
     * RM-0056: chores marked done that are still inside their Undo window. The
     * screen shows them as done; complete_chore runs when the window closes.
     */
    val pendingDone: Set<String> = emptySet(),
    /** RM-0058: the signed-in user, so the screen can pick "Your next chore" and label "You". */
    val currentUserId: String? = null,
    /**
     * RM-0058: chore id -> the user who completed it in the current cycle,
     * read from chore_completions. A chore in this map shows a Done pill.
     */
    val doneBy: Map<String, String> = emptyMap()
) {
    /** True when [choreId] is done this cycle or is waiting in its Undo window. */
    fun isDone(choreId: String): Boolean = choreId in doneBy || choreId in pendingDone

    /** RM-0058: the chore for the "Your next chore" card, or null when the user has none due. */
    fun nextChore(today: LocalDate = LocalDate.now()): ChoreRow? =
        nextChoreFor(chores, currentUserId, doneBy.keys, today)
}

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
private data class ChoreCompletionRow(val chore_id: String, val completed_by_user_id: String, val completed_at: String)

/**
 * RM-0058: the first day of the cycle that [today] falls in, using the same
 * rules as complete_chore (migration 0009): a week starts on Monday and a month
 * on the 1st. A "Once" chore has a single cycle, so this returns null.
 */
internal fun cycleStart(recurrence: String, today: LocalDate): LocalDate? = when (recurrence) {
    "Weekly" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    "Monthly" -> today.withDayOfMonth(1)
    else -> null
}

/**
 * RM-0058: the last day of a cycle, which the screen shows as the day pill.
 * [cyclesAhead] 0 is the current cycle, 1 the next one, and so on. Weekly
 * chores end on Sunday and monthly chores on the last day of the month. A
 * "Once" chore has no end day, so this returns null.
 */
internal fun cycleDueDate(recurrence: String, today: LocalDate, cyclesAhead: Int = 0): LocalDate? = when (recurrence) {
    "Weekly" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(cyclesAhead.toLong())
    "Monthly" -> today.plusMonths(cyclesAhead.toLong()).with(TemporalAdjusters.lastDayOfMonth())
    else -> null
}

/**
 * RM-0058: true when a completion on [completedOn] counts for the current
 * cycle of a chore with [recurrence]. Any completion counts for a "Once" chore.
 */
internal fun countsForCurrentCycle(recurrence: String, completedOn: LocalDate, today: LocalDate): Boolean {
    val start = cycleStart(recurrence, today) ?: return true
    return !completedOn.isBefore(start)
}

/**
 * RM-0058: the chore for the "Your next chore" card. It is the first chore
 * where [userId] is the current assignee and that is not in [doneIds], with
 * the earliest due day first. Chores with no due day ("Once") come last.
 */
internal fun nextChoreFor(chores: List<ChoreRow>, userId: String?, doneIds: Set<String>, today: LocalDate): ChoreRow? {
    if (userId == null) return null
    return chores
        .filter { it.currentAssignee() == userId && it.id !in doneIds }
        .minWithOrNull(compareBy(nullsLast()) { cycleDueDate(it.recurrence, today) })
}

/**
 * RM-0048: the message to show when [userId] tries to complete [chore], or
 * null when they may. Only the current assignee can complete a chore, and a
 * chore with an empty rotation can be completed by anyone. The server checks
 * the same rule, so this only saves a round trip.
 */
@StringRes
internal fun completeBlockedRes(chore: ChoreRow, userId: String?, alreadyDone: Boolean): Int? {
    val assignee = chore.currentAssignee()
    return when {
        alreadyDone -> R.string.chore_error_already_done
        userId != null && assignee != null && assignee != userId -> R.string.chore_error_not_assignee
        else -> null
    }
}

@Serializable
private data class LeaderboardParams(val household_id_in: String)

@HiltViewModel
class ChoresViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    @ApplicationContext private val context: Context,
    private val activity: ActivityLogger,
    private val choresRepository: ChoresRepository,
    private val feedback: FeedbackCenter,
    private val undoQueue: UndoQueue
) : ViewModel() {
    private val settingsRepository = SettingsRepository(context)
    private val _state = MutableStateFlow(ChoresUiState())
    val state: StateFlow<ChoresUiState> = _state

    init {
        // RM-0016: the list always comes from the Room cache, so it survives a failed refresh.
        viewModelScope.launch {
            choresRepository.chores
                .catch { Log.e(TAG, "could not read cached chores", it) }
                .collect { chores -> _state.update { it.copy(chores = chores) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            Log.d(TAG, "refresh household=$householdId")
            _state.update { it.copy(currentUserId = supabase.auth.currentUserOrNull()?.id) }
            runCatching { choresRepository.refresh() }
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = ErrorMapper.toMessage(e)) } }

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
            val chores = choresRepository.chores.first()
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

            // RM-0058: who has done which chore this cycle, for the Done pills.
            if (chores.isNotEmpty()) {
                runCatching {
                    supabase.from("chore_completions")
                        .select(columns = Columns.raw("chore_id, completed_by_user_id, completed_at")) {
                            filter { isIn("chore_id", chores.map { it.id }) }
                        }
                        .decodeList<ChoreCompletionRow>()
                }.onSuccess { rows ->
                    val today = LocalDate.now()
                    val byId = chores.associateBy { it.id }
                    val doneBy = rows.mapNotNull { row ->
                        val chore = byId[row.chore_id] ?: return@mapNotNull null
                        val day = runCatching {
                            OffsetDateTime.parse(row.completed_at).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                        }.getOrNull() ?: return@mapNotNull null
                        if (countsForCurrentCycle(chore.recurrence, day, today)) row.chore_id to row.completed_by_user_id else null
                    }.toMap()
                    Log.d(TAG, "done this cycle: ${doneBy.size} of ${chores.size}")
                    _state.update { it.copy(doneBy = doneBy) }
                }.onFailure { Log.w(TAG, "chore_completions failed: ${it.message}") }
            }

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

    /**
     * US-9: records who completed the chore; the server advances the rotation.
     * RM-0056: the chore shows as done at once and a snackbar offers Undo for
     * 5 s. complete_chore runs only after that, because the rotation it
     * advances cannot be put back safely. See [UndoQueue].
     */
    fun complete(choreId: String) {
        val current = _state.value
        val chore = current.chores.firstOrNull { it.id == choreId }
        val title = chore?.title
        // RM-0048: only the assignee completes a chore. Say so at once instead of
        // showing Undo for a call the server will refuse.
        val blocked = chore?.let { completeBlockedRes(it, current.currentUserId, current.isDone(choreId)) }
        if (blocked != null) {
            Log.d(TAG, "complete $choreId blocked")
            feedback.show(blocked)
            return
        }
        Log.d(TAG, "complete $choreId, waiting for Undo")
        undoQueue.submit(
            if (title != null) UserMessage(R.string.chores_marked_done, listOf(title)) else UserMessage(R.string.undo_chore_done),
            UndoableAction(
                key = "complete:$choreId",
                hide = { _state.update { it.copy(pendingDone = it.pendingDone + choreId) } },
                restore = { _state.update { it.copy(pendingDone = it.pendingDone - choreId) } },
                commit = {
                    supabase.postgrest.rpc("complete_chore", CompleteChoreParams(choreId).toRpcParams())
                    _state.update {
                        val doer = it.currentUserId
                        it.copy(
                            pendingDone = it.pendingDone - choreId,
                            doneBy = if (doer != null) it.doneBy + (choreId to doer) else it.doneBy
                        )
                    }
                    if (title != null) activity.choreCompleted(title, choreId)
                    refresh()
                },
                onCommitFailed = {
                    // Seth's specific chore rules first (RM-0008), then the app-wide mapper (RM-0004).
                    val res = (it as? PostgrestRestException)
                        ?.let { e -> completeChoreErrorRes(e.code, e.message) }
                        ?: completeChoreErrorRes(null, it.message)
                    if (res != null) {
                        feedback.show(res)
                    } else {
                        feedback.failure(it, R.string.feedback_complete_chore_failed, retry = { complete(choreId) })
                    }
                }
            )
        )
    }
}
