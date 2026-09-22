package com.rentmate.app.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.ShoppingItemRow
import com.rentmate.app.ui.components.ScreenHeader

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
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn {
                items(state.items, key = { it.id }) { item ->
                    ShoppingItemRow(item, onToggle = { viewModel.purchase(item.id) })
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemRow, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Checkbox(checked = item.is_purchased, onCheckedChange = { onToggle() })
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text("added by ${item.added_by_user_id?.display_name ?: "?"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
