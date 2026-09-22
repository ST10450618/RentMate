package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// US-13: name only; the list records who added it.
@Composable
fun AddShoppingItemScreen(onClose: () -> Unit, viewModel: AddShoppingItemViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    var name by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is AddShoppingItemUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add item", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.add(name) },
            enabled = name.isNotBlank() && state !is AddShoppingItemUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        when (val s = state) {
            is AddShoppingItemUiState.Loading -> Row { CircularProgressIndicator() }
            is AddShoppingItemUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
