package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rentmate.app.R
import com.rentmate.app.auth.AuthUiState
import com.rentmate.app.auth.AuthViewModel
import com.rentmate.app.ui.components.BrandMark
import com.rentmate.app.ui.theme.Aloe
import com.rentmate.app.ui.theme.AloeDark
import com.rentmate.app.ui.theme.AloeLight
import com.rentmate.app.ui.theme.Amber
import com.rentmate.app.ui.theme.Ink
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "LoginScreen"

/**
 * S1 - Login (US-1).
 *
 * Branded entry screen: the RentMate mark on an aloe gradient, a one-line
 * promise, the three things the app does, and a single Google SSO button.
 * Sign-in itself is handled by [AuthViewModel]; this screen only reacts to
 * its state and routes new users to household setup.
 *
 * Errors are shown as a fixed, translatable message. The underlying exception
 * text is written to Logcat for debugging but never shown to the user
 * (Part 2 feedback: "raw error messages are shown when sign-in fails").
 */
@Composable
fun LoginScreen(
    onSignedInNewUser: () -> Unit,
    onSignedInExistingUser: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthUiState.Success -> {
                Log.i(TAG, "Sign-in succeeded, hasHousehold=${s.hasHousehold}")
                if (s.hasHousehold) onSignedInExistingUser() else onSignedInNewUser()
            }
            is AuthUiState.Error -> Log.w(TAG, "Sign-in failed: ${s.message}")
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Aloe, AloeDark)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            BrandMark(size = 112.dp)
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = AloeLight,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xxl))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                FeatureLine(Icons.Outlined.ReceiptLong, stringResource(R.string.login_feature_bills))
                FeatureLine(Icons.Outlined.CleaningServices, stringResource(R.string.login_feature_chores))
                FeatureLine(Icons.Outlined.Build, stringResource(R.string.login_feature_maintenance))
            }

            Spacer(Modifier.height(Spacing.xxl + Spacing.lg))

            val loading = state is AuthUiState.Loading
            if (state is AuthUiState.Error) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource((state as AuthUiState.Error).messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(Spacing.md),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            Button(
                onClick = { viewModel.signIn() },
                enabled = !loading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Ink,
                    disabledContainerColor = Color.White.copy(alpha = 0.7f),
                    disabledContentColor = Ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Aloe)
                    Spacer(Modifier.size(Spacing.md))
                    Text(stringResource(R.string.login_signing_in), style = MaterialTheme.typography.titleSmall)
                } else {
                    GoogleBadge()
                    Spacer(Modifier.size(Spacing.md))
                    Text(
                        stringResource(
                            if (state is AuthUiState.Error) R.string.login_try_again else R.string.login_continue_google
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                stringResource(R.string.login_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = AloeLight.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** One row of the value proposition: an amber icon chip and a short sentence. */
@Composable
private fun FeatureLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

/**
 * Neutral "G" badge for the sign-in button. Google's branding guidelines allow
 * a plain button labelled "Continue with Google"; we avoid copying the
 * multicolour logo asset into the repo.
 */
@Composable
private fun GoogleBadge() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(AloeLight, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("G", style = MaterialTheme.typography.labelLarge, color = AloeDark)
    }
}
