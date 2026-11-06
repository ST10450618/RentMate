package com.rentmate.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.data.ShoppingRepository
import com.rentmate.app.data.WriteResult
import com.rentmate.app.data.activity.ActivityLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateRequiredText
import javax.inject.Inject
import androidx.annotation.StringRes
import com.rentmate.app.ui.error.ErrorMapper

private const val TAG = "AddShoppingItemVM"

sealed interface AddShoppingItemUiState {
    data object Idle : AddShoppingItemUiState
    data object Loading : AddShoppingItemUiState
    data object Saved : AddShoppingItemUiState
    data class Error(@StringRes val messageRes: Int) : AddShoppingItemUiState
}

@HiltViewModel
class AddShoppingItemViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val currentHousehold: CurrentHousehold,
    private val activity: ActivityLogger,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AddShoppingItemUiState>(AddShoppingItemUiState.Idle)
    val state: StateFlow<AddShoppingItemUiState> = _state

    // US-13: any member can add an item; the list records who added it.
    // RM-0017: offline, the item is saved locally and queued for upload.
    fun add(name: String) {
        // Last guard behind the screen's inline validation (RM-0036).
        if (validateRequiredText(name, Limits.ITEM_NAME_MAX) != null) return
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            _state.value = AddShoppingItemUiState.Loading
            runCatching { shoppingRepository.addItem(householdId, name.trim(), userId) }
                .onSuccess { result ->
                    when (result) {
                        is WriteResult.Sent -> activity.shoppingAdded(name.trim(), result.id)
                        // TODO(RM-0018): post the shoppingAdded activity event once the outbox replay succeeds.
                        is WriteResult.Queued -> Log.d(TAG, "item queued offline: ${result.id}")
                    }
                    _state.value = AddShoppingItemUiState.Saved
                }
                .onFailure { _state.value = AddShoppingItemUiState.Error(ErrorMapper.toMessage(it)) }
        }
    }
}
