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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rentmate.app.R
import com.rentmate.app.ui.theme.Spacing

/**
 * The one screen header: title and optional subtitle on the page ground.
 *
 * RM-0056: the title sits on the ground in ink, not on a solid green banner.
 * The banner pushed content down and made every screen open with the same
 * block of colour (docs/ui-redesign-plan.html, "Header pattern differs").
 * Padding follows the plan: 16 dp sides, 16 dp top, 8 dp bottom.
 * The title is a heading for TalkBack, so users can jump between screens' titles.
 *
 * @param onBack when set, a back arrow sits before the title. Forms (Add Bill,
 *   Add Chore, Add Item) pass their close action so they share the list header.
 */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = if (onBack != null) Spacing.xs else Spacing.lg,
                end = Spacing.lg,
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
        Column {
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
    }
}
