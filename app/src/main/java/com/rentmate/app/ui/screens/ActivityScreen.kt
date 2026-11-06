package com.rentmate.app.ui.screens

import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.PillTone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityType
import com.rentmate.app.ui.components.AdaptiveList
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.fullWidthItem
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.theme.RentMateTheme
import com.rentmate.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Household activity feed: what housemates did recently, newest first, grouped by day. */
@Composable
fun ActivityScreen(viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    ActivityContent(state, onRetry = viewModel::retry)
}

@Composable
private fun ActivityContent(state: ActivityUiState, now: Instant = Instant.now(), onRetry: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.activity_title),
            subtitle = stringResource(R.string.activity_subtitle)
        )
        when (state) {
            ActivityUiState.Loading -> LoadingState()
            // RM-0035: same retry error state as the other list screens.
            is ActivityUiState.Error -> ErrorState(
                message = stringResource(R.string.activity_error),
                onRetry = onRetry
            )
            is ActivityUiState.Events -> {
                if (state.isSample) SampleDataBanner()
                if (state.events.isEmpty()) ActivityEmpty() else ActivityList(state.events, now)
            }
        }
    }
}

/**
 * Shown while the feed uses the stand-in repository (until RM-0032), so the sample
 * housemates and events are never mistaken for the household's real activity.
 */
@Composable
private fun SampleDataBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(stringResource(R.string.activity_sample_pill), PillTone.Attention)
        Text(
            stringResource(R.string.activity_sample_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = Spacing.sm)
        )
    }
}

/** RM-0056: the shared EmptyState, with the activity illustration and no action. */
@Composable
private fun ActivityEmpty() {
    EmptyState(
        illustration = R.drawable.illus_empty_activity,
        title = stringResource(R.string.activity_empty_title),
        body = stringResource(R.string.activity_empty_body)
    )
}

@Composable
private fun ActivityList(events: List<ActivityEvent>, now: Instant) {
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(events, now) { groupByDay(events, LocalDate.now(zone), zone) }
    // RM-0056: two columns of events at 600 dp and wider; day headers span both.
    AdaptiveList(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen)
    ) {
        groups.forEach { group ->
            fullWidthItem(key = "header-${group.bucket}") { SectionLabel(dayLabel(group.bucket)) }
            items(group.events, key = { it.id }) { ActivityRow(it, now) }
        }
    }
}

@Composable
private fun dayLabel(bucket: DayBucket): String = when (bucket) {
    DayBucket.Today -> stringResource(R.string.activity_today)
    DayBucket.Yesterday -> stringResource(R.string.activity_yesterday)
    is DayBucket.On -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(bucket.date)
}

/** The "5 min ago" label for an event. Home reuses it (RM-0056). */
@Composable
internal fun relativeTimeLabel(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.activity_just_now)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.activity_minutes_ago, time.count, time.count)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.activity_hours_ago, time.count, time.count)
    is RelativeTime.Days -> pluralStringResource(R.plurals.activity_days_ago, time.count, time.count)
}

/** One event: who did what, and how long ago. Home reuses it for its recent activity (RM-0056). */
@Composable
internal fun ActivityRow(event: ActivityEvent, now: Instant) {
    RentMateCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(event.actorName)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.activity_event, event.actorName, event.summary), style = MaterialTheme.typography.bodyLarge)
                Text(
                    relativeTimeLabel(relativeTime(event.createdAt, now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(iconFor(event.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun iconFor(type: ActivityType): ImageVector = when (type) {
    ActivityType.BILL_ADDED -> Icons.Filled.ReceiptLong
    ActivityType.BILL_PAID -> Icons.Filled.Payments
    ActivityType.CHORE_COMPLETED -> Icons.Filled.CheckCircle
    ActivityType.SHOPPING_ADDED -> Icons.Filled.ShoppingCart
    ActivityType.SHOPPING_BOUGHT -> Icons.Filled.ShoppingBasket
    ActivityType.MAINTENANCE_LOGGED -> Icons.Filled.Build
    ActivityType.MAINTENANCE_STATUS -> Icons.Filled.Sync
}

@Preview(showBackground = true)
@Composable
private fun ActivityScreenPreview() {
    val now = Instant.now()
    RentMateTheme {
        ActivityContent(
            ActivityUiState.Events(
                listOf(
                    ActivityEvent("1", ActivityType.BILL_ADDED, "Thandi", "added a bill: Electricity R1 200", null, now.minusSeconds(300)),
                    ActivityEvent("2", ActivityType.CHORE_COMPLETED, "Sipho", "completed: Take out bins", null, now.minusSeconds(86_400 * 2L))
                )
            ),
            now
        )
    }
}
