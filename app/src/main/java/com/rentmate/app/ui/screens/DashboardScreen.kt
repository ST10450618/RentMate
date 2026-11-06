package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.HouseholdRow
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRand
import com.rentmate.app.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.ui.theme.Sizes
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "DashboardScreen"

/**
 * S3 - Home (US-3). RM-0056: the screen answers first. The top card says what
 * you owe, to whom and by when, with one button. Your next chore and the
 * household's recent activity sit below it.
 *
 * @param onOpenBills opens Money, where the user pays or confirms payments.
 * @param onOpenActivity opens the full activity feed.
 */
@Composable
fun DashboardScreen(
    onOpenBills: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenActivity: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(title = state.householdName ?: stringResource(R.string.home_title))

        val hasStarted = rememberHasStartedLoading(state.loading)
        LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
        val genericError = state.error?.let { stringResource(it) } ?: stringResource(R.string.state_error_generic)
        // RM-0016: offline the household list cannot load, but a saved household still has cached cards.
        val noData = state.households.isEmpty() && state.currentHouseholdId == null
        when {
            noData && state.error != null -> ErrorState(message = genericError, onRetry = viewModel::refresh)
            noData && (state.loading || !hasStarted) -> LoadingState()
            noData -> EmptyState(
                illustration = R.drawable.illus_empty_activity,
                title = stringResource(R.string.empty_dashboard_title),
                body = stringResource(R.string.empty_dashboard_body)
            )
            else -> HomeContent(state, genericError, viewModel::refresh, viewModel::switchHousehold, onOpenBills, onOpenChores, onOpenActivity)
        }
    }
}

@Composable
private fun HomeContent(
    state: DashboardUiState,
    genericError: String,
    onRetry: () -> Unit,
    onSwitchHousehold: (String) -> Unit,
    onOpenBills: () -> Unit,
    onOpenChores: () -> Unit,
    onOpenActivity: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        if (state.households.size > 1) {
            HouseholdSwitcher(households = state.households, currentId = state.currentHouseholdId, onSelect = onSwitchHousehold)
        }
        if (state.loading) RefreshingIndicator()
        if (state.error != null) InlineError(message = genericError, onRetry = onRetry)

        HeadlineCard(state, onOpenBills)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel(stringResource(R.string.home_this_week))
            val chore = state.nextChore
            RentMateCard(modifier = Modifier.clickable(role = Role.Button, onClick = onOpenChores)) {
                Text(
                    if (chore != null) stringResource(R.string.home_your_turn, chore.title)
                    else stringResource(R.string.dashboard_no_chore),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.dashboard_view_chores),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        RecentActivity(state.recentActivity, state.activityIsSample, onOpenActivity)
    }
}

/**
 * The answer card: the amount, the person and the date, then the screen's one
 * filled button. Overdue bills add an amber pill here, not a second card.
 */
@Composable
private fun HeadlineCard(state: DashboardUiState, onOpenBills: () -> Unit) {
    val housemate = stringResource(R.string.housemate_fallback)
    val headline = state.headline
    val accent = if (state.overdueBills.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    RentMateCard(accent = accent) {
        if (state.overdueBills.isNotEmpty()) {
            StatusPill(pluralStringResource(R.plurals.bills_overdue, state.overdueBills.size, state.overdueBills.size), PillTone.Attention)
        }
        when (headline) {
            is HomeHeadline.YouOwe -> {
                val name = state.memberNames[headline.toUserId] ?: housemate
                SectionLabel(stringResource(R.string.home_you_owe_label))
                Text(formatRand(headline.cents / 100.0), style = MaterialTheme.typography.displaySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MemberAvatar(name, userId = headline.toUserId)
                    Text(
                        headline.dueDate?.let { stringResource(R.string.home_to_by, name, formatDueDate(it)) }
                            ?: stringResource(R.string.home_to, name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (headline.otherCreditors > 0) {
                    Text(
                        pluralStringResource(R.plurals.home_also_owe, headline.otherCreditors, headline.otherCreditors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrimaryButton(stringResource(R.string.home_pay, name), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
            is HomeHeadline.OwedToYou -> {
                val name = state.memberNames[headline.fromUserId] ?: housemate
                SectionLabel(stringResource(R.string.home_owed_label))
                Text(formatRand(headline.cents / 100.0), style = MaterialTheme.typography.displaySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MemberAvatar(name, userId = headline.fromUserId)
                    Text(stringResource(R.string.home_from, name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
                if (headline.otherDebtors > 0) {
                    Text(
                        pluralStringResource(R.plurals.home_also_owed, headline.otherDebtors, headline.otherDebtors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrimaryButton(stringResource(R.string.home_open_money), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
            HomeHeadline.Settled -> {
                Text(stringResource(R.string.dashboard_settled), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_settled_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(stringResource(R.string.home_open_money), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
        }
    }
}

/** The newest few events, then a text link to the full feed. */
@Composable
private fun RecentActivity(events: List<ActivityEvent>, isSample: Boolean, onOpenActivity: () -> Unit) {
    val now = remember(events) { Instant.now() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel(stringResource(R.string.home_recent), modifier = Modifier.weight(1f))
            if (isSample) StatusPill(stringResource(R.string.activity_sample_pill), PillTone.Attention)
        }
        if (events.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.forEach { ActivityRow(it, now) }
        }
        TextButton(onClick = onOpenActivity, modifier = Modifier.heightIn(min = Sizes.minTouch)) {
            Text(stringResource(R.string.home_see_all), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Formats a yyyy-MM-dd due date as "30 Nov" in the app language. Falls back to the raw text. */
@Composable
private fun formatDueDate(isoDate: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(isoDate, locale) {
        runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM", locale)) }.getOrDefault(isoDate)
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
                modifier = Modifier.padding(end = Spacing.sm)
            )
        }
    }
}
