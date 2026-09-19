package com.rentmate.app.ui.screens

import android.util.Log
import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

private const val TAG = "LoginScreen"

// Owner: Michael (Stage 2) - SSO sign-in and biometric unlock.
@Composable
fun LoginScreen(onSignedInNewUser: () -> Unit, onSignedInExistingUser: () -> Unit) {
    Log.d(TAG, "composed")
    StubScreen(
        id = Screen.Login.id,
        title = Screen.Login.title,
        stories = Screen.Login.stories,
        purpose = "Google SSO sign-in, biometric unlock and language selection.",
        actionLabel = "Continue (stub)",
        onAction = {
            Log.d(TAG, "stub sign-in, routing to household setup")
            onSignedInNewUser()
        }
    )
}
