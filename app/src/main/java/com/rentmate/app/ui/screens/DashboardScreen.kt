package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
import android.util.Log
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.rentmate.app.ui.components.rememberHasStartedLoading
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
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

private const val TAG = "DashboardScreen"

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
            title = stringResource(R.string.nav_dashboard),
            subtitle = if (state.households.isNotEmpty()) pluralStringResource(R.plurals.households_count, state.households.size, state.households.size) else null
        )

        val hasStarted = rememberHasStartedLoading(state.loading)
        LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
        val genericError = state.error?.let { stringResource(it) } ?: stringResource(R.string.state_error_generic)
        val noData = state.households.isEmpty()
        when {
            noData && state.error != null -> ErrorState(message = genericError, onRetry = viewModel::refresh)
            noData && (state.loading || !hasStarted) -> LoadingState()
            noData -> EmptyState(
                illustration = R.drawable.illus_empty_activity,
                title = stringResource(R.string.empty_dashboard_title),
                body = stringResource(R.string.empty_dashboard_body)
            )
            else -> {
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

            if (state.loading) RefreshingIndicator()
            if (state.error != null) InlineError(message = genericError, onRetry = viewModel::refresh)

            if (state.overdueBills.isNotEmpty()) {
                RentMateCard(accent = MaterialTheme.colorScheme.secondary) {
                    StatusPill(pluralStringResource(R.plurals.bills_overdue, state.overdueBills.size, state.overdueBills.size), PillTone.Attention)
                    state.overdueBills.forEach { Text(it.title, style = MaterialTheme.typography.titleMedium) }
                    PrimaryButton(stringResource(R.string.dashboard_view_bills), onClick = onOpenBills, modifier = Modifier.padding(top = Spacing.sm))
                }
            }

            RentMateCard {
                SectionLabel(stringResource(R.string.dashboard_balance))
                if (state.owedToYou > 0) {
                    Text(
                        stringResource(R.string.dashboard_owed_to_you, formatRand(state.owedToYou)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (state.youOwe > 0) {
                    Text(
                        stringResource(R.string.dashboard_you_owe, formatRand(state.youOwe)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.owedToYou == 0.0 && state.youOwe == 0.0) {
                    Text(stringResource(R.string.dashboard_settled), style = MaterialTheme.typography.titleMedium)
                }
                SecondaryButton(stringResource(R.string.settle_up), onClick = onOpenBills, modifier = Modifier.padding(top = Spacing.sm))
            }

            RentMateCard {
                SectionLabel(stringResource(R.string.dashboard_next_chore))
                val chore = state.nextChore
                Text(
                    chore?.title ?: stringResource(R.string.dashboard_no_chore),
                    style = MaterialTheme.typography.titleMedium
                )
                SecondaryButton(stringResource(R.string.dashboard_view_chores), onClick = onOpenChores, modifier = Modifier.padding(top = Spacing.sm))
            }

            if (state.members.isNotEmpty()) {
                RentMateCard {
                    SectionLabel(stringResource(R.string.dashboard_household), color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        state.members.forEach { name -> MemberAvatar(name) }
                        Text(state.members.joinToString(", "), modifier = Modifier.padding(start = 8.dp))
                    }
                }
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
