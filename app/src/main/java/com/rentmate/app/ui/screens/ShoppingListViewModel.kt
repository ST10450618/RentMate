package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.data.ShoppingRepository
import com.rentmate.app.data.WriteResult
import android.util.Log
import com.rentmate.app.data.activity.ActivityLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter
import com.rentmate.app.ui.feedback.UndoQueue
import com.rentmate.app.ui.feedback.UndoableAction
import com.rentmate.app.ui.feedback.UserMessage

data class ShoppingListUiState(
    val items: List<ShoppingItemRow> = emptyList(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null,
    /** RM-0056: items deleted but still inside their Undo window. They are hidden. */
    val hiddenItems: Set<String> = emptySet()
) {
    /** The items the screen lists: everything except those waiting to be deleted. */
    val visibleItems: List<ShoppingItemRow> get() = items.filterNot { it.id in hiddenItems }
}

private const val TAG = "ShoppingListViewModel"

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val activity: ActivityLogger,
    private val shoppingRepository: ShoppingRepository,
    private val feedback: FeedbackCenter,
    private val undoQueue: UndoQueue
) : ViewModel() {
    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state

    init {
        // RM-0016: the list always comes from the Room cache, so it survives a failed refresh.
        viewModelScope.launch {
            shoppingRepository.items
                .catch { Log.e(TAG, "could not read cached shopping items", it) }
                .collect { items -> _state.update { it.copy(items = items) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            Log.d(TAG, "refresh household=$householdId")
            runCatching { shoppingRepository.refresh() }
                .onSuccess { _state.update { it.copy(loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = ErrorMapper.toMessage(e)) } }
        }
    }

    /**
     * Marks an item bought. RM-0017: offline the change is queued and the row
     * shows as pending; the repository refreshes the cache after an online save.
     */
    fun purchase(id: String) {
        val name = _state.value.items.firstOrNull { it.id == id }?.name
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            runCatching { shoppingRepository.markPurchased(id, userId) }
                .onSuccess { result ->
                    when (result) {
                        is WriteResult.Sent -> if (name != null) activity.shoppingBought(name, id)
                        // RM-0018: SyncWorker posts shoppingBought once the queued purchase uploads.
                        is WriteResult.Queued -> Log.d(TAG, "purchase of $id queued offline")
                    }
                }
                .onFailure { feedback.failure(it, R.string.feedback_mark_bought_failed, retry = { purchase(id) }) }
        }
    }

    /**
     * RM-0056: deletes an item. It disappears at once and a snackbar offers
     * Undo for 5 s; the server delete runs only after that. See [UndoQueue].
     * Delete is online only: offline the item comes back with a Retry snackbar.
     */
    fun delete(id: String) {
        Log.d(TAG, "delete $id, waiting for Undo")
        undoQueue.submit(
            UserMessage(R.string.undo_item_deleted),
            UndoableAction(
                key = "delete:$id",
                hide = { _state.update { it.copy(hiddenItems = it.hiddenItems + id) } },
                restore = { _state.update { it.copy(hiddenItems = it.hiddenItems - id) } },
                commit = {
                    shoppingRepository.deleteItem(id)
                    _state.update { it.copy(hiddenItems = it.hiddenItems - id) }
                },
                onCommitFailed = {
                    if (it is IllegalStateException) feedback.show(R.string.feedback_delete_item_pending)
                    else feedback.failure(it, R.string.feedback_delete_item_failed, retry = { delete(id) })
                }
            )
        )
    }
}
