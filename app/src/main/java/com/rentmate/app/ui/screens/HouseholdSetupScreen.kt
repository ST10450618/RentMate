package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: Michael (Stage 2) - create household, join by invite code.
@Composable
fun HouseholdSetupScreen(onDone: () -> Unit) {
    StubScreen(
        id = Screen.HouseholdSetup.id,
        title = Screen.HouseholdSetup.title,
        stories = Screen.HouseholdSetup.stories,
        purpose = "Create a household or join an existing one with a six-character code.",
        actionLabel = "Continue to dashboard (stub)",
        onAction = onDone
    )
}
