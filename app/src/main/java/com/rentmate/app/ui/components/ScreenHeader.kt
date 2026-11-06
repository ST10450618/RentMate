package com.rentmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rentmate.app.R
import com.rentmate.app.ui.theme.Spacing

/**
 * The one screen header: an optional eyebrow line, the title and an optional
 * subtitle on the page ground, with an optional avatar or action at the end.
 * Use it at the top of every screen.
 *
 * RM-0056: the title sits on the ground in ink, not on a solid green banner.
 * The banner pushed content down and made every screen open with the same
 * block of colour (docs/ui-redesign-plan.html, "Header pattern differs").
 * Padding follows the plan: the screen gutter at the sides (16 dp on a phone,
 * 24 dp on a tablet), 16 dp at the top and 8 dp at the bottom.
 * The title is a heading for TalkBack, so users can jump between screens' titles.
 *
 * @param eyebrow a small muted line above the title, for example the household
 *   name above "Home". Leave it null on most screens.
 * @param onBack when set, a back arrow sits before the title. Forms (Add Bill,
 *   Add Chore, Add Item) pass their close action so they share the list header.
 * @param trailing content at the end of the row. When null, the header shows
 *   [LocalHeaderAction] instead: the app scaffold sets it to the avatar that
 *   opens Settings on the four tab screens (RM-0056).
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val action = trailing ?: LocalHeaderAction.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                // The IconButton has 12 dp around its icon, so the arrow lines up with the gutter.
                start = if (onBack != null) Spacing.screen - Spacing.md else Spacing.screen,
                end = Spacing.screen,
                top = Spacing.lg,
                bottom = Spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) action()
    }
}

/**
 * What the end of a [ScreenHeader] shows when the screen passes nothing. The
 * app scaffold provides the Settings avatar on tab screens and null elsewhere,
 * so screens do not need to know about Settings.
 */
val LocalHeaderAction = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
