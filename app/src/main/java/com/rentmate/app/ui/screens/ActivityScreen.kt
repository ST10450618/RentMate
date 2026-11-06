package com.rentmate.app.ui.screens

import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.activity.ActivityEvent
import com.rentmate.app.data.activity.ActivityType
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.theme.RentMateTheme
import com.rentmate.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TAG = "ActivityScreen"

/**
 * Household activity feed: what housemates did recently, newest first, grouped by day.
 *
 * RM-0060: opens from See all on Home, so it has a back arrow. [onBack] defaults
 * to the system back action, so the navigation graph does not need to pass it.
 */
@Composable
fun ActivityScreen(onBack: (() -> Unit)? = null, viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    ActivityContent(
        state,
        onRetry = viewModel::retry,
        onBack = onBack ?: { dispatcher?.onBackPressed(); Unit }
    )
}

@Composable
private fun ActivityContent(
    state: ActivityUiState,
    now: Instant = Instant.now(),
    onRetry: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.activity_title), onBack = onBack)
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

/**
 * RM-0060: one heading per day (Today, Yesterday, then the date) and that
 * day's events as rows in one rounded group, as in the Activity mockup.
 */
@Composable
private fun ActivityList(events: List<ActivityEvent>, now: Instant) {
    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(events, now) { groupByDay(events, LocalDate.now(zone), zone) }
    Log.d(TAG, "showing ${events.size} events in ${groups.size} days")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        groups.forEach { group ->
            item(key = "header-${group.bucket}") { ListHeading(dayLabel(group.bucket)) }
            item(key = "group-${group.bucket}") {
                ListGroup(group.events) { ActivityRow(it, zone) }
            }
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

/** RM-0060: the part of the household an event is about, shown after the time. */
@StringRes
internal fun activityContextRes(type: ActivityType): Int = when (type) {
    ActivityType.BILL_ADDED, ActivityType.BILL_PAID -> R.string.activity_context_bills
    ActivityType.CHORE_COMPLETED -> R.string.activity_context_chores
    ActivityType.SHOPPING_ADDED, ActivityType.SHOPPING_BOUGHT -> R.string.activity_context_shopping
    ActivityType.MAINTENANCE_LOGGED, ActivityType.MAINTENANCE_STATUS -> R.string.activity_context_repairs
}

/**
 * RM-0060: the status pill at the end of a row, for events that finish
 * something, or null when the sentence says it all.
 */
internal fun activityStatusPill(type: ActivityType): Pair<Int, PillTone>? = when (type) {
    ActivityType.BILL_PAID -> R.string.activity_pill_paid to PillTone.Positive
    ActivityType.CHORE_COMPLETED -> R.string.activity_pill_done to PillTone.Positive
    ActivityType.SHOPPING_BOUGHT -> R.string.activity_pill_bought to PillTone.Positive
    ActivityType.MAINTENANCE_STATUS -> R.string.activity_pill_updated to PillTone.Neutral
    ActivityType.BILL_ADDED, ActivityType.SHOPPING_ADDED, ActivityType.MAINTENANCE_LOGGED -> null
}

/**
 * One event as a plain sentence ("Thandi added a bill: Wi-Fi"), with the
 * actor's avatar colour and initial, a muted line with the clock time and the
 * part of the household, and a status pill when the event finished something.
 */
@Composable
internal fun ActivityRow(event: ActivityEvent, zone: ZoneId) {
    val time = remember(event.createdAt, zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(event.createdAt.atZone(zone))
    }
    val pill = activityStatusPill(event.type)
    ListRow(
        title = stringResource(R.string.activity_event, event.actorName, event.summary),
        supporting = stringResource(R.string.activity_row_meta, time, stringResource(activityContextRes(event.type))),
        leading = { MemberAvatar(event.actorName) },
        trailing = pill?.let { (res, tone) -> { StatusPill(stringResource(res), tone) } }
    )
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
