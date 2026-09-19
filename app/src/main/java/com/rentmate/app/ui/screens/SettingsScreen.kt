package com.rentmate.app.ui.screens

import androidx.compose.runtime.Composable
import com.rentmate.app.navigation.Screen
import com.rentmate.app.ui.components.StubScreen

// Owner: James (Stage 1) - notification, language and display preferences.
@Composable
fun SettingsScreen() {
    StubScreen(
        id = Screen.Settings.id,
        title = Screen.Settings.title,
        stories = Screen.Settings.stories,
        purpose = "Per-category notifications, language, biometrics and leaderboard visibility."
    )
}
