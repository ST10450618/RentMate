package com.rentmate.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.ui.components.ScreenHeader

// S3 - dashboard: household switcher, overdue-bill alert, next chore, balance summary (US-3).
@Composable
fun DashboardScreen(
    onOpenBills: () -> Unit,
    onOpenChores: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "Dashboard",
            subtitle = if (state.households.isNotEmpty()) "${state.households.size} household(s)" else null
        )

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.households.size > 1) {
                HouseholdSwitcher(
                    households = state.households,
                    currentId = state.currentHouseholdId,
                    onSelect = viewModel::switchHousehold
                )
            }

            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (state.overdueBills.isNotEmpty()) {
                DashboardCard(borderColor = MaterialTheme.colorScheme.secondary) {
                    Text(
                        "${state.overdueBills.size} overdue bill(s)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    state.overdueBills.forEach { Text(it.title) }
                    Button(onClick = onOpenBills) { Text("View bills") }
                }
            }

            DashboardCard {
                Text("YOUR BALANCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.owedToYou > 0) {
                    Text(
                        "You're owed R${"%.2f".format(state.owedToYou)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.youOwe > 0) {
                    Text(
                        "You owe R${"%.2f".format(state.youOwe)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.owedToYou == 0.0 && state.youOwe == 0.0) {
                    Text("You're all settled up", style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = onOpenBills) { Text("Settle up") }
            }

            DashboardCard {
                Text("YOUR NEXT CHORE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val chore = state.nextChore
                Text(
                    chore?.title ?: "Nothing assigned to you right now.",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = onOpenChores) { Text("View chores") }
            }

            if (state.members.isNotEmpty()) {
                DashboardCard {
                    Text("HOUSEHOLD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.members.forEach { name -> MemberAvatar(name) }
                        Text(state.members.joinToString(", "), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardCard(
    borderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun HouseholdSwitcher(
    households: List<HouseholdRow>,
    currentId: String?,
    onSelect: (String) -> Unit
) {
    LazyRow {
        items(households, key = { it.id }) { household ->
            FilterChip(
                selected = household.id == currentId,
                onClick = { onSelect(household.id) },
                label = { Text(household.name) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
