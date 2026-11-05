package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rentmate.app.R
import com.rentmate.app.auth.AuthFailure

/**
 * RM-0013: opaque screen shown while the app is locked. It swallows touches so the
 * content below cannot be used, explains why the last attempt failed (if it did),
 * and offers a Retry button that shows the prompt again.
 */
@Composable
fun LockScreen(failure: AuthFailure?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { /* consume every touch */ },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_locked),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            if (failure != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(failureMessage(failure)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}

private fun failureMessage(failure: AuthFailure): Int = when (failure) {
    AuthFailure.CANCELLED -> R.string.lock_error_cancelled
    AuthFailure.LOCKOUT -> R.string.lock_error_lockout
    AuthFailure.NO_CREDENTIALS -> R.string.lock_error_no_credentials
    AuthFailure.OTHER -> R.string.lock_error_other
}
