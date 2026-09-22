package com.rentmate.app.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.ui.components.ScreenHeader

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
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { viewModel.loadSettleUp() }) { Text("Settle Up") }
            }

            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn {
                items(state.bills, key = { it.id }) { bill -> BillRow(bill, onPay = viewModel::payShare) }
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
                                    Text("${s.from_display_name} -> ${s.to_display_name}: R${"%.2f".format(s.amount)}")
                                    OutlinedButton(onClick = { viewModel.confirmSettlement(s.to_user_id, s.amount) }) {
                                        Text("Confirm")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissSettleUp() }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun BillRow(bill: BillWithSharesRow, onPay: (String) -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(bill.title, style = MaterialTheme.typography.titleMedium)
            Text("R${"%.2f".format(bill.amount)} - due ${bill.due_date.take(10)}", style = MaterialTheme.typography.bodySmall)
            bill.bill_shares.forEach { share ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${share.profiles?.display_name ?: "?"}: R${"%.2f".format(share.amount_owed)}")
                    if (share.is_paid) {
                        Text("Paid", color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = { onPay(share.id) }) { Text("Mark paid") }
                    }
                }
            }
        }
    }
}
