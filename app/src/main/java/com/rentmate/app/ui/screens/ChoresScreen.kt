package com.rentmate.app.ui.screens

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.ChoreForecastRow
import com.rentmate.app.data.ChoreRow
import com.rentmate.app.data.LeaderboardRow
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.ListAmount
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.rememberHasStartedLoading
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "ChoresScreen"

/** Date patterns for the day pills. They are locale-aware patterns, not user-facing text. */
private const val PATTERN_DAY_LONG = "EEEE"
private const val PATTERN_DAY_SHORT = "EEE"
private const val PATTERN_WEEK_DAY = "EEE d"
private const val PATTERN_MONTH_DAY = "d MMM"

/**
 * S6, Chores (US-9, US-12), redesigned for RM-0058.
 *
 * The screen opens with "Your next chore" and its one Mark done button. Below
 * it, "This week" lists every chore with the assignee's avatar, the points and
 * a day pill or a Done pill, so nobody has to remember whose turn it is. The
 * four-week forecast (forecast_chore, RM-0007) and the points leaderboard
 * start collapsed. The leaderboard row is left out when the household hides
 * it (RM-0008). Marking a chore done shows a 5 s Undo snackbar (RM-0056).
 */
@Composable
fun ChoresScreen(onAddChore: () -> Unit, viewModel: ChoresViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.nav_chores))
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
                actionLabel = stringResource(R.string.add_chore_title),
                onAction = onAddChore
            )
            else -> ChoresContent(
                state = state,
                errorMessage = if (state.error != null) genericError else null,
                onRetry = viewModel::refresh,
                onComplete = viewModel::complete,
                onAddChore = onAddChore
            )
        }
    }
}

/**
 * The filled Chores screen: the next-chore card, the "This week" list and the
 * two collapsed sections. Expanded state survives rotation.
 */
@Composable
private fun ChoresContent(
    state: ChoresUiState,
    errorMessage: String?,
    onRetry: () -> Unit,
    onComplete: (String) -> Unit,
    onAddChore: () -> Unit
) {
    var forecastOpen by rememberSaveable { mutableStateOf(false) }
    var leaderboardOpen by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val names = rememberNames(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            bottom = Spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (state.loading) item(key = "refreshing") { RefreshingIndicator() }
        if (errorMessage != null) item(key = "error") { InlineError(message = errorMessage, onRetry = onRetry) }

        item(key = "next") {
            NextChoreCard(
                chore = state.nextChore(today),
                pendingDone = state.pendingDone,
                today = today,
                onComplete = onComplete
            )
        }

        item(key = "week-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListHeading(stringResource(R.string.chores_this_week), modifier = Modifier.weight(1f))
                TextButton(onClick = onAddChore) { Text(stringResource(R.string.add_chore_title)) }
            }
        }
        item(key = "week") {
            ListGroup(state.chores) { chore ->
                WeekRow(chore, state, names, today, onComplete)
            }
        }

        item(key = "toggles") {
            ListGroup {
                ToggleRow(R.string.chores_next_four_weeks, forecastOpen) {
                    forecastOpen = !forecastOpen
                    Log.d(TAG, "forecast open=$forecastOpen")
                }
            }
        }
        if (forecastOpen) forecastItems(state, names, today)

        // RM-0008: when the household hides the leaderboard, the row is left out entirely.
        if (state.showLeaderboard) {
            item(key = "leaderboard-toggle") {
                ListGroup {
                    ToggleRow(R.string.chores_points_this_month, leaderboardOpen) {
                        leaderboardOpen = !leaderboardOpen
                        Log.d(TAG, "leaderboard open=$leaderboardOpen")
                    }
                }
            }
            val board = state.leaderboard
            if (leaderboardOpen && board != null) {
                item(key = "leaderboard") { LeaderboardList(board, state.currentUserId) }
            }
        }
    }
}

/** Names by user id, with "You" for the signed-in user. */
@Composable
private fun rememberNames(state: ChoresUiState): (String?) -> String {
    val you = stringResource(R.string.chores_you)
    val fallback = stringResource(R.string.housemate_fallback)
    return { id ->
        when {
            id == null -> fallback
            id == state.currentUserId -> you
            else -> state.memberNames[id] ?: fallback
        }
    }
}

/**
 * "Your next chore": the chore name, a day pill, the points and frequency, and
 * the screen's one filled button. While the Undo window is open the button
 * gives way to a Done pill. With no chore due, a short line says so.
 */
@Composable
private fun NextChoreCard(chore: ChoreRow?, pendingDone: Set<String>, today: LocalDate, onComplete: (String) -> Unit) {
    RentMateCard {
        Text(
            stringResource(R.string.chores_your_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (chore == null) {
            Text(stringResource(R.string.chores_none_for_you), style = MaterialTheme.typography.bodyLarge)
            return@RentMateCard
        }
        Text(chore.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StatusPill(dayLabel(chore.recurrence, today, long = true), PillTone.Attention)
            Text(
                stringResource(
                    R.string.chores_meta,
                    pluralStringResource(R.plurals.chores_points_long, chore.point_value, chore.point_value),
                    stringResource(recurrenceLabel(chore.recurrence))
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (chore.id in pendingDone) {
            StatusPill(stringResource(R.string.chores_done), PillTone.Positive, modifier = Modifier.padding(top = Spacing.sm))
        } else {
            PrimaryButton(
                text = stringResource(R.string.chores_mark_done),
                onClick = { onComplete(chore.id) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}

/**
 * One "This week" row: the assignee's avatar, the chore, "name · points" and a
 * day pill, or a Done pill once the chore is done this cycle. Tapping a row
 * marks it done; for someone else's chore the RM-0048 message shows instead.
 */
@Composable
private fun WeekRow(
    chore: ChoreRow,
    state: ChoresUiState,
    names: (String?) -> String,
    today: LocalDate,
    onComplete: (String) -> Unit
) {
    val done = state.isDone(chore.id)
    // A done chore has already moved to the next person, so show who did it.
    val personId = state.doneBy[chore.id] ?: chore.currentAssignee()
    val person = if (personId == null) stringResource(R.string.chores_unassigned) else names(personId)
    ListRow(
        title = chore.title,
        supporting = stringResource(
            R.string.chores_meta,
            person,
            pluralStringResource(R.plurals.chores_points_long, chore.point_value, chore.point_value)
        ),
        leading = { MemberAvatar(person, size = Sizes.avatarHeader, userId = personId) },
        trailing = {
            if (done) {
                StatusPill(stringResource(R.string.chores_done), PillTone.Positive)
            } else {
                StatusPill(dayLabel(chore.recurrence, today, long = false), PillTone.Neutral)
            }
        },
        onClick = if (done) null else ({ onComplete(chore.id) })
    )
}

/** A collapsed-section row: the section name and "Show" or "Hide". */
@Composable
private fun ToggleRow(@StringRes title: Int, open: Boolean, onToggle: () -> Unit) {
    ListRow(
        title = stringResource(title),
        trailing = {
            Text(
                stringResource(if (open) R.string.chores_hide else R.string.chores_show),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        onClick = onToggle
    )
}

/**
 * The expanded "Next 4 weeks": for each chore a heading with its frequency and
 * one row per cycle from forecast_chore, so every phone shows the same turns.
 */
private fun LazyListScope.forecastItems(state: ChoresUiState, names: (String?) -> String, today: LocalDate) {
    state.chores.forEach { chore ->
        val rows = state.forecasts[chore.id].orEmpty()
        if (rows.isEmpty()) return@forEach
        item(key = "forecast-${chore.id}") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ListHeading(
                    stringResource(R.string.chores_meta, chore.title, stringResource(recurrenceLabel(chore.recurrence)))
                )
                ListGroup(rows) { row -> ForecastRow(chore, row, names, today) }
            }
        }
    }
}

/** One forecast row: who takes the turn, which cycle it is and its last day. */
@Composable
private fun ForecastRow(chore: ChoreRow, row: ChoreForecastRow, names: (String?) -> String, today: LocalDate) {
    val name = names(row.assignee_user_id)
    val ahead = row.cycle_number - 1
    val due = cycleDueDate(chore.recurrence, today, ahead)
    ListRow(
        title = cycleLabel(chore.recurrence, ahead),
        leading = { MemberAvatar(name, size = Sizes.avatarHeader, userId = row.assignee_user_id) },
        supporting = name,
        trailing = {
            if (due != null) {
                val pattern = if (chore.recurrence == "Weekly") PATTERN_WEEK_DAY else PATTERN_MONTH_DAY
                Text(
                    due.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** The expanded "Points this month": one row per housemate, most points first. */
@Composable
private fun LeaderboardList(board: List<LeaderboardRow>, currentUserId: String?) {
    val you = stringResource(R.string.chores_you)
    ListGroup(board.sortedByDescending { it.month_points }) { entry ->
        ListRow(
            title = if (entry.user_id == currentUserId) you else entry.display_name,
            leading = { MemberAvatar(entry.display_name, size = Sizes.avatarHeader, userId = entry.user_id) },
            trailing = { ListAmount(entry.month_points.toString()) }
        )
    }
}

/** "This week", "Next week", "In 3 weeks", or the month or turn versions. */
@Composable
private fun cycleLabel(recurrence: String, ahead: Int): String = when (recurrence) {
    "Weekly" -> when (ahead) {
        0 -> stringResource(R.string.chores_this_week)
        1 -> stringResource(R.string.chores_next_week)
        else -> pluralStringResource(R.plurals.chores_in_weeks, ahead, ahead)
    }
    "Monthly" -> when (ahead) {
        0 -> stringResource(R.string.chores_this_month)
        1 -> stringResource(R.string.chores_next_month)
        else -> pluralStringResource(R.plurals.chores_in_months, ahead, ahead)
    }
    else -> stringResource(R.string.chores_turn, ahead + 1)
}

/**
 * The day pill text. A weekly chore shows the weekday its cycle ends, a
 * monthly chore the date, and a one-off chore "Any time".
 */
@Composable
private fun dayLabel(recurrence: String, today: LocalDate, long: Boolean): String {
    val due = cycleDueDate(recurrence, today) ?: return stringResource(R.string.chores_any_time)
    val pattern = when {
        recurrence != "Weekly" -> PATTERN_MONTH_DAY
        long -> PATTERN_DAY_LONG
        else -> PATTERN_DAY_SHORT
    }
    return due.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** The translated label for a chores.recurrence value. */
@StringRes
private fun recurrenceLabel(recurrence: String): Int = when (recurrence) {
    "Weekly" -> R.string.recurrence_weekly
    "Monthly" -> R.string.recurrence_monthly
    else -> R.string.recurrence_once
}
