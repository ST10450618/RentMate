package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
import android.util.Log
import androidx.compose.ui.res.stringResource
import com.rentmate.app.R
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.rememberHasStartedLoading
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.ChoreForecastRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.LeaderboardRow
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "ChoresScreen"

// S6 - roster, four-week rotation forecast and points (US-9, US-12).
@Composable
fun ChoresScreen(onAddChore: () -> Unit, viewModel: ChoresViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { ScreenHeader(title = stringResource(R.string.nav_chores), subtitle = stringResource(R.string.chores_subtitle)) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChore) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_chore_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(Spacing.screen)) {
            val hasStarted = rememberHasStartedLoading(state.loading)
            LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
            val genericError = state.error?.let { stringResource(it) } ?: stringResource(R.string.state_error_generic)
            when {
                state.chores.isEmpty() && state.error != null ->
                    ErrorState(message = genericError, onRetry = viewModel::refresh)
                state.chores.isEmpty() && (state.loading || !hasStarted) -> LoadingState()
                state.chores.isEmpty() -> EmptyState(
                    illustration = R.drawable.illus_empty_chores,
                    title = stringResource(R.string.empty_chores_title),
                    body = stringResource(R.string.empty_chores_body),
                    actionLabel = stringResource(R.string.empty_chores_action),
                    onAction = onAddChore
                )
                else -> {
                    if (state.loading) RefreshingIndicator()
                    if (state.error != null) InlineError(message = genericError, onRetry = viewModel::refresh)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(state.chores, key = { it.id }) { chore ->
                            ChoreRow(chore, state.memberNames, state.forecasts[chore.id].orEmpty(), onComplete = { viewModel.complete(chore.id) })
                        }
                        if (state.showLeaderboard) {
                            state.leaderboard?.let { item { LeaderboardCard(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoreRow(
    chore: ChoreRow,
    memberNames: Map<String, String>,
    forecast: List<ChoreForecastRow>,
    onComplete: () -> Unit
) {
    RentMateCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(chore.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(pluralStringResource(R.plurals.chore_points, chore.point_value, chore.point_value), PillTone.Positive)
        }
        val housemate = stringResource(R.string.housemate_fallback)
        val current = chore.currentAssignee()?.let { memberNames[it] ?: housemate }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
            MemberAvatar(current ?: "?", size = 28.dp)
            Text(
                stringResource(R.string.chores_this_cycle, current ?: stringResource(R.string.chores_unassigned)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }

        // Forecast comes from the server's forecast_chore RPC (RM-0007), so every phone shows the same schedule.
        if (forecast.isNotEmpty()) {
            SectionLabel(stringResource(R.string.chores_next_cycles))
            forecast.forEach { row ->
                Text(stringResource(R.string.chores_forecast_row, row.cycle_number, memberNames[row.assignee_user_id] ?: housemate), style = MaterialTheme.typography.bodySmall)
            }
        }
        SecondaryButton(stringResource(R.string.chores_mark_done), onClick = onComplete, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
    }
}

@Composable
private fun LeaderboardCard(leaderboard: List<LeaderboardRow>) {
    RentMateCard(accent = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = Spacing.sm)) {
        SectionLabel(stringResource(R.string.chores_leaderboard), color = MaterialTheme.colorScheme.primary)
        leaderboard.sortedByDescending { it.month_points }.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = Spacing.sm))
                MemberAvatar(entry.display_name, size = 28.dp)
                Text(entry.display_name, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
                Text(
                    pluralStringResource(R.plurals.leaderboard_points, entry.month_points.toInt(), entry.month_points, entry.total_points),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
