package com.rentmate.app.ui.screens

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
import com.rentmate.app.ui.components.MemberAvatar
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
        topBar = { ScreenHeader(title = "Bills", subtitle = "${state.bills.size} bill(s)") },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBill) {
                Icon(Icons.Filled.Add, contentDescription = "Add bill")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(Spacing.screen)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton("Settle up", onClick = { viewModel.loadSettleUp() })
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
                        items(state.bills, key = { it.id }) { bill -> BillRow(bill, onPay = viewModel::payShare) }
                    }
                }
            }
        }
    }

    if (state.settleUp != null || state.settleUpLoading) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSettleUp() },
            title = { Text("Settle up") },
            text = {
                if (state.settleUpLoading) {
                    CircularProgressIndicator()
                } else {
                    val suggestions = state.settleUp.orEmpty()
                    if (suggestions.isEmpty()) {
                        Text("The household is already settled.")
                    } else {
                        Column {
                            suggestions.forEach { s ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "${s.from_display_name} \u2192 ${s.to_display_name}: ${formatRand(s.amount)}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    SecondaryButton("Confirm", onClick = { viewModel.confirmSettlement(s.to_user_id, s.amount) })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                PrimaryButton("Close", onClick = { viewModel.dismissSettleUp() })
            }
        )
    }
}

@Composable
private fun BillRow(bill: BillWithSharesRow, onPay: (String) -> Unit) {
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
            Text("Due ${bill.due_date.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (allPaid) StatusPill("Paid", PillTone.Positive) else StatusPill("Outstanding", PillTone.Attention)
        }
        bill.bill_shares.forEach { share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val name = share.profiles?.display_name ?: stringResource(R.string.housemate_fallback)
                MemberAvatar(name, size = 28.dp)
                Text(
                    "$name \u00B7 ${formatRand(share.amount_owed)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm)
                )
                if (share.is_paid) {
                    StatusPill("Paid", PillTone.Positive)
                } else {
                    SecondaryButton("Mark paid", onClick = { onPay(share.id) })
                }
            }
        }
    }
}
