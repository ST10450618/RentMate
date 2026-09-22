package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = false,
    val error: String? = null,
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
private data class DashboardSettleUpParams(val household_id_in: String)

@Serializable
private data class MemberNameRow(val display_name: String)

@Serializable
private data class MemberWrapperRow(val profiles: MemberNameRow? = null)

private val BILL_WITH_SHARES_COLUMNS = Columns.raw("*, bill_shares(*, profiles(display_name))")
private val MEMBER_COLUMNS = Columns.raw("profiles(display_name)")

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val myId = supabase.auth.currentUserOrNull()?.id

            // RLS already scopes this to households the signed-in user belongs to.
            val households = runCatching { supabase.from("households").select().decodeList<HouseholdRow>() }
                .getOrElse {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                    return@launch
                }

            val householdId = currentHousehold.id.first() ?: households.firstOrNull()?.id
            if (householdId == null) {
                _state.value = _state.value.copy(loading = false, myUserId = myId, households = households)
                return@launch
            }

            runCatching {
                val bills = supabase.from("bills")
                    .select(columns = BILL_WITH_SHARES_COLUMNS) { filter { eq("household_id", householdId) } }
                    .decodeList<BillWithSharesRow>()
                val chores = supabase.from("chores")
                    .select { filter { eq("household_id", householdId) } }
                    .decodeList<ChoreRow>()
                val settleUp = supabase.postgrest.rpc("settle_up", DashboardSettleUpParams(householdId).toRpcParams())
                    .decodeList<SettleUpRow>()
                val members = supabase.from("household_members")
                    .select(columns = MEMBER_COLUMNS) { filter { eq("household_id", householdId) } }
                    .decodeList<MemberWrapperRow>()
                    .mapNotNull { it.profiles?.display_name }
                Triple(bills, chores, settleUp) to members
            }.onSuccess { (triple, members) ->
                val (bills, chores, settleUp) = triple
                val now = Instant.now().toString()
                _state.value = _state.value.copy(
                    loading = false,
                    myUserId = myId,
                    households = households,
                    currentHouseholdId = householdId,
                    members = members,
                    overdueBills = bills.filter { bill -> bill.due_date < now && bill.bill_shares.any { !it.is_paid } },
                    nextChore = chores.firstOrNull { it.currentAssignee() == myId },
                    youOwe = settleUp.filter { it.from_user_id == myId }.sumOf { it.amount },
                    owedToYou = settleUp.filter { it.to_user_id == myId }.sumOf { it.amount }
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    /** Household switcher on the Dashboard (S3). */
    fun switchHousehold(householdId: String) {
        currentHousehold.set(householdId)
        refresh()
    }
}
