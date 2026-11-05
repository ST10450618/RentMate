package com.rentmate.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rentmate.app.navigation.Screen
import com.rentmate.app.navigation.moreItems
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.theme.Spacing

/** Fourth bottom-bar tab: reaches S8, S9 and S10. */
@Composable
fun MoreScreen(onOpen: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(title = "More", subtitle = "Shopping, maintenance and settings")
        Column(
            modifier = Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            moreItems.forEach { item ->
                RentMateCard(
                    modifier = Modifier.clickable { onOpen(item.screen) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(item.screen.title, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(moreScreenDescription(item.screen)) },
                        leadingContent = {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

private fun moreScreenDescription(screen: Screen): String = when (screen) {
    Screen.ShoppingList -> "Shared items to buy"
    Screen.Maintenance -> "Log and track faults"
    Screen.Settings -> "Notifications, language, account"
    else -> ""
}
