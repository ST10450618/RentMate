package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.BillsRepository
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.NewBill
import com.rentmate.app.data.ProfileNameRow
import com.rentmate.app.data.RepeatMode
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.SplitShare
import com.rentmate.app.data.WriteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.data.activity.ActivityLogger
import com.rentmate.app.data.attachments.AttachmentParent
import com.rentmate.app.data.attachments.AttachmentRepository

private const val TAG = "AddBillViewModel"

sealed interface AddBillUiState {
    data object Idle : AddBillUiState
    data object Loading : AddBillUiState
    data object Saved : AddBillUiState
}

@Serializable
data class HouseholdMemberWithProfile(val user_id: String, val profiles: ProfileNameRow? = null)

@HiltViewModel
class AddBillViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val attachments: AttachmentRepository,
    private val activity: ActivityLogger,
    private val billsRepository: BillsRepository,
    private val feedback: FeedbackCenter
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
        // RM-0017: members come from the Room roster, so the split works offline too.
        viewModelScope.launch {
            billsRepository.members
                .catch { Log.e(TAG, "could not read cached members", it) }
                .collect { rows ->
                    _members.value = rows.map { HouseholdMemberWithProfile(it.user_id, it.display_name?.let(::ProfileNameRow)) }
                }
        }
        refreshMembers()
    }

    /** Reloads the housemates. The ErrorState's Retry button calls it again. */
    fun refreshMembers() {
        Log.d(TAG, "refreshMembers")
        rosterRefresh.value = RosterRefresh.Running
        viewModelScope.launch {
            runCatching { billsRepository.refreshMembers() }
                .onSuccess { rosterRefresh.value = RosterRefresh.Done }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Log.w(TAG, "members refresh failed, using the cached roster", it)
                    rosterRefresh.value = RosterRefresh.Failed
                }
        }
    }

    /**
     * US-6: creates the bill and its shares in one create_bill call. The server
     * re-checks the split (RM-0010), so a bad client cannot store shares that
     * do not add up. [shares] come from BillSplit.compute.
     *
     * The screen validates first; these checks are a last guard so an invalid
     * bill never reaches Supabase even if the UI is bypassed (RM-0036).
     *
     * RM-0017: with no connection the bill and its shares are saved locally as
     * pending and queued for upload. RM-0018: photos that already uploaded are
     * kept with the queued bill and linked by the sync worker.
     */
    fun createBill(
        title: String,
        amount: BigDecimal,
        dueDate: LocalDate,
        mode: SplitMode,
        shares: List<SplitShare>,
        repeat: RepeatMode = RepeatMode.Never,
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
                    .onFailure { linkFailed(it) { createBill(title, amount, dueDate, mode, shares, repeat, photoPaths) } }
                return@launch
            }
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _state.value = AddBillUiState.Idle
                feedback.show(R.string.error_session_expired)
                return@launch
            }
            runCatching {
                billsRepository.createBill(NewBill(householdId, title, amount, dueDate, mode, shares, repeat, userId, photoPaths))
            }.onSuccess { result ->
                when (result) {
                    is WriteResult.Sent -> {
                        createdBillId = result.id
                        activity.billAdded(title, amount.toDouble(), result.id)
                        runCatching { attachments.link(AttachmentParent.Bill(result.id), photoPaths) }
                            .onSuccess { _state.value = AddBillUiState.Saved }
                            .onFailure { linkFailed(it) { createBill(title, amount, dueDate, mode, shares, repeat, photoPaths) } }
                    }
                    is WriteResult.Queued -> {
                        // RM-0018: SyncWorker posts billAdded and links these photos once the bill uploads.
                        Log.d(TAG, "bill queued offline: ${result.id}, photos=${photoPaths.size}")
                        _state.value = AddBillUiState.Saved
                    }
                }
            }.onFailure {
                _state.value = AddBillUiState.Idle
                feedback.failure(
                    it, R.string.feedback_save_bill_failed,
                    retry = { createBill(title, amount, dueDate, mode, shares, repeat, photoPaths) }
                )
            }
        }
    }

    /** The bill exists; only the photos are missing. Retry links them without creating the bill again. */
    private fun linkFailed(cause: Throwable, retry: () -> Unit) {
        if (cause is kotlinx.coroutines.CancellationException) throw cause
        Log.e(TAG, "Bill saved but its photos could not be linked", cause)
        _state.value = AddBillUiState.Idle
        feedback.show(R.string.photo_error_link, retry = retry)
    }
}
