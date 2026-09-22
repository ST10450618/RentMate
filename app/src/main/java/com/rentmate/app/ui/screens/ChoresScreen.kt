package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: Ali (Stage 3) - rotation, four-week forecast, points.
@Composable
fun ChoresScreen(onAddChore: () -> Unit) {
    StubScreen(
        id = Screen.Chores.id,
        title = Screen.Chores.title,
        stories = Screen.Chores.stories,
        purpose = "This week's roster, the four-week rotation forecast and points.",
        actionLabel = "Add a chore",
        onAction = onAddChore
    )
}
