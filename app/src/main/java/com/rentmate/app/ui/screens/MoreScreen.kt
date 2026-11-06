package com.rentmate.app.ui.screens

import androidx.annotation.StringRes
import com.rentmate.app.R
import androidx.compose.ui.res.stringResource
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
import com.rentmate.app.navigation.Screen
import com.rentmate.app.navigation.moreItems
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.theme.Spacing

/** Fourth bottom-bar tab: reaches S8, S9 and S10. */
@Composable
fun MoreScreen(onOpen: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(title = stringResource(R.string.nav_more), subtitle = stringResource(R.string.more_subtitle))
        Column(
            modifier = Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            moreItems.forEach { item ->
                RentMateCard(
                    modifier = Modifier.clickable { onOpen(item.screen) },
                    contentPadding = PaddingValues()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(item.screen.titleRes), style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { moreScreenDescription(item.screen)?.let { Text(stringResource(it)) } },
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

@StringRes
private fun moreScreenDescription(screen: Screen): Int? = when (screen) {
    Screen.ShoppingList -> R.string.more_shopping_desc
    Screen.Maintenance -> R.string.more_maintenance_desc
    Screen.Activity -> R.string.more_activity_desc
    Screen.Settings -> R.string.more_settings_desc
    else -> null
}
