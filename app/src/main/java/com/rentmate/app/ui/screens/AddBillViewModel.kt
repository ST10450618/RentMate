package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ProfileNameRow
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitShare
import com.rentmate.app.network.toRpcParams
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import android.util.Log
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateAmount
import com.rentmate.app.ui.validation.validateDueDate
import com.rentmate.app.ui.validation.validateRequiredText
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository

private const val TAG = "AddBillViewModel"

sealed interface AddBillUiState {
    data object Idle : AddBillUiState
    data object Loading : AddBillUiState
    data object Saved : AddBillUiState
    data class Error(@StringRes val messageRes: Int) : AddBillUiState
}

@Serializable
data class HouseholdMemberWithProfile(val user_id: String, val profiles: ProfileNameRow? = null)

@Serializable
private data class CreateBillShareParam(val user_id: String, val value: Double)

@Serializable
private data class CreateBillParams(
    val household_id_in: String,
    val title_in: String,
    val amount_in: Double,
    val due_date_in: String,
    val split_method_in: String,
    val shares_in: List<CreateBillShareParam>
)

@Serializable
private data class CreatedBillRow(val id: String)

@HiltViewModel
class AddBillViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val attachments: AttachmentRepository,
    private val activity: ActivityLogger
) : ViewModel() {
    /**
     * Set once create_bill succeeded. If linking the photos then fails, Save
     * only retries the link, so the bill is never created twice.
     */
    private var createdBillId: String? = null
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

    /**
     * US-6: creates the bill and its shares in one create_bill call. The server
     * re-checks the split (RM-0010), so a bad client cannot store shares that
     * do not add up. [shares] come from BillSplit.compute.
     *
     * The screen validates first; these checks are a last guard so an invalid
     * bill never reaches Supabase even if the UI is bypassed (RM-0036).
     */
    fun createBill(
        title: String,
        amount: BigDecimal,
        dueDate: LocalDate,
        mode: SplitMode,
        shares: List<SplitShare>,
        photoPaths: List<String> = emptyList()
    ) {
        if (validateRequiredText(title, Limits.TITLE_MAX) != null ||
            validateAmount(amount.toPlainString()) != null ||
            validateDueDate(dueDate) != null ||
            shares.isEmpty()
        ) {
            Log.w(TAG, "createBill rejected invalid input")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = AddBillUiState.Loading
            createdBillId?.let { billId ->
                runCatching { attachments.link(AttachmentParent.Bill(billId), photoPaths) }
                    .onSuccess { _state.value = AddBillUiState.Saved }
                    .onFailure { linkFailed(it) }
                return@launch
            }
            runCatching {
                val params = CreateBillParams(
                    household_id_in = householdId,
                    title_in = title,
                    amount_in = amount.toDouble(),
                    due_date_in = "${dueDate}T00:00:00Z",
                    split_method_in = mode.wire,
                    shares_in = shares.map { CreateBillShareParam(it.userId, it.value.toDouble()) }
                )
                supabase.postgrest.rpc("create_bill", params.toRpcParams()).decodeAs<CreatedBillRow>()
            }.onSuccess { bill ->
                createdBillId = bill.id
                activity.billAdded(title, amount.toDouble(), bill.id)
                runCatching { attachments.link(AttachmentParent.Bill(bill.id), photoPaths) }
                    .onSuccess { _state.value = AddBillUiState.Saved }
                    .onFailure { linkFailed(it) }
            }.onFailure { _state.value = AddBillUiState.Error(ErrorMapper.toMessage(it)) }
        }
    }

    private fun linkFailed(cause: Throwable) {
        if (cause is kotlinx.coroutines.CancellationException) throw cause
        Log.e(TAG, "Bill saved but its photos could not be linked", cause)
        _state.value = AddBillUiState.Error(R.string.photo_error_link)
    }
}
