package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: Ali (Stage 3) - balance summary, overdue alert, next chore.
@Composable
fun DashboardScreen(onOpenBills: () -> Unit, onOpenChores: () -> Unit) {
    StubScreen(
        id = Screen.Dashboard.id,
        title = Screen.Dashboard.title,
        stories = Screen.Dashboard.stories,
        purpose = "At-a-glance household state: balances, overdue bills, next chore.",
        actionLabel = "Open bills",
        onAction = onOpenBills
    )
}
