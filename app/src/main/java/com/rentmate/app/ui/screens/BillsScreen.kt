package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.RepeatMode
import com.rentmate.app.data.SettleUpRow
import com.rentmate.app.data.SplitMode
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.data.isRepeating
import com.rentmate.app.ui.components.AdaptiveList
import com.rentmate.app.ui.components.AttachmentThumbnails
import com.rentmate.app.ui.components.ChoiceChips
import com.rentmate.app.ui.components.EmptyState
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.ListAmount
import com.rentmate.app.ui.components.ListDivider
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.RefreshingIndicator
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRandShort
import com.rentmate.app.ui.components.fullWidthItem
import com.rentmate.app.ui.components.rememberHasStartedLoading
import com.rentmate.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "BillsScreen"

/**
 * S4 - Money (US-6, US-11). RM-0056: bills and settle-up on one screen, as
 * rows instead of cards, like the redesign mockup.
 *
 * Filter chips (Open, Paid, Repeating) pick the bills. Each bill is one row:
 * title, a muted line such as "Monthly · split equally · 4", and the amount.
 * RM-0057: tapping a bill opens [BillDetailScreen], where the person owed
 * marks a share paid. "Settle up" lists the payments that would settle the household,
 * one row each. The person paid taps theirs to mark it received; a snackbar
 * offers Undo for 5 s. There is no settle-up dialog any more.
 *
 * At 600 dp and wider the bills and settle-up sit side by side.
 */
@Composable
fun BillsScreen(onAddBill: () -> Unit, viewModel: BillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    // RM-0057: the bill opened by tapping its row. There is no BillDetail route
    // yet, so the detail is a full-screen state of this screen.
    var openBillId by rememberSaveable { mutableStateOf<String?>(null) }
    val openBill = openBillId?.let { id -> state.bills.firstOrNull { it.id == id } }
    if (openBill != null) {
        BillDetailScreen(
            bill = openBill,
            currentUserId = state.currentUserId,
            photos = state.photos[openBill.id].orEmpty(),
            isPaid = state::isShownPaid,
            onPay = viewModel::payShare,
            onStopRepeat = viewModel::stopRepeat,
            onBack = { openBillId = null }
        )
        return
    }

    Scaffold(
        topBar = { ScreenHeader(title = stringResource(R.string.nav_money)) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBill) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_bill_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = Spacing.screen)) {
            val hasStarted = rememberHasStartedLoading(state.loading)
            LaunchedEffect(state.error) { state.error?.let { Log.w(TAG, "Load failed: $it") } }
            val genericError = state.error?.let { stringResource(it) } ?: stringResource(R.string.state_error_generic)
            when {
                state.bills.isEmpty() && state.error != null ->
                    ErrorState(message = genericError, onRetry = viewModel::refresh)
                state.bills.isEmpty() && (state.loading || !hasStarted) -> LoadingState()
                state.bills.isEmpty() -> EmptyState(
                    illustration = R.drawable.illus_empty_bills,
                    title = stringResource(R.string.empty_bills_title),
                    body = stringResource(R.string.money_empty_body),
                    actionLabel = stringResource(R.string.money_empty_action),
                    onAction = onAddBill
                )
                else -> MoneyContent(state, genericError, viewModel, onOpenBill = { openBillId = it })
            }
        }
    }
}

@Composable
private fun MoneyContent(
    state: BillsUiState,
    genericError: String,
    viewModel: BillsViewModel,
    onOpenBill: (String) -> Unit
) {
    val shown = filterBills(state.bills, state.filter, state::isShownPaid)
    // RM-0056: one column on a phone; bills and settle-up side by side from 600 dp.
    // The add button is 56 dp plus a 16 dp margin, so the list ends 96 dp above
    // the bottom: the last settle-up row stays readable when scrolled to the end.
    AdaptiveList(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xxl * 3)) {
        fullWidthItem(key = "top") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.loading) RefreshingIndicator()
                if (state.error != null) InlineError(message = genericError, onRetry = viewModel::refresh)
                ChoiceChips(
                    options = BillFilter.entries,
                    selected = state.filter,
                    onSelect = viewModel::setFilter,
                    label = { stringResource(it.labelRes) }
                )
            }
        }
        item(key = "bills") {
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.money_no_bills_in_filter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ListGroup {
                    shown.forEachIndexed { index, bill ->
                        if (index > 0) ListDivider()
                        BillRow(bill = bill, onOpen = {
                            Log.d(TAG, "open bill ${bill.id}")
                            onOpenBill(bill.id)
                        })
                    }
                }
            }
        }
        item(key = "settle") {
            SettleUpSection(state, onReceived = viewModel::confirmSettlement)
        }
    }
}

private val BillFilter.labelRes: Int
    get() = when (this) {
        BillFilter.Open -> R.string.money_filter_open
        BillFilter.Paid -> R.string.money_filter_paid
        BillFilter.Repeating -> R.string.money_filter_repeating
    }

/** The muted second line of a bill row: "Monthly · split equally · 4 · Added by Sipho" or "Due 30 Nov · Added by Sipho". */
@Composable
private fun billSummary(bill: BillWithSharesRow, issuerName: String?): String {
    val parts = mutableListOf<String>()
    if (isRepeating(bill.repeat, bill.repeat_stopped)) {
        parts += stringResource(if (RepeatMode.fromWire(bill.repeat) == RepeatMode.Weekly) R.string.repeat_weekly else R.string.repeat_monthly)
        parts += stringResource(
            when (bill.split_method) {
                SplitMode.Percentage.wire -> R.string.money_split_percent
                SplitMode.ExactAmount.wire -> R.string.money_split_exact
                else -> R.string.money_split_equally
            }
        )
        parts += bill.bill_shares.size.toString()
    } else {
        parts += stringResource(R.string.bills_due, shortDate(bill.due_date))
    }
    // RM-0057: both kinds of row say who added the bill, as in the mockup.
    issuerName?.let { parts += stringResource(R.string.money_added_by, it) }
    if (bill.pending) parts += stringResource(R.string.sync_pending)
    return parts.joinToString(" · ")
}

/** "30 Nov" in the app language, or the raw date if it cannot be read. */
@Composable
private fun shortDate(isoDate: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(isoDate, locale) {
        runCatching { LocalDate.parse(isoDate.take(10)).format(DateTimeFormatter.ofPattern("d MMM", locale)) }.getOrDefault(isoDate.take(10))
    }
}

/**
 * One bill row: title, a muted line with the due date or repeat and who added
 * it, and the bold amount. Tapping it opens [BillDetailScreen], where the
 * shares are marked paid. RM-0017: a bill saved offline says "Waiting to sync".
 */
@Composable
private fun BillRow(bill: BillWithSharesRow, onOpen: () -> Unit) {
    val issuer = bill.bill_shares.firstOrNull { it.user_id == bill.issued_by_user_id }?.profiles?.display_name
    ListRow(
        title = bill.title,
        supporting = billSummary(bill, issuer),
        emphasised = true,
        trailing = { ListAmount(formatRandShort(bill.amount)) },
        onClick = onOpen
    )
}

/**
 * "Settle up": one row per payment. "You pay Sipho" says who must confirm it;
 * "Thandi pays you" can be tapped to mark it received, with Undo.
 *
 * RM-0031: when the plan was worked out on this phone (offline, or a bill still
 * waits to sync), a line says it is a preview, each row has a Preview pill,
 * and mark received is off until the server plan replaces it.
 */
@Composable
private fun SettleUpSection(state: BillsUiState, onReceived: (fromUserId: String, amount: Double) -> Unit) {
    val me = state.currentUserId
    val provisional = state.settleUpProvisional
    val rows = visibleSettleUp(state.settleUp.orEmpty(), me, state.pendingReceived)
    LaunchedEffect(provisional) { Log.d(TAG, "settle-up provisional=$provisional") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListHeading(stringResource(R.string.settle_up))
        if (provisional) {
            Text(
                stringResource(R.string.settle_up_preview_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            state.settleUp == null && state.settleUpLoading -> RefreshingIndicator()
            rows.isEmpty() -> ListGroup { ListRow(title = stringResource(R.string.settle_up_none)) }
            else -> ListGroup(rows) { row -> SettleRow(row, settleRole(row, me), provisional, onReceived) }
        }
    }
}

/**
 * One settle-up payment as a row: avatar, who pays whom, and the amount.
 *
 * @param provisional true when the row is a local preview (RM-0031). The row
 *   then shows a Preview pill and Confirm received is not offered.
 */
@Composable
private fun SettleRow(row: SettleUpRow, role: SettleRole, provisional: Boolean, onReceived: (String, Double) -> Unit) {
    val fallback = stringResource(R.string.housemate_fallback)
    val fromName = row.from_display_name.ifBlank { fallback }
    val toName = row.to_display_name.ifBlank { fallback }
    val trailing: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ListAmount(formatRandShort(row.amount))
            if (provisional) StatusPill(stringResource(R.string.settle_up_preview_pill), PillTone.Neutral)
        }
    }
    val avatar: @Composable () -> Unit = { MemberAvatar(fromName, userId = row.from_user_id) }
    when (role) {
        SettleRole.YouPay -> ListRow(
            title = stringResource(R.string.money_you_pay, toName),
            supporting = stringResource(R.string.money_pay_then_confirm, toName),
            leading = avatar,
            trailing = trailing
        )
        SettleRole.PaysYou -> ListRow(
            title = stringResource(R.string.money_pays_you, fromName),
            // RM-0031: a preview is not the server plan, so it cannot be marked received yet.
            supporting = stringResource(if (provisional) R.string.settle_up_confirm_needs_sync else R.string.money_tap_received),
            leading = avatar,
            trailing = trailing,
            // Only the person who is owed can confirm they received it (RM-0010).
            onClick = if (provisional) null else { { onReceived(row.from_user_id, row.amount) } }
        )
        SettleRole.Between -> ListRow(
            title = stringResource(R.string.money_pays, fromName, toName),
            supporting = stringResource(R.string.bills_waiting_for, toName),
            leading = avatar,
            trailing = trailing
        )
    }
}
