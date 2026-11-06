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
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
    val members: List<String> = emptyList(),
    val overdueBills: List<BillWithSharesRow> = emptyList(),
    val nextChore: ChoreRow? = null,
    val youOwe: Double = 0.0,
    val owedToYou: Double = 0.0
)

@Serializable
private data class MemberNameRow(val display_name: String)

@Serializable
private data class MemberWrapperRow(val profiles: MemberNameRow? = null)

private val MEMBER_COLUMNS = Columns.raw("profiles(display_name)")

private const val TAG = "DashboardViewModel"

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
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val billsRepository: BillsRepository,
    private val choresRepository: ChoresRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        // RM-0016: the cards come from the Room cache, so they show offline and
        // survive a failed refresh.
        viewModelScope.launch {
            combine(currentHousehold.id, billsRepository.bills, choresRepository.chores) { id, bills, chores ->
                id to summarise(bills, chores, supabase.auth.currentUserOrNull()?.id, Instant.now().toString())
            }.catch { Log.e(TAG, "could not read cached dashboard data", it) }
                .collect { (id, summary) ->
                    _state.update {
                        it.copy(
                            currentHouseholdId = id,
                            overdueBills = summary.overdueBills,
                            nextChore = summary.nextChore,
                            youOwe = summary.youOwe,
                            owedToYou = summary.owedToYou
                        )
                    }
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
            runCatching {
                supabase.from("household_members")
                    .select(columns = MEMBER_COLUMNS) { filter { eq("household_id", householdId) } }
                    .decodeList<MemberWrapperRow>()
                    .mapNotNull { it.profiles?.display_name }
            }.onSuccess { members -> _state.update { it.copy(members = members) } }
                .onFailure { if (error == null) error = ErrorMapper.toMessage(it) }

            _state.update { it.copy(loading = false, error = error) }
        }
    }

    /** Household switcher on the Dashboard (S3). */
    fun switchHousehold(householdId: String) {
        currentHousehold.set(householdId)
        refresh()
    }
}
