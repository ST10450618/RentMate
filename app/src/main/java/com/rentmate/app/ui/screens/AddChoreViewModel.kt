package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.util.Log
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validatePoints
import com.rentmate.app.ui.validation.validateRequiredText
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter

private const val TAG = "AddChoreViewModel"

sealed interface AddChoreUiState {
    data object Idle : AddChoreUiState
    data object Loading : AddChoreUiState
    data object Saved : AddChoreUiState
}

/** RM-0058: one housemate in the "Takes turns" list. */
data class RotationMember(val userId: String, val name: String?, val isYou: Boolean)

/**
 * RM-0058: the default rotation. The person adding the chore goes first, so
 * they see their own turn come up, and everyone else follows in name order.
 * Everyone is included, as migration 0013 requires.
 */
internal fun defaultRotation(members: List<RotationMember>): List<RotationMember> =
    members.sortedWith(compareBy<RotationMember>({ !it.isYou }, { it.name?.lowercase() ?: "\uFFFF" }, { it.userId }))

@Serializable
private data class ChoreInsert(
    val household_id: String,
    val title: String,
    val recurrence: String,
    val point_value: Int,
    val rotation_order: List<String>
)

@HiltViewModel
class AddChoreViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val feedback: FeedbackCenter
) : ViewModel() {
    private val _members = MutableStateFlow<List<RotationMember>>(emptyList())

    /** RM-0058: the rotation in turn order, from [defaultRotation]. */
    val members: StateFlow<List<RotationMember>> = _members

    private val _state = MutableStateFlow<AddChoreUiState>(AddChoreUiState.Idle)
    val state: StateFlow<AddChoreUiState> = _state

    private val household = MutableStateFlow(HouseholdStatus.Unknown)
    private val rosterRefresh = MutableStateFlow(RosterRefresh.Running)

    /** RM-0056: LoadingState, EmptyState or ErrorState until the form can be filled in. */
    val loadState: StateFlow<FormLoadState> = combine(household, _members, rosterRefresh) { h, m, r ->
        formLoadState(h, needsRoster = true, rosterSize = m.size, refresh = r)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FormLoadState.Loading)

    init {
        viewModelScope.launch {
            currentHousehold.awaitRestored()
            currentHousehold.id.collect { household.value = if (it == null) HouseholdStatus.None else HouseholdStatus.Present }
        }
        loadMembers()
    }

    /** Loads the rotation order. The ErrorState's Retry button calls it again. */
    fun loadMembers() {
        Log.d(TAG, "loadMembers")
        rosterRefresh.value = RosterRefresh.Running
        viewModelScope.launch {
            currentHousehold.awaitRestored()
            val householdId = currentHousehold.id.first()
            if (householdId == null) {
                rosterRefresh.value = RosterRefresh.Done
                return@launch
            }
            runCatching {
                supabase.from("household_members")
                    .select(columns = Columns.raw("user_id, profiles(display_name)")) { filter { eq("household_id", householdId) } }
                    .decodeList<HouseholdMemberWithProfile>()
            }.onSuccess {
                val me = supabase.auth.currentUserOrNull()?.id
                _members.value = defaultRotation(
                    it.map { m -> RotationMember(m.user_id, m.profiles?.display_name, m.user_id == me) }
                )
                Log.d(TAG, "rotation loaded: ${_members.value.size} housemates")
                rosterRefresh.value = RosterRefresh.Done
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.w(TAG, "could not load members", it)
                rosterRefresh.value = RosterRefresh.Failed
            }
        }
    }

    /** US-9/US-12: the rotation is every household member, in the "Takes turns" order. */
    fun create(title: String, recurrence: String, pointValue: Int) {
        // Last guard behind the screen's inline validation (RM-0036).
        if (validateRequiredText(title, Limits.TITLE_MAX) != null ||
            validatePoints(pointValue.toString()) != null ||
            _members.value.isEmpty()
        ) {
            Log.w(TAG, "create rejected invalid input")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = AddChoreUiState.Loading
            runCatching {
                supabase.from("chores").insert(
                    ChoreInsert(householdId, title, recurrence, pointValue, _members.value.map { m -> m.userId })
                )
            }.onSuccess { _state.value = AddChoreUiState.Saved }
                .onFailure {
                    _state.value = AddChoreUiState.Idle
                    feedback.failure(it, R.string.feedback_add_chore_failed, retry = { create(title, recurrence, pointValue) })
                }
        }
    }
}
