package com.rentmate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.auth.BiometricAvailability
import com.rentmate.app.auth.BiometricLockPolicy
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingGroup
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.UserSettings
import com.rentmate.app.ui.components.RentMateCard
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.SecondaryButton
import com.rentmate.app.ui.components.SectionLabel
import com.rentmate.app.ui.theme.Spacing

/**
 * S10 - Settings. Delivers US-3 (preferences) and US-4 (language), and holds the
 * biometric opt-in for US-2.
 */
@Composable
fun SettingsScreen(onSignedOut: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    val biometric = rememberBiometricToggleState { viewModel.setToggle(SettingToggle.BIOMETRIC_UNLOCK, it) }

    SettingsContent(
        settings = settings,
        biometricAvailability = biometric.availability,
        onToggle = { toggle, on ->
            if (toggle == SettingToggle.BIOMETRIC_UNLOCK) biometric.onToggle(on) else viewModel.setToggle(toggle, on)
        },
        onLanguage = viewModel::setLanguage,
        onSignOut = { viewModel.signOut(onSignedOut) }
    )
}

/** Stateless body, so it can be previewed and tested without a ViewModel. */
@Composable
fun SettingsContent(
    settings: UserSettings,
    onToggle: (SettingToggle, Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    biometricAvailability: BiometricAvailability = BiometricAvailability.AVAILABLE
) {
    Column(modifier = modifier.fillMaxWidth()) {
    ScreenHeader(title = stringResource(R.string.settings_title))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        SettingGroup.entries.forEach { group ->
            SettingsSection(title = stringResource(group.titleRes)) {
                SettingToggle.inGroup(group).forEachIndexed { index, toggle ->
                    if (index > 0) HorizontalDivider()
                    val isBiometric = toggle == SettingToggle.BIOMETRIC_UNLOCK
                    val usable = !isBiometric || BiometricLockPolicy.isToggleUsable(biometricAvailability)
                    ToggleRow(
                        title = stringResource(toggle.titleRes),
                        description = toggle.descRes?.let { stringResource(it) },
                        checked = settings.isOn(toggle) && usable,
                        onCheckedChange = { onToggle(toggle, it) },
                        enabled = usable,
                        note = if (isBiometric) biometricNoteRes(biometricAvailability)?.let { stringResource(it) } else null
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
            // RM-0025: no native speaker reviewed the af and xh strings.
            Text(
                stringResource(R.string.language_translation_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
            )
        }

        SecondaryButton(
            text = stringResource(R.string.sign_out),
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        )
    }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(title)
        RentMateCard(contentPadding = PaddingValues(vertical = Spacing.xs)) { content() }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    note: String? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun Row3(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) { content() }
}
