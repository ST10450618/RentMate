package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.data.ShoppingRepository
import android.util.Log
import com.rentmate.app.data.activity.ActivityLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper
import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter

data class ShoppingListUiState(
    val items: List<ShoppingItemRow> = emptyList(),
    val loading: Boolean = false,
    @StringRes val error: Int? = null
)

private const val TAG = "ShoppingListViewModel"

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val activity: ActivityLogger,
    private val shoppingRepository: ShoppingRepository,
    private val feedback: FeedbackCenter
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

    fun purchase(id: String) {
        val name = _state.value.items.firstOrNull { it.id == id }?.name
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            runCatching {
                supabase.from("shopping_items").update({
                    set("is_purchased", true)
                    set("purchased_by_user_id", userId)
                    set("purchased_at", Instant.now().toString())
                }) {
                    filter { eq("id", id) }
                }
            }.onSuccess {
                if (name != null) activity.shoppingBought(name, id)
                refresh()
            }.onFailure { feedback.failure(it, R.string.feedback_mark_bought_failed, retry = { purchase(id) }) }
        }
    }
}
