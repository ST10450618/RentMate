package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: Michael (Stage 2) - bill list, filters, settle-up.
@Composable
fun BillsScreen(onAddBill: () -> Unit) {
    StubScreen(
        id = Screen.Bills.id,
        title = Screen.Bills.title,
        stories = Screen.Bills.stories,
        purpose = "Bills by status, with settle-up showing the minimum set of payments.",
        actionLabel = "Add a bill",
        onAction = onAddBill
    )
}
