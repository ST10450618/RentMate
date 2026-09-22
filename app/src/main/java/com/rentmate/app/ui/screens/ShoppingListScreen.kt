package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.network.ShoppingItemResponse

// S8 - shared shopping list, tick off as bought (US-13).
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var newItem by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Shopping list", style = MaterialTheme.typography.headlineSmall)

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                label = { Text("Add item") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = { viewModel.add(newItem); newItem = "" },
            enabled = newItem.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add") }

        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn {
            items(state.items, key = { it.id }) { item ->
                ShoppingItemRow(item, onToggle = { viewModel.purchase(item.id) })
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: ShoppingItemResponse, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Checkbox(checked = item.isPurchased, onCheckedChange = { onToggle() })
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text("added by ${item.addedByDisplayName}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
