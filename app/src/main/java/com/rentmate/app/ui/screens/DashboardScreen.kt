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
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRand
import com.rentmate.app.ui.theme.Spacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.rentmate.app.data.activity.ActivityType
import com.rentmate.app.ui.components.ListDivider
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.theme.Sizes
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "DashboardScreen"

/**
 * S3 - Home (US-3). RM-0056: the screen answers first, as in the redesign
 * mockup. The header shows the household name above "Home", with the avatar
 * that opens Settings. The "You owe" card says how much, to whom, for what and
 * by when, with a days-left pill and one "Pay <name>" button. Below it,
 * "This week" lists your chore turn and the household's recent activity as
 * rows, each with the housemate's own avatar colour.
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
        ScreenHeader(title = stringResource(R.string.home_title), eyebrow = state.householdName)

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
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (state.households.size > 1) {
            HouseholdSwitcher(households = state.households, currentId = state.currentHouseholdId, onSelect = onSwitchHousehold)
        }
        if (state.loading) RefreshingIndicator()
        if (state.error != null) InlineError(message = genericError, onRetry = onRetry)

        HeadlineCard(state, onOpenBills)
        ThisWeek(state, onOpenChores, onOpenActivity)
    }
}

/**
 * The answer card: the amount in the figure style, then the person, what for
 * and when, then the screen's one filled button.
 */
@Composable
private fun HeadlineCard(state: DashboardUiState, onOpenBills: () -> Unit) {
    val housemate = stringResource(R.string.housemate_fallback)
    RentMateCard(accent = if (state.overdueBills.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline) {
        when (val headline = state.headline) {
            is HomeHeadline.YouOwe -> {
                val name = state.memberNames[headline.toUserId] ?: housemate
                HeadlineAmount(stringResource(R.string.home_you_owe_label), headline.cents)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MemberAvatar(name, userId = headline.toUserId)
                    Text(
                        when {
                            headline.billTitle != null && headline.dueDate != null ->
                                stringResource(R.string.home_owe_detail, name, headline.billTitle, formatDueDate(headline.dueDate))
                            else -> name
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    headline.dueDate?.let { DaysLeftPill(it) }
                }
                if (headline.otherCreditors > 0) {
                    MutedLine(pluralStringResource(R.plurals.home_also_owe, headline.otherCreditors, headline.otherCreditors))
                }
                PrimaryButton(stringResource(R.string.home_pay, name), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
            is HomeHeadline.OwedToYou -> {
                val name = state.memberNames[headline.fromUserId] ?: housemate
                HeadlineAmount(stringResource(R.string.home_owed_label), headline.cents)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MemberAvatar(name, userId = headline.fromUserId)
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                if (headline.otherDebtors > 0) {
                    MutedLine(pluralStringResource(R.plurals.home_also_owed, headline.otherDebtors, headline.otherDebtors))
                }
                PrimaryButton(stringResource(R.string.home_open_money), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
            HomeHeadline.Settled -> {
                Text(stringResource(R.string.dashboard_settled), style = MaterialTheme.typography.titleMedium)
                MutedLine(stringResource(R.string.home_settled_body))
                PrimaryButton(stringResource(R.string.home_open_money), onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
            }
        }
        if (state.overdueBills.isNotEmpty()) {
            StatusPill(pluralStringResource(R.plurals.bills_overdue, state.overdueBills.size, state.overdueBills.size), PillTone.Attention)
        }
    }
}

/** "You owe" in the muted small style, then the amount in the figure style (32 sp). */
@Composable
private fun HeadlineAmount(label: String, cents: Long) {
    MutedLine(label)
    Text(formatRand(cents / 100.0), style = MaterialTheme.typography.displaySmall)
}

@Composable
private fun MutedLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The days-left pill: "3 days", "Today" or "Overdue", amber once it is a week or less away. */
@Composable
private fun DaysLeftPill(dueDate: String) {
    val days = remember(dueDate) { daysUntil(dueDate, LocalDate.now()) } ?: return
    when {
        days < 0 -> StatusPill(stringResource(R.string.home_overdue), PillTone.Danger)
        days == 0L -> StatusPill(stringResource(R.string.home_due_today), PillTone.Attention)
        else -> StatusPill(
            pluralStringResource(R.plurals.home_days_left, days.toInt(), days.toInt()),
            if (days <= 7) PillTone.Attention else PillTone.Neutral
        )
    }
}

/**
 * "This week": your chore turn first, then the newest household events, as
 * rows in one group. A text button under the group opens the full feed.
 */
@Composable
private fun ThisWeek(state: DashboardUiState, onOpenChores: () -> Unit, onOpenActivity: () -> Unit) {
    val now = remember(state.recentActivity) { Instant.now() }
    val idsByName = remember(state.memberNames) { state.memberNames.entries.associate { (id, name) -> name to id } }
    val me = state.myUserId
    val myName = me?.let { state.memberNames[it] } ?: stringResource(R.string.housemate_fallback)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ListHeading(stringResource(R.string.home_this_week), modifier = Modifier.weight(1f))
            if (state.activityIsSample) StatusPill(stringResource(R.string.activity_sample_pill), PillTone.Attention)
        }
        ListGroup {
            val chore = state.nextChore
            ListRow(
                title = if (chore != null) stringResource(R.string.home_your_turn, chore.title) else stringResource(R.string.dashboard_no_chore),
                leading = { MemberAvatar(myName, userId = me) },
                onClick = onOpenChores
            )
            state.recentActivity.forEach { event ->
                ListDivider()
                ListRow(
                    title = stringResource(R.string.activity_event, event.actorName, event.summary),
                    supporting = relativeTimeLabel(relativeTime(event.createdAt, now)),
                    leading = { MemberAvatar(event.actorName, userId = idsByName[event.actorName]) },
                    trailing = activityPill(event.type)
                )
            }
        }
        TextButton(onClick = onOpenActivity, modifier = Modifier.heightIn(min = Sizes.minTouch)) {
            Text(stringResource(R.string.home_see_all), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A "Done" pill on events that finished something, like the mockup's "Sipho fixed the tap". */
private fun activityPill(type: ActivityType): (@Composable () -> Unit)? = when (type) {
    ActivityType.CHORE_COMPLETED, ActivityType.BILL_PAID, ActivityType.SHOPPING_BOUGHT ->
        { { StatusPill(stringResource(R.string.chores_done), PillTone.Positive) } }
    else -> null
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
