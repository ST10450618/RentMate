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
import androidx.compose.material3.FilterChip
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

private val RECURRENCE_OPTIONS = listOf("Once", "Weekly", "Monthly")

// US-9/US-12: chore title, recurrence and point value; rotation order follows household membership.
@Composable
fun AddChoreScreen(onClose: () -> Unit, viewModel: AddChoreViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsState()
    val state by viewModel.state.collectAsState()

    var title by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("Weekly") }
    var pointValue by remember { mutableStateOf("1") }

    LaunchedEffect(state) {
        if (state is AddChoreUiState.Saved) onClose()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add chore", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text("Recurrence", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RECURRENCE_OPTIONS.forEach { option ->
                FilterChip(
                    selected = recurrence == option,
                    onClick = { recurrence = option },
                    label = { Text(option) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pointValue,
            onValueChange = { pointValue = it },
            label = { Text("Points") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Rotates across ${members.size} member(s)", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        val points = pointValue.toIntOrNull()
        Button(
            onClick = { if (points != null) viewModel.create(title, recurrence, points) },
            enabled = title.isNotBlank() && points != null && points > 0 &&
                members.isNotEmpty() && state !is AddChoreUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        when (val s = state) {
            is AddChoreUiState.Loading -> Row { CircularProgressIndicator() }
            is AddChoreUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
