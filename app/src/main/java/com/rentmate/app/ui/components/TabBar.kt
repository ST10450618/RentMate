package com.rentmate.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rentmate.app.ui.theme.Sizes

/**
 * One bottom tab.
 *
 * @property icon the outlined icon shown while the tab is not selected.
 * @property selectedIcon the filled version of the same icon, shown while it is.
 */
data class TabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * RM-0056: the bottom tabs. Each tab has a 24 dp icon above its label.
 *
 * An inactive tab shows the outlined icon with the label, both muted. The
 * active tab swaps to the filled icon, and the icon and the SemiBold label
 * turn aloe. There is no pill or indicator behind the active tab. A hairline
 * Line border separates the bar from the content.
 *
 * Each tab is a 48 dp+ target. TalkBack reads the label as the tab's name, so
 * the icons have no description of their own and the name is not read twice.
 */
@Composable
fun RentMateTabBar(tabs: List<TabItem>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = scheme.primary,
        selectedTextColor = scheme.primary,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant
    )
    Column(modifier = modifier.fillMaxWidth()) {
        ListDivider()
        NavigationBar(containerColor = scheme.surface, tonalElevation = Sizes.none) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab.selected,
                    onClick = tab.onClick,
                    icon = { Icon(if (tab.selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = colors
                )
            }
        }
    }
}

/**
 * RM-0056: the snackbar look from the mockups. It floats above the tabs in the
 * inverse colours (ink on light, light on dark), with rounded corners (radius
 * 10) and the action (Undo, Retry) in the inverse primary colour. The app
 * scaffold passes it to its one SnackbarHost.
 */
@Composable
fun RentMateSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionColor = MaterialTheme.colorScheme.inversePrimary,
        dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface
    )
}
