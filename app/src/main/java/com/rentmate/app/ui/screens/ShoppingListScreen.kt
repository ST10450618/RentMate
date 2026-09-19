package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: Seth (Stage 3 support) - shared list, tick off, convert to bill.
@Composable
fun ShoppingListScreen() {
    StubScreen(
        id = Screen.ShoppingList.id,
        title = Screen.ShoppingList.title,
        stories = Screen.ShoppingList.stories,
        purpose = "Shared list any housemate can add to, with purchased items convertible to a bill."
    )
}
