package com.rentmate.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing

/** One tab of a [TextTabBar]: its label and whether it is the current tab. */
data class TabItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * RM-0056: the bottom tabs from the mockups. Text only, no icons: the four
 * labels (Home, Money, Chores, House) are short and say what each tab is for.
 * The current tab is aloe and SemiBold, the others muted. A hairline Line
 * border separates the bar from the content.
 *
 * Each tab is at least 48 dp tall and fills an equal share of the width.
 * TalkBack reads the bar as one group of tabs with the selected state. Labels
 * wrap rather than clip at the largest font size.
 */
@Composable
fun TextTabBar(tabs: List<TabItem>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            ListDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .selectableGroup()
            ) {
                tabs.forEach { tab ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Sizes.tabBar)
                            .selectable(selected = tab.selected, role = Role.Tab, onClick = tab.onClick)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
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
