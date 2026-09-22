package com.rentmate.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rentmate.app.data.CurrentHousehold
import com.rentmate.app.network.AddShoppingItemRequest
import com.rentmate.app.network.PurchaseShoppingItemRequest
import com.rentmate.app.network.ShoppingItemResponse
import com.rentmate.app.network.ShoppingListApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ShoppingListUiState(
    val items: List<ShoppingItemResponse> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val api: ShoppingListApi,
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
            runCatching { api.list(householdId) }
                .onSuccess { _state.value = _state.value.copy(loading = false, items = it) }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun add(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val householdId = currentHousehold.id.first() ?: return@launch
            runCatching { api.add(householdId, AddShoppingItemRequest(name.trim())) }
                .onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    /** US-13: an item bought can optionally become a bill with the buyer as payer. */
    fun purchase(id: String, convertToBill: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                api.purchase(id, PurchaseShoppingItemRequest(Instant.now().toString(), convertToBill))
            }.onSuccess { refresh() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}
