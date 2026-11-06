package com.rentmate.app.ui.screens

import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentmate.app.R
import com.rentmate.app.auth.BiometricAvailability
import com.rentmate.app.auth.BiometricLockPolicy
import com.rentmate.app.data.AppLanguage
import com.rentmate.app.data.SettingGroup
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.UserSettings
import com.rentmate.app.ui.components.ErrorState
import com.rentmate.app.ui.components.InlineError
import com.rentmate.app.ui.components.ListDivider
import com.rentmate.app.ui.components.ListGroup
import com.rentmate.app.ui.components.ListHeading
import com.rentmate.app.ui.components.ListRow
import com.rentmate.app.ui.components.LoadingState
import com.rentmate.app.ui.components.MemberAvatar
import com.rentmate.app.ui.components.PillTone
import com.rentmate.app.ui.components.ScreenHeader
import com.rentmate.app.ui.components.StatusPill
import com.rentmate.app.ui.theme.Sizes
import com.rentmate.app.ui.theme.Spacing

private const val TAG = "SettingsScreen"

/**
 * S10 - Settings. Delivers US-3 (preferences) and US-4 (language), and holds the
 * biometric opt-in for US-2.
 *
 * RM-0060: opens from the avatar, so it has a back arrow. [onBack] defaults to
 * the system back action, so the navigation graph does not need to pass it.
 */
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val load by viewModel.load.collectAsStateWithLifecycle()
    val syncFailed by viewModel.syncFailed.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val clipboard = LocalClipboardManager.current

    val biometric = rememberBiometricToggleState { viewModel.setToggle(SettingToggle.BIOMETRIC_UNLOCK, it) }

    SettingsContent(
        settings = settings,
        profile = profile,
        biometricAvailability = biometric.availability,
        onToggle = { toggle, on ->
            if (toggle == SettingToggle.BIOMETRIC_UNLOCK) biometric.onToggle(on) else viewModel.setToggle(toggle, on)
        },
        onLanguage = viewModel::setLanguage,
        onSignOut = { viewModel.signOut(onSignedOut) },
        onBack = onBack ?: { dispatcher?.onBackPressed(); Unit },
        onCopyCode = { code ->
            clipboard.setText(AnnotatedString(code))
            viewModel.onInviteCodeCopied()
        },
        load = load,
        syncFailed = syncFailed,
        onReload = viewModel::reload,
        onRetrySync = viewModel::pullFromProfile
    )
}

/**
 * RM-0060: the one line under each switch that says what it does. Every toggle
 * has one, so no switch is a guess (checked by SettingsDescriptionTest).
 */
@StringRes
internal fun toggleSummaryRes(toggle: SettingToggle): Int = when (toggle) {
    SettingToggle.NOTIFY_BILLS -> R.string.settings_bills_desc
    SettingToggle.NOTIFY_CHORES -> R.string.settings_chores_desc
    SettingToggle.NOTIFY_SHOPPING -> R.string.settings_shopping_desc
    SettingToggle.NOTIFY_MAINTENANCE -> R.string.settings_repairs_desc
    SettingToggle.BIOMETRIC_UNLOCK -> R.string.settings_app_lock_desc
    SettingToggle.SHOW_LEADERBOARD -> R.string.settings_leaderboard_desc
}

/** RM-0060: the short title of each toggle, in the words housemates use. */
@StringRes
internal fun toggleTitleRes(toggle: SettingToggle): Int = when (toggle) {
    SettingToggle.NOTIFY_BILLS -> R.string.settings_bills_title
    SettingToggle.NOTIFY_CHORES -> R.string.settings_chores_title
    SettingToggle.NOTIFY_SHOPPING -> R.string.settings_shopping_title
    SettingToggle.NOTIFY_MAINTENANCE -> R.string.settings_repairs_title
    SettingToggle.BIOMETRIC_UNLOCK -> R.string.settings_app_lock_title
    SettingToggle.SHOW_LEADERBOARD -> R.string.leaderboard_title
}

/**
 * Stateless body, so it can be previewed and tested without a ViewModel.
 *
 * RM-0060 layout, from the Settings mockup: the profile row, then the
 * Notifications group, then Privacy and language (app lock, language and the
 * leaderboard), then Sign out as a red text button at the bottom.
 */
@Composable
fun SettingsContent(
    settings: UserSettings,
    onToggle: (SettingToggle, Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    profile: SettingsProfile = SettingsProfile(userId = null, name = ""),
    onBack: (() -> Unit)? = null,
    onCopyCode: (String) -> Unit = {},
    biometricAvailability: BiometricAvailability = BiometricAvailability.AVAILABLE,
    load: SettingsLoad = SettingsLoad.Ready,
    syncFailed: Boolean = false,
    onReload: () -> Unit = {},
    onRetrySync: () -> Unit = {}
) {
    var showLanguages by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        // RM-0056: the shared state views, like the list screens.
        when (load) {
            SettingsLoad.Loading -> LoadingState()
            SettingsLoad.Failed -> ErrorState(message = stringResource(R.string.settings_error_load), onRetry = onReload)
            SettingsLoad.Ready -> Unit
        }
        if (load == SettingsLoad.Ready) Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (syncFailed) InlineError(message = stringResource(R.string.settings_error_sync), onRetry = onRetrySync)

            ProfileRow(profile, onCopyCode)

            ListHeading(stringResource(R.string.settings_notifications))
            ListGroup(SettingToggle.inGroup(SettingGroup.NOTIFICATIONS)) { toggle ->
                SettingToggleRow(toggle, settings, biometricAvailability, onToggle)
            }

            ListHeading(stringResource(R.string.settings_privacy))
            ListGroup {
                SettingToggleRow(SettingToggle.BIOMETRIC_UNLOCK, settings, biometricAvailability, onToggle)
                ListDivider()
                ListRow(
                    title = stringResource(R.string.section_language),
                    supporting = stringResource(R.string.settings_language_options),
                    trailing = {
                        Text(
                            stringResource(settings.language.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        Log.d(TAG, "open language sheet")
                        showLanguages = true
                    }
                )
                ListDivider()
                SettingToggleRow(SettingToggle.SHOW_LEADERBOARD, settings, biometricAvailability, onToggle)
            }

            // RM-0060: a quiet red text button, not a filled one, so it is never
            // mistaken for the screen's main action.
            TextButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouch)
                    .padding(top = Spacing.sm)
            ) {
                Text(
                    stringResource(R.string.sign_out),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showLanguages) {
        LanguageSheet(
            current = settings.language,
            onPick = { language ->
                showLanguages = false
                if (language != settings.language) onLanguage(language)
            },
            onDismiss = { showLanguages = false }
        )
    }
}

/**
 * RM-0060: the user's avatar and name, with the household name and invite code
 * below, and a button that copies the code. The code is on screen so nobody has
 * to remember it to invite a housemate.
 */
@Composable
private fun ProfileRow(profile: SettingsProfile, onCopyCode: (String) -> Unit) {
    val code = profile.inviteCode
    val line = when {
        profile.householdName != null && code != null ->
            stringResource(R.string.settings_profile_line, profile.householdName, code)
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        MemberAvatar(profile.name, size = Sizes.avatarHeader, userId = profile.userId)
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (line != null) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (code != null) {
            IconButton(onClick = { onCopyCode(code) }) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.settings_copy_code),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * One switch row. The app lock row also shows the RM-0014 explanation when the
 * device has no screen lock or no biometric hardware, and is then disabled.
 */
@Composable
private fun SettingToggleRow(
    toggle: SettingToggle,
    settings: UserSettings,
    biometricAvailability: BiometricAvailability,
    onToggle: (SettingToggle, Boolean) -> Unit
) {
    val isBiometric = toggle == SettingToggle.BIOMETRIC_UNLOCK
    val usable = !isBiometric || BiometricLockPolicy.isToggleUsable(biometricAvailability)
    ToggleRow(
        title = stringResource(toggleTitleRes(toggle)),
        description = stringResource(toggleSummaryRes(toggle)),
        checked = settings.isOn(toggle) && usable,
        onCheckedChange = { onToggle(toggle, it) },
        enabled = usable,
        note = if (isBiometric) biometricNoteRes(biometricAvailability)?.let { stringResource(it) } else null
    )
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
    // RM-0056: the whole row is the switch, so TalkBack reads the title with
    // the state ("Bills, switch, on") and the touch target is the full row.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * RM-0060: a bottom sheet that lists each language in its own name, with the
 * RM-0025 note that Afrikaans and isiXhosa are machine-translated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(current: AppLanguage, onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            ListHeading(stringResource(R.string.section_language))
            ListGroup(AppLanguage.entries) { language ->
                ListRow(
                    title = stringResource(language.labelRes),
                    trailing = if (language == current) {
                        { StatusPill(stringResource(R.string.settings_language_current), PillTone.Positive) }
                    } else null,
                    onClick = {
                        Log.d(TAG, "pick language ${language.tag}")
                        onPick(language)
                    }
                )
            }
            // RM-0025: no native speaker reviewed the af and xh strings.
            Text(
                stringResource(R.string.language_translation_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
