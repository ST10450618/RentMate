package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        topBar = { ScreenHeader(title = stringResource(R.string.nav_shopping), subtitle = pluralStringResource(R.plurals.items_count, state.visibleItems.size, state.visibleItems.size)) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_item_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(Spacing.screen)) {
            val hasStarted = rememberHasStartedLoading(state.loading)
            LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
            val genericError = state.error?.let { stringResource(it) } ?: stringResource(R.string.state_error_generic)
            when {
                state.visibleItems.isEmpty() && state.error != null ->
                    ErrorState(message = genericError, onRetry = viewModel::refresh)
                state.visibleItems.isEmpty() && (state.loading || !hasStarted) -> LoadingState()
                state.visibleItems.isEmpty() -> EmptyState(
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
                        items(state.visibleItems, key = { it.id }) { item ->
                            ShoppingItemRow(item, onToggle = { viewModel.purchase(item.id) }, onDelete = { viewModel.delete(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemRow, onToggle: () -> Unit, onDelete: () -> Unit) {
    RentMateCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // RM-0056: name the checkbox, so TalkBack says what ticking it does.
            val toggleLabel = stringResource(R.string.shopping_mark_bought, item.name)
            Checkbox(
                checked = item.is_purchased,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = toggleLabel }
            )
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.xs)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.is_purchased) TextDecoration.LineThrough else null,
                    color = if (item.is_purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.shopping_added_by, item.added_by_user_id?.display_name ?: stringResource(R.string.housemate_fallback)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // RM-0017: saved offline, not uploaded yet.
            if (item.pending) StatusPill(stringResource(R.string.sync_pending), PillTone.Neutral, modifier = Modifier.padding(end = Spacing.xs))
            if (item.is_purchased) StatusPill(stringResource(R.string.shopping_bought), PillTone.Positive)
            // RM-0056: delete is online only, so an item still waiting to sync has no delete button.
            if (!item.pending) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_delete, item.name))
                }
            }
        }
    }
}
