package com.rentmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rentmate.app.ui.theme.Spacing

/** Full-bleed banner matching the Canva mockups: title + subtitle on the brand colour. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            )
        }
    }
}
