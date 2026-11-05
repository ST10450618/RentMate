package com.rentmate.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rentmate.app.R
import com.rentmate.app.ui.theme.RentMateTheme
import com.rentmate.app.ui.theme.Spacing

/*
 * Empty, loading and error views shared by every list screen (RM-0035).
 */

/**
 * Centred empty state: illustration, title, explanation and an optional action.
 *
 * @param illustration one of the `illus_empty_*` vector drawables.
 * @param actionLabel label of the call-to-action; the button shows only when
 *   both [actionLabel] and [onAction] are given.
 */
@Composable
fun EmptyState(
    @DrawableRes illustration: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(illustration), contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            PrimaryButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

/**
 * Full-area error state with a retry button, for when a load failed and there
 * is nothing to show.
 *
 * @param message a friendly, user-facing sentence. Never pass exception text.
 */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        SecondaryButton(stringResource(R.string.state_retry), onClick = onRetry)
    }
}

/**
 * Compact error banner with retry, shown above a list that still has data
 * (e.g. a background refresh failed).
 *
 * @param message a friendly, user-facing sentence. Never pass exception text.
 */
@Composable
fun InlineError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    RentMateCard(modifier = modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.error) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.state_retry), onClick = onRetry)
        }
    }
}

/** Full-area centred spinner for the first load, when there is nothing to show yet. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

/** Thin progress bar for refreshing while content is already on screen. */
@Composable
fun RefreshingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(modifier = modifier.fillMaxWidth())
}

/**
 * True once [loading] has been seen as true at least once. ViewModels start
 * with `loading = false`, so without this a screen would flash its empty state
 * before the first refresh begins.
 */
@Composable
fun rememberHasStartedLoading(loading: Boolean): Boolean {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (loading) started = true }
    return started || loading
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    RentMateTheme {
        EmptyState(
            illustration = R.drawable.illus_empty_bills,
            title = "No bills yet",
            body = "Add the first bill and RentMate will split it for everyone.",
            actionLabel = "Add a bill",
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    RentMateTheme { ErrorState(message = "Something went wrong. Check your connection and try again.", onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun InlineErrorPreview() {
    RentMateTheme { InlineError(message = "Something went wrong. Check your connection and try again.", onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    RentMateTheme { LoadingState() }
}

@Preview(showBackground = true)
@Composable
private fun RefreshingIndicatorPreview() {
    RentMateTheme { RefreshingIndicator() }
}
