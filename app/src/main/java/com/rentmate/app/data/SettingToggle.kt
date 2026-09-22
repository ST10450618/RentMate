package com.rentmate.app.data

import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.rentmate.app.R

/** Sections of the Settings screen, in display order. */
enum class SettingGroup(@StringRes val titleRes: Int) {
    NOTIFICATIONS(R.string.section_notifications),
    SECURITY_DISPLAY(R.string.section_security)
}

/**
 * Every boolean preference in one place: its storage key, its default, the
 * strings that label it, and the section it belongs to. Adding a setting means
 * adding one entry here - the screen renders whatever this enum contains.
 *
 * Notification preferences are per category, as US-3 requires.
 */
enum class SettingToggle(
    val key: Preferences.Key<Boolean>,
    val default: Boolean,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int?,
    val group: SettingGroup
) {
    NOTIFY_BILLS(
        booleanPreferencesKey("notify_bills"), true,
        R.string.notif_bills, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_CHORES(
        booleanPreferencesKey("notify_chores"), true,
        R.string.notif_chores, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_SHOPPING(
        booleanPreferencesKey("notify_shopping"), false,
        R.string.notif_shopping, null, SettingGroup.NOTIFICATIONS
    ),
    NOTIFY_MAINTENANCE(
        booleanPreferencesKey("notify_maintenance"), true,
        R.string.notif_maintenance, null, SettingGroup.NOTIFICATIONS
    ),

    // Off by default: US-2 requires one successful SSO sign-in before biometrics
    // can be enabled, so the user opts in deliberately.
    BIOMETRIC_UNLOCK(
        booleanPreferencesKey("biometric_unlock"), false,
        R.string.biometric_title, R.string.biometric_desc, SettingGroup.SECURITY_DISPLAY
    ),

    // US-12: hiding the leaderboard does not stop points accruing.
    SHOW_LEADERBOARD(
        booleanPreferencesKey("show_leaderboard"), true,
        R.string.leaderboard_title, R.string.leaderboard_desc, SettingGroup.SECURITY_DISPLAY
    );

    companion object {
        fun inGroup(group: SettingGroup): List<SettingToggle> = entries.filter { it.group == group }
    }
}
