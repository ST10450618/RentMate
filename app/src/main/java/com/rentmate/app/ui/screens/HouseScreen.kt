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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import com.rentmate.app.navigation.Screen
import com.rentmate.app.navigation.houseItems
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.theme.Spacing

/**
 * The House tab (RM-0056): the jobs that keep the house running, Shopping (S8)
 * and Maintenance (S9). It replaced the More tab. Settings moved to the avatar
 * in the header, and Activity moved into Home.
 */
@Composable
fun HouseScreen(onOpen: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(title = stringResource(R.string.nav_house), subtitle = stringResource(R.string.house_subtitle))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            houseItems.forEach { item ->
                RentMateCard(
                    modifier = Modifier.clickable(role = Role.Button) { onOpen(item.screen) },
                    contentPadding = PaddingValues()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(item.screen.titleRes), style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { houseScreenDescription(item.screen)?.let { Text(stringResource(it)) } },
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
private fun houseScreenDescription(screen: Screen): Int? = when (screen) {
    Screen.ShoppingList -> R.string.more_shopping_desc
    Screen.Maintenance -> R.string.more_maintenance_desc
    else -> null
}
