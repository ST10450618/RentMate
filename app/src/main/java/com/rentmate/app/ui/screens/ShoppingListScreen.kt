package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.rememberHasStartedLoading
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import com.rentmate.app.ui.validation.Limits
import com.rentmate.app.ui.validation.validateRequiredText

private const val TAG = "ShoppingListScreen"

/**
 * S8, the shared shopping list (US-13).
 *
 * RM-0059: the title sits on the ground under a "House" eyebrow. An "Add an
 * item" field at the top adds in place. Items still to buy come first, each
 * with who added it; a tap marks the item bought with a 5 s Undo snackbar.
 * Bought items fold away under "Bought (n)".
 *
 * @param onAddItem opens the separate Add item form. The quick-add field
 *   replaced it on this screen; the parameter stays because navigation still
 *   passes it.
 */
@Composable
fun ShoppingListScreen(
    @Suppress("UNUSED_PARAMETER") onAddItem: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
    addViewModel: AddShoppingItemViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var showBought by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_shopping),
            eyebrow = stringResource(R.string.nav_house),
            subtitle = pluralStringResource(R.plurals.house_to_buy_count, state.toBuy.size, state.toBuy.size)
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            QuickAddField(addViewModel)

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
                    body = stringResource(R.string.house_shopping_empty_body)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    if (state.loading) item { RefreshingIndicator() }
                    if (state.error != null) item { InlineError(message = genericError, onRetry = viewModel::refresh) }
                    if (state.toBuy.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.house_shopping_all_bought),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item(key = "to-buy") {
                            ListGroup(state.toBuy) { item ->
                                ShoppingItemListRow(item, bought = false, onBuy = { viewModel.purchase(item.id) }, onDelete = { viewModel.delete(item.id) })
                            }
                        }
                    }
                    if (state.bought.isNotEmpty()) {
                        item(key = "bought-toggle") {
                            BoughtToggle(count = state.bought.size, expanded = showBought, onToggle = {
                                showBought = !showBought
                                Log.d(TAG, "bought section expanded=$showBought")
                            })
                        }
                        if (showBought) {
                            item(key = "bought") {
                                ListGroup(state.bought) { item ->
                                    ShoppingItemListRow(item, bought = true, onBuy = null, onDelete = { viewModel.delete(item.id) })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Spacing.lg)) }
                }
            }
        }
    }
}

/**
 * RM-0059: the "Add an item" field that adds to the list in place. Done on
 * the keyboard or the plus button saves; the field then clears for the next
 * item. A blank or overlong name shows the RM-0036 message under the field.
 */
@Composable
private fun QuickAddField(viewModel: AddShoppingItemViewModel) {
    val saveState by viewModel.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val error = validateRequiredText(name, Limits.ITEM_NAME_MAX)
    val saving = saveState is AddShoppingItemUiState.Loading

    LaunchedEffect(saveState) {
        if (saveState is AddShoppingItemUiState.Saved) {
            Log.d(TAG, "quick add saved, clearing the field")
            name = ""
            submitted = false
            viewModel.reset()
        }
    }

    val submit = {
        submitted = true
        if (error == null && !saving) viewModel.add(name.trim())
    }
    val showError = submitted && error != null
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(Limits.ITEM_NAME_MAX + 10) },
        label = { Text(stringResource(R.string.empty_shopping_action)) },
        placeholder = { Text(stringResource(R.string.house_shopping_add_hint)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(error!!.messageRes)) }
        } else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(Sizes.spinner), strokeWidth = Sizes.stroke)
            } else {
                IconButton(onClick = submit) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_item_title))
                }
            }
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * One shopping item: the avatar and name of who added it, the
 * "Waiting to sync" pill from RM-0017 while it is queued, and a delete button.
 *
 * @param onBuy marks the item bought; null on bought rows, which are not tappable.
 */
@Composable
private fun ShoppingItemListRow(item: ShoppingItemRow, bought: Boolean, onBuy: (() -> Unit)?, onDelete: () -> Unit) {
    val addedBy = item.added_by_user_id?.display_name ?: stringResource(R.string.housemate_fallback)
    val supporting = if (bought) {
        item.purchased_by_user_id?.display_name?.let { stringResource(R.string.house_bought_by, it) }
            ?: stringResource(R.string.shopping_bought)
    } else {
        stringResource(R.string.shopping_added_by, addedBy)
    }
    ListRow(
        title = item.name,
        supporting = supporting,
        leading = { MemberAvatar(name = addedBy) },
        onClick = onBuy,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // RM-0017: saved offline, not uploaded yet.
                if (item.pending) StatusPill(stringResource(R.string.sync_pending), PillTone.Neutral)
                // RM-0056: delete is online only, so an item still waiting to sync has no delete button.
                if (!item.pending) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shopping_delete, item.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}

/** RM-0059: the folded "Bought (n)" row. A tap shows or hides the bought items. */
@Composable
private fun BoughtToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    ListGroup {
        ListRow(
            title = stringResource(R.string.house_bought_count, count),
            onClick = onToggle,
            trailing = {
                Text(
                    stringResource(if (expanded) R.string.house_hide else R.string.house_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}
