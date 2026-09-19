package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Modal form: reached only from S4, returns to it on save or cancel.
@Composable
fun AddBillScreen(onClose: () -> Unit) {
    StubScreen(
        id = Screen.AddBill.id,
        title = Screen.AddBill.title,
        stories = Screen.AddBill.stories,
        purpose = "Title, amount in ZAR, due date, payer and split method.",
        actionLabel = "Close",
        onAction = onClose
    )
}
