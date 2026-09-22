package com.rentmate.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rentmate.app.navigation.Screen
import com.rentmate.app.navigation.moreItems

/** Fourth bottom-bar tab: reaches S8, S9 and S10. */
@Composable
fun MoreScreen(onOpen: (Screen) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        moreItems.forEach { item ->
            ListItem(
                headlineContent = { Text(item.screen.title) },
                supportingContent = { Text(item.screen.id) },
                leadingContent = { Icon(item.icon, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(item.screen) }
            )
            HorizontalDivider()
        }
    }
}
