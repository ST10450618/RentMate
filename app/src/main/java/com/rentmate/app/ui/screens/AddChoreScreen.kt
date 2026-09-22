package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Modal form: reached only from S6, returns to it on save or cancel.
@Composable
fun AddChoreScreen(onClose: () -> Unit) {
    StubScreen(
        id = Screen.AddChore.id,
        title = Screen.AddChore.title,
        stories = Screen.AddChore.stories,
        purpose = "Chore title, assignee, recurrence and point value.",
        actionLabel = "Close",
        onAction = onClose
    )
}
