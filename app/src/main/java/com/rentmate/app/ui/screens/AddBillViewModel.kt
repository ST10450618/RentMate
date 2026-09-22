package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileNameRow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

sealed interface AddBillUiState {
    data object Idle : AddBillUiState
    data object Loading : AddBillUiState
    data object Saved : AddBillUiState
    data class Error(val message: String) : AddBillUiState
}

@Serializable
data class HouseholdMemberWithProfile(val user_id: String, val profiles: ProfileNameRow? = null)

@Serializable
private data class BillInsert(
    val household_id: String,
    val title: String,
    val amount: Double,
    val due_date: String,
    val split_method: String,
    val issued_by_user_id: String
)

@Serializable
private data class BillIdRow(val id: String)

@Serializable
private data class BillShareInsert(val bill_id: String, val user_id: String, val amount_owed: Double)

@HiltViewModel
class AddBillViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _members = MutableStateFlow<List<HouseholdMemberWithProfile>>(emptyList())
    val members: StateFlow<List<HouseholdMemberWithProfile>> = _members

    private val _state = MutableStateFlow<AddBillUiState>(AddBillUiState.Idle)
    val state: StateFlow<AddBillUiState> = _state

    init {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching {
                supabase.from("household_members")
                    .select(columns = Columns.raw("user_id, profiles(display_name)")) { filter { eq("household_id", householdId) } }
                    .decodeList<HouseholdMemberWithProfile>()
            }.onSuccess { _members.value = it }
        }
    }

    /** US-6: equal split across every household member; amount must be greater than 0. */
    fun createEqualSplit(title: String, amount: Double, dueDate: LocalDate) {
        if (amount <= 0) {
            _state.value = AddBillUiState.Error("Amount must be greater than 0.")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = AddBillUiState.Loading
            runCatching {
                val bill = supabase.from("bills").insert(
                    BillInsert(
                        household_id = householdId,
                        title = title,
                        amount = amount,
                        due_date = "${dueDate}T00:00:00Z",
                        split_method = "Equal",
                        issued_by_user_id = userId
                    )
                ) { select() }.decodeSingle<BillIdRow>()

                val shares = equalShares(amount, _members.value.map { it.user_id })
                    .map { (memberId, share) -> BillShareInsert(bill.id, memberId, share) }
                supabase.from("bill_shares").insert(shares)
            }.onSuccess { _state.value = AddBillUiState.Saved }
                .onFailure { _state.value = AddBillUiState.Error(it.message ?: "Could not create bill") }
        }
    }

    /** Same rounding rule as the API's C# version: last share absorbs the remainder. */
    private fun equalShares(amount: Double, memberIds: List<String>): List<Pair<String, Double>> {
        if (memberIds.isEmpty()) return emptyList()
        val total = BigDecimal.valueOf(amount)
        val each = total.divide(BigDecimal(memberIds.size), 2, RoundingMode.HALF_UP)
        var running = BigDecimal.ZERO
        return memberIds.mapIndexed { index, id ->
            val share = if (index == memberIds.lastIndex) total.subtract(running) else each
            running = running.add(share)
            id to share.toDouble()
        }
    }
}
