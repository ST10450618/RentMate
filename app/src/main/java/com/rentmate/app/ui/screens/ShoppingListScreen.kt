package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.ui.res.stringResource
import com.rentmate.app.R
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.rememberHasStartedLoading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "ShoppingListScreen"

// S8 - shared shopping list, tick off as bought (US-13).
@Composable
fun ShoppingListScreen(onAddItem: () -> Unit, viewModel: ShoppingListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { ScreenHeader(title = "Shopping list", subtitle = "${state.items.size} item(s)") },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(Spacing.screen)) {
            val hasStarted = rememberHasStartedLoading(state.loading)
            LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
            val genericError = stringResource(R.string.state_error_generic)
            when {
                state.items.isEmpty() && state.error != null ->
                    ErrorState(message = genericError, onRetry = viewModel::refresh)
                state.items.isEmpty() && (state.loading || !hasStarted) -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    illustration = R.drawable.illus_empty_shopping,
                    title = stringResource(R.string.empty_shopping_title),
                    body = stringResource(R.string.empty_shopping_body),
                    actionLabel = stringResource(R.string.empty_shopping_action),
                    onAction = onAddItem
                )
                else -> {
                    if (state.loading) RefreshingIndicator()
                    if (state.error != null) InlineError(message = genericError, onRetry = viewModel::refresh)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.items, key = { it.id }) { item ->
                            ShoppingItemRow(item, onToggle = { viewModel.purchase(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemRow, onToggle: () -> Unit) {
    RentMateCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.is_purchased, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.xs)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.is_purchased) TextDecoration.LineThrough else null,
                    color = if (item.is_purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Added by ${item.added_by_user_id?.display_name ?: "a housemate"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.is_purchased) StatusPill("Bought", PillTone.Positive)
        }
    }
}
