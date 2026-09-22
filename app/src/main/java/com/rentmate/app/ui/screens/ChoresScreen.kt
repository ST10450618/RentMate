package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.LeaderboardRow

// S6 - roster, four-week rotation forecast and points (US-9, US-12).
@Composable
fun ChoresScreen(onAddChore: () -> Unit, viewModel: ChoresViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChore) {
                Icon(Icons.Filled.Add, contentDescription = "Add chore")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Text("Chores", style = MaterialTheme.typography.headlineSmall)

            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn {
                items(state.chores, key = { it.id }) { chore ->
                    ChoreRow(chore, state.memberNames, onComplete = { viewModel.complete(chore.id) })
                }
                if (state.showLeaderboard) {
                    state.leaderboard?.let { item { LeaderboardCard(it) } }
                }
            }
        }
    }
}

@Composable
private fun ChoreRow(chore: ChoreRow, memberNames: Map<String, String>, onComplete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chore.title, style = MaterialTheme.typography.titleMedium)
                Text("${chore.point_value} pt", style = MaterialTheme.typography.bodySmall)
            }
            val current = chore.currentAssignee()?.let { memberNames[it] ?: it }
            Text("This cycle: ${current ?: "unassigned"}", style = MaterialTheme.typography.bodyMedium)

            val forecast = chore.forecast()
            if (forecast.isNotEmpty()) {
                Text("Next 4 cycles:", style = MaterialTheme.typography.labelMedium)
                forecast.forEach { (cycle, userId) ->
                    Text("  $cycle. ${memberNames[userId] ?: userId}", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text("Mark done")
            }
        }
    }
}

@Composable
private fun LeaderboardCard(leaderboard: List<LeaderboardRow>) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Leaderboard", style = MaterialTheme.typography.titleMedium)
            leaderboard.sortedByDescending { it.month_points }.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.display_name)
                    Text("${entry.month_points} pts this month (${entry.total_points} total)")
                }
            }
        }
    }
}
