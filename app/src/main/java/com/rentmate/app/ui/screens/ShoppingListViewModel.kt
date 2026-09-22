package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ShoppingItemRow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ShoppingListUiState(
    val items: List<ShoppingItemRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

// Explicit column list (no "*"): the raw FK columns would otherwise collide
// with the aliased embeds below, since both want the same JSON key.
private val SHOPPING_ITEM_COLUMNS = Columns.raw(
    "id, household_id, name, is_purchased, purchased_at, converted_to_bill_id, created_at, " +
        "added_by_user_id:profiles!added_by_user_id(display_name), " +
        "purchased_by_user_id:profiles!purchased_by_user_id(display_name)"
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow(ShoppingListUiState())
    val state: StateFlow<ShoppingListUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                supabase.from("shopping_items")
                    .select(columns = SHOPPING_ITEM_COLUMNS) { filter { eq("household_id", householdId) } }
                    .decodeList<ShoppingItemRow>()
            }.onSuccess { _state.value = _state.value.copy(loading = false, items = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun purchase(id: String) {
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
            }.onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}
