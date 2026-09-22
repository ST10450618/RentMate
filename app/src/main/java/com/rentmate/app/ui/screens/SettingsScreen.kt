package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingGroup
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.UserSettings

/**
 * S10 - Settings. Delivers US-3 (preferences) and US-4 (language), and holds the
 * biometric opt-in for US-2.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    SettingsContent(
        settings = settings,
        onToggle = viewModel::setToggle,
        onLanguage = viewModel::setLanguage
    )
}

/** Stateless body, so it can be previewed and tested without a ViewModel. */
@Composable
fun SettingsContent(
    settings: UserSettings,
    onToggle: (SettingToggle, Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )

        SettingGroup.entries.forEach { group ->
            SettingsSection(title = stringResource(group.titleRes)) {
                SettingToggle.inGroup(group).forEachIndexed { index, toggle ->
                    if (index > 0) HorizontalDivider()
                    ToggleRow(
                        title = stringResource(toggle.titleRes),
                        description = toggle.descRes?.let { stringResource(it) },
                        checked = settings.isOn(toggle),
                        onCheckedChange = { onToggle(toggle, it) }
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.section_language)) {
            Row3 {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = settings.language == language,
                        onClick = { onLanguage(language) },
                        label = { Text(stringResource(language.labelRes)) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { /* Michael wires this to sign-out in Stage 2 */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.sign_out))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Row3(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
