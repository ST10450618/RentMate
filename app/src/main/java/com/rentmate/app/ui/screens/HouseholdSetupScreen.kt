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

// US-5: create a household or join one with a six-character invite code.
@Composable
fun HouseholdSetupScreen(
    onDone: () -> Unit,
    viewModel: HouseholdSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is HouseholdSetupUiState.Joined) onDone()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Set up your household", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = householdName,
            onValueChange = { householdName = it },
            label = { Text("Household name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.create(householdName) },
            enabled = householdName.isNotBlank() && state !is HouseholdSetupUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create household") }

        Spacer(Modifier.height(24.dp))
        Text("or join an existing one", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Invite code") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.join(inviteCode) },
            enabled = inviteCode.isNotBlank() && state !is HouseholdSetupUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Join household") }

        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is HouseholdSetupUiState.Loading -> Row { CircularProgressIndicator() }
            is HouseholdSetupUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is HouseholdSetupUiState.Created -> {
                Text("Household created. Invite code:", style = MaterialTheme.typography.bodyMedium)
                Text(s.inviteCode, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
            else -> {}
        }
    }
}
