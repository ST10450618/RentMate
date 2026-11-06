package com.rentmate.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.BillsRepository
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.ChoresRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.DebtSimplifier
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.data.Obligation
import com.rentmate.app.data.local.centsToRand
import com.rentmate.app.data.local.toCents
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper

data class DashboardUiState(
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    val myUserId: String? = null,
    val households: List<HouseholdRow> = emptyList(),
    val currentHouseholdId: String? = null,
    /** RM-0056: housemates' names by user id, from the cached roster, so Home works offline. */
    val memberNames: Map<String, String> = emptyMap(),
    val overdueBills: List<BillWithSharesRow> = emptyList(),
    val nextChore: ChoreRow? = null,
    val youOwe: Double = 0.0,
    val owedToYou: Double = 0.0,
    /** RM-0056: the answer at the top of Home. */
    val headline: HomeHeadline = HomeHeadline.Settled,
    /** RM-0056: the newest few household events, shown under the headline. */
    val recentActivity: List<ActivityEvent> = emptyList(),
    /** True when [recentActivity] is sample data; Home then labels it. */
    val activityIsSample: Boolean = false
) {
    /** The current household's name, used as the Home title. */
    val householdName: String? get() = households.firstOrNull { it.id == currentHouseholdId }?.name
}

private const val TAG = "DashboardViewModel"

/** How many events Home shows under "Recent activity". The Activity screen has the rest. */
internal const val RECENT_ACTIVITY_LIMIT = 3

/** The Dashboard cards derived from cached bills and chores. */
internal data class DashboardSummary(
    val overdueBills: List<BillWithSharesRow>,
    val nextChore: ChoreRow?,
    val youOwe: Double,
    val owedToYou: Double
)

/**
 * RM-0016: builds the Dashboard cards from cached rows so they show offline.
 * Balances use [DebtSimplifier.netBalances] on unpaid shares. That is the same
 * rule as the server's settle_up (migration 0012), so the totals match it.
 */
internal fun summarise(
    bills: List<BillWithSharesRow>,
    chores: List<ChoreRow>,
    myId: String?,
    now: String
): DashboardSummary {
    val obligations = bills.flatMap { bill ->
        bill.bill_shares.filter { !it.is_paid }.map {
            Obligation(creditor = bill.issued_by_user_id, debtor = it.user_id, cents = it.amount_owed.toCents())
        }
    }
    val myNet = myId?.let { DebtSimplifier.netBalances(obligations)[it] } ?: 0L
    return DashboardSummary(
        overdueBills = bills.filter { bill -> bill.due_date < now && bill.bill_shares.any { !it.is_paid } },
        nextChore = chores.firstOrNull { it.currentAssignee() == myId },
        youOwe = if (myNet < 0) (-myNet).centsToRand() else 0.0,
        owedToYou = if (myNet > 0) myNet.centsToRand() else 0.0
    )
}

/** S3 Dashboard. Cards come from the Room cache; [refresh] updates it from Supabase. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val billsRepository: BillsRepository,
    private val choresRepository: ChoresRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        // RM-0016: the cards come from the Room cache, so they show offline and
        // survive a failed refresh.
        viewModelScope.launch {
            combine(currentHousehold.id, billsRepository.bills, choresRepository.chores) { id, bills, chores ->
                val myId = supabase.auth.currentUserOrNull()?.id
                Triple(id, summarise(bills, chores, myId, Instant.now().toString()), homeHeadline(bills, myId))
            }.catch { Log.e(TAG, "could not read cached dashboard data", it) }
                .collect { (id, summary, headline) ->
                    Log.d(TAG, "headline -> ${headline::class.simpleName}")
                    _state.update {
                        it.copy(
                            currentHouseholdId = id,
                            overdueBills = summary.overdueBills,
                            nextChore = summary.nextChore,
                            youOwe = summary.youOwe,
                            owedToYou = summary.owedToYou,
                            headline = headline
                        )
                    }
                }
        }
        // RM-0056: names for "Pay Sipho" come from the Room roster, so they show offline.
        viewModelScope.launch {
            billsRepository.members
                .catch { Log.e(TAG, "could not read cached members", it) }
                .collect { rows ->
                    _state.update { s -> s.copy(memberNames = rows.mapNotNull { r -> r.display_name?.let { r.user_id to it } }.toMap()) }
                }
        }
        // RM-0056: recent activity moves into Home. A failing feed leaves the list empty.
        viewModelScope.launch {
            currentHousehold.id
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else activityRepository.observe(id, RECENT_ACTIVITY_LIMIT) }
                .catch { Log.w(TAG, "recent activity failed", it) }
                .collect { events ->
                    _state.update { it.copy(recentActivity = events, activityIsSample = activityRepository.isSampleData) }
                }
        }
        refresh()
    }

    /** Reloads households, members and the cached bills and chores. Errors keep the cached cards. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, myUserId = supabase.auth.currentUserOrNull()?.id) }
            var error: Int? = null

            // RLS already scopes this to households the signed-in user belongs to.
            runCatching { supabase.from("households").select().decodeList<HouseholdRow>() }
                .onSuccess { households -> _state.update { it.copy(households = households) } }
                .onFailure { error = ErrorMapper.toMessage(it) }

            currentHousehold.awaitRestored()
            val householdId = currentHousehold.id.value ?: _state.value.households.firstOrNull()?.id
            if (householdId == null) {
                _state.update { it.copy(loading = false, error = error) }
                return@launch
            }
            if (currentHousehold.id.value == null) currentHousehold.set(householdId)
            Log.d(TAG, "refresh household=$householdId")

            // Each refresh writes to Room; the collector in init updates the cards.
            runCatching { billsRepository.refresh() }
                .onFailure { if (error == null) error = ErrorMapper.toMessage(it) }
            runCatching { choresRepository.refresh() }
                .onFailure { if (error == null) error = ErrorMapper.toMessage(it) }
            // Members are refreshed with the bills (BillsRepository.refresh) and read from Room above.

            _state.update { it.copy(loading = false, error = error) }
        }
    }

    /** Household switcher on the Dashboard (S3). */
    fun switchHousehold(householdId: String) {
        currentHousehold.set(householdId)
        refresh()
    }
}
