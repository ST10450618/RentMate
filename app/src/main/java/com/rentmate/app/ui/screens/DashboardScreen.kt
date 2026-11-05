package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRand
import com.rentmate.app.ui.theme.Spacing

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
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
                RentMateCard(accent = MaterialTheme.colorScheme.secondary) {
                    StatusPill("${state.overdueBills.size} overdue", PillTone.Attention)
                    state.overdueBills.forEach { Text(it.title, style = MaterialTheme.typography.titleMedium) }
                    PrimaryButton("View bills", onClick = onOpenBills, modifier = Modifier.padding(top = Spacing.sm))
                }
            }

            RentMateCard {
                SectionLabel("Your balance")
                if (state.owedToYou > 0) {
                    Text(
                        "You're owed ${formatRand(state.owedToYou)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.youOwe > 0) {
                    Text(
                        "You owe ${formatRand(state.youOwe)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.owedToYou == 0.0 && state.youOwe == 0.0) {
                    Text("You're all settled up", style = MaterialTheme.typography.titleMedium)
                }
                SecondaryButton("Settle up", onClick = onOpenBills, modifier = Modifier.padding(top = Spacing.sm))
            }

            RentMateCard {
                SectionLabel("Your next chore")
                val chore = state.nextChore
                Text(
                    chore?.title ?: "Nothing assigned to you right now.",
                    style = MaterialTheme.typography.titleMedium
                )
                SecondaryButton("View chores", onClick = onOpenChores, modifier = Modifier.padding(top = Spacing.sm))
            }

            if (state.members.isNotEmpty()) {
                RentMateCard {
                    SectionLabel("Household", color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        state.members.forEach { name -> MemberAvatar(name) }
                        Text(state.members.joinToString(", "), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
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
