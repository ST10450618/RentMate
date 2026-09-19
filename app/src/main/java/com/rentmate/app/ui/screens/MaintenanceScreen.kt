package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: James (Stage 1) - RentMate's differentiator. Build this one next.
@Composable
fun MaintenanceScreen() {
    StubScreen(
        id = Screen.Maintenance.id,
        title = Screen.Maintenance.title,
        stories = Screen.Maintenance.stories,
        purpose = "Log a fault with photo, category and urgency; track open to resolved."
    )
}
