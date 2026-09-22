package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

sealed interface AddShoppingItemUiState {
    data object Idle : AddShoppingItemUiState
    data object Loading : AddShoppingItemUiState
    data object Saved : AddShoppingItemUiState
    data class Error(val message: String) : AddShoppingItemUiState
}

@Serializable
private data class ShoppingItemInsert(val household_id: String, val name: String, val added_by_user_id: String)

@HiltViewModel
class AddShoppingItemViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold
) : ViewModel() {
    private val _state = MutableStateFlow<AddShoppingItemUiState>(AddShoppingItemUiState.Idle)
    val state: StateFlow<AddShoppingItemUiState> = _state

    // US-13: any member can add an item; the list records who added it.
    fun add(name: String) {
        if (name.isBlank()) {
            _state.value = AddShoppingItemUiState.Error("Item name can't be blank.")
            return
        }
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = AddShoppingItemUiState.Loading
            runCatching {
                supabase.from("shopping_items").insert(ShoppingItemInsert(householdId, name.trim(), userId))
            }.onSuccess { _state.value = AddShoppingItemUiState.Saved }
                .onFailure { _state.value = AddShoppingItemUiState.Error(it.message ?: "Could not add item") }
        }
    }
}
