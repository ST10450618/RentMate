package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.rentmate.app.ui.components.BrandMark
import com.rentmate.app.ui.theme.Aloe
import com.rentmate.app.ui.theme.AloeDark
import com.rentmate.app.ui.theme.AloeLight
import com.rentmate.app.ui.theme.Amber
import com.rentmate.app.ui.theme.Ink
import com.rentmate.app.ui.theme.Spacing
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
    // Same aloe gradient and brand mark as Login, so locking feels part of the app.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Aloe, AloeDark)))
            .pointerInput(Unit) { /* consume every touch */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(size = 96.dp)
            Spacer(Modifier.height(Spacing.xl))
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Amber, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.lock_locked),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (failure != null) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(failureMessage(failure)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AloeLight,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = onRetry,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                modifier = Modifier.heightIn(min = 52.dp)
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = Aloe)
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(R.string.lock_unlock), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun failureMessage(failure: AuthFailure): Int = when (failure) {
    AuthFailure.CANCELLED -> R.string.lock_error_cancelled
    AuthFailure.LOCKOUT -> R.string.lock_error_lockout
    AuthFailure.NO_CREDENTIALS -> R.string.lock_error_no_credentials
    AuthFailure.OTHER -> R.string.lock_error_other
}
