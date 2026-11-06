package com.rentmate.app.ui.screens

import androidx.compose.ui.res.pluralStringResource
import android.util.Log
import androidx.compose.ui.res.stringResource
import com.rentmate.app.R
import com.rentmate.app.data.RepeatMode
import com.rentmate.app.data.isRepeating
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
import androidx.compose.material3.AlertDialog
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
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.attachments.PhotoLink
import com.rentmate.app.ui.components.AttachmentThumbnails
import com.rentmate.app.ui.components.MemberAvatar
import androidx.compose.material3.SnackbarHost
import com.rentmate.app.ui.feedback.LocalSnackbarHostState
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.PrimaryButton
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.components.formatRand
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "BillsScreen"

// S4 - bills by status, with settle-up (US-6, US-11).
@Composable
fun BillsScreen(onAddBill: () -> Unit, viewModel: BillsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { ScreenHeader(title = stringResource(R.string.nav_bills), subtitle = pluralStringResource(R.plurals.bills_count, state.bills.size, state.bills.size)) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBill) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_bill_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(Spacing.screen)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(stringResource(R.string.settle_up), onClick = { viewModel.loadSettleUp() })
            }

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
                    body = stringResource(R.string.empty_bills_body),
                    actionLabel = stringResource(R.string.empty_bills_action),
                    onAction = onAddBill
                )
                else -> {
                    if (state.loading) RefreshingIndicator()
                    if (state.error != null) InlineError(message = genericError, onRetry = viewModel::refresh)
                    LazyColumn(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(state.bills, key = { it.id }) { bill -> BillRow(bill, photos = state.photos[bill.id].orEmpty(), canMarkPaid = bill.issued_by_user_id == state.currentUserId && !bill.pending, onPay = viewModel::payShare, onStopRepeat = viewModel::stopRepeat) }
                    }
                }
            }
        }
    }

    if (state.settleUp != null || state.settleUpLoading) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSettleUp() },
            title = { Text(stringResource(R.string.settle_up)) },
            text = {
              Column {
                if (state.settleUpLoading) {
                    CircularProgressIndicator()
                } else {
                    val suggestions = state.settleUp.orEmpty()
                    val provisional = state.settleUpProvisional
                    if (provisional) {
                        // RM-0031: computed on this phone; the server plan replaces it after a sync.
                        Text(
                            stringResource(R.string.settle_up_preview_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.xs)
                        )
                    }
                    if (suggestions.isEmpty()) {
                        Text(stringResource(R.string.settle_up_none))
                    } else {
                        val fallback = stringResource(R.string.housemate_fallback)
                        Column {
                            suggestions.forEach { s ->
                                val fromName = s.from_display_name.ifBlank { fallback }
                                val toName = s.to_display_name.ifBlank { fallback }
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                                    Text(stringResource(R.string.settle_up_payment, fromName, toName, formatRand(s.amount)))
                                    if (provisional) StatusPill(stringResource(R.string.settle_up_preview_pill), PillTone.Neutral)
                                    if (s.to_user_id == state.currentUserId) {
                                        // Only the person who is owed can confirm they received it (RM-0010).
                                        // RM-0031: not from a preview, since the server has not checked it.
                                        SecondaryButton(
                                            stringResource(R.string.bills_confirm_received),
                                            onClick = { viewModel.confirmSettlement(s.from_user_id, s.amount) },
                                            enabled = !provisional
                                        )
                                        if (provisional) {
                                            Text(
                                                stringResource(R.string.settle_up_confirm_needs_sync),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (s.from_user_id == state.currentUserId) {
                                        Text(
                                            stringResource(R.string.bills_ask_to_confirm, toName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            stringResource(R.string.bills_waiting_for, toName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // RM-0052: the dialog is its own window, so the app's snackbar would be
                // hidden behind it. Show the same snackbar state here, in front.
                SnackbarHost(LocalSnackbarHostState.current)
              }
            },
            confirmButton = {
                PrimaryButton(stringResource(R.string.close), onClick = { viewModel.dismissSettleUp() })
            }
        )
    }
}

@Composable
/**
 * One bill card. Only the bill's creator (the person owed) may mark shares paid (RM-0010).
 * RM-0017: a bill saved offline shows "Waiting to sync" and no server actions until it is uploaded.
 */
private fun BillRow(bill: BillWithSharesRow, photos: List<PhotoLink>, canMarkPaid: Boolean, onPay: (String) -> Unit, onStopRepeat: (String) -> Unit) {
    val allPaid = bill.bill_shares.isNotEmpty() && bill.bill_shares.all { it.is_paid }
    RentMateCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bill.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatRand(bill.amount), style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.bills_due, bill.due_date.take(10)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (bill.pending) StatusPill(stringResource(R.string.sync_pending), PillTone.Neutral)
                if (allPaid) StatusPill(stringResource(R.string.bills_paid), PillTone.Positive) else StatusPill(stringResource(R.string.bills_outstanding), PillTone.Attention)
            }
        }
        if (isRepeating(bill.repeat, bill.repeat_stopped)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(if (RepeatMode.fromWire(bill.repeat) == RepeatMode.Weekly) R.string.bills_repeats_weekly else R.string.bills_repeats_monthly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canMarkPaid) SecondaryButton(stringResource(R.string.bills_stop_repeat), onClick = { onStopRepeat(bill.id) })
            }
        }
        if (photos.isNotEmpty()) AttachmentThumbnails(photos, modifier = Modifier.padding(top = Spacing.xs))
        bill.bill_shares.forEach { share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val name = share.profiles?.display_name ?: stringResource(R.string.housemate_fallback)
                MemberAvatar(name, size = 28.dp, userId = share.user_id)
                Text(
                    stringResource(R.string.bills_share, name, formatRand(share.amount_owed)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm)
                )
                if (share.is_paid) {
                    StatusPill(stringResource(R.string.bills_paid), PillTone.Positive)
                } else if (canMarkPaid) {
                    SecondaryButton(stringResource(R.string.bills_mark_paid), onClick = { onPay(share.id) })
                } else {
                    StatusPill(stringResource(R.string.bills_owes), PillTone.Attention)
                }
            }
        }
    }
}
