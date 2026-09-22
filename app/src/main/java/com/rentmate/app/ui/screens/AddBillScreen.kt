package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import java.time.LocalDate

// US-6: title, amount in ZAR, due date; split equally across the household.
@Composable
fun AddBillScreen(onClose: () -> Unit, viewModel: AddBillViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }

    LaunchedEffect(state) {
        if (state is AddBillUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add bill", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (ZAR)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due date (yyyy-mm-dd)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Split equally across ${members.size} member(s)", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        val parsedAmount = amount.toDoubleOrNull()
        val parsedDate = runCatching { LocalDate.parse(dueDate) }.getOrNull()
        Button(
            onClick = {
                if (parsedAmount != null && parsedDate != null) {
                    viewModel.createEqualSplit(title, parsedAmount, parsedDate)
                }
            },
            enabled = title.isNotBlank() && parsedAmount != null && parsedAmount > 0 &&
                parsedDate != null && members.isNotEmpty() && state !is AddBillUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        when (val s = state) {
            is AddBillUiState.Loading -> Row { CircularProgressIndicator() }
            is AddBillUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
