package com.rentmate.app.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.ui.theme.Spacing

/**
 * RM-0019: the app-wide offline and sync banner, plus the notice for rejected
 * changes. The app scaffold places it once, above every signed-in screen.
 */
@Composable
fun SyncBannerHost(modifier: Modifier = Modifier, viewModel: SyncBannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth()) {
        SyncBanner(state.banner, onRetry = viewModel::retry)
        RejectionNoticeBar(state.notice, onDismiss = viewModel::dismiss)
    }
}

/** Remembers the last visible value of [value], so the exit animation keeps its content. */
@Composable
private fun <T : Any> rememberLastVisible(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}

/** The banner for [state]. Slides in and out; [BannerState.Hidden] shows nothing. */
@Composable
fun SyncBanner(state: BannerState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val visible = state != BannerState.Hidden
    val shown = rememberLastVisible(state.takeIf { visible })
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        val scheme = MaterialTheme.colorScheme
        when (val s = shown ?: return@AnimatedVisibility) {
            is BannerState.Offline -> BannerRow(
                icon = Icons.Outlined.CloudOff,
                text = buildString {
                    append(stringResource(R.string.sync_banner_offline))
                    if (s.pending > 0) {
                        append(' ')
                        append(pluralStringResource(R.plurals.sync_banner_offline_pending, s.pending, s.pending))
                    }
                },
                background = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer
            )
            is BannerState.Syncing -> BannerRow(
                icon = Icons.Outlined.Sync,
                text = pluralStringResource(R.plurals.sync_banner_syncing, s.pending, s.pending),
                background = scheme.primaryContainer,
                content = scheme.onPrimaryContainer
            )
            is BannerState.Failed -> BannerRow(
                icon = Icons.Outlined.ErrorOutline,
                text = pluralStringResource(R.plurals.sync_banner_failed, s.pending, s.pending),
                background = scheme.errorContainer,
                content = scheme.onErrorContainer,
                actionLabel = stringResource(R.string.feedback_retry),
                onAction = onRetry
            )
            BannerState.Synced -> BannerRow(
                icon = Icons.Outlined.CloudDone,
                text = stringResource(R.string.sync_banner_synced),
                background = scheme.primaryContainer,
                content = scheme.onPrimaryContainer
            )
            BannerState.Hidden -> Unit
        }
    }
}

/** The notice for changes the server refused. Stays until the user dismisses it. */
@Composable
fun RejectionNoticeBar(notice: RejectionNotice?, onDismiss: (RejectionNotice) -> Unit, modifier: Modifier = Modifier) {
    val shown = rememberLastVisible(notice)
    AnimatedVisibility(visible = notice != null, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        val s = shown ?: return@AnimatedVisibility
        val scheme = MaterialTheme.colorScheme
        val reason = stringResource(
            when (s.kind) {
                RejectionKind.NOT_ALLOWED -> R.string.sync_rejected_not_allowed
                RejectionKind.GONE -> R.string.sync_rejected_gone
                RejectionKind.NOT_SAVED -> R.string.sync_rejected_not_saved
            }
        )
        val more = if (s.others > 0) {
            " " + pluralStringResource(R.plurals.sync_rejected_more, s.others, s.others)
        } else {
            ""
        }
        BannerRow(
            icon = Icons.Outlined.Info,
            text = reason + more,
            background = scheme.surfaceVariant,
            content = scheme.onSurfaceVariant,
            actionLabel = stringResource(R.string.sync_rejected_dismiss),
            onAction = { onDismiss(s) }
        )
    }
}

@Composable
private fun BannerRow(
    icon: ImageVector,
    text: String,
    background: Color,
    content: Color,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .heightIn(min = 40.dp)
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}
