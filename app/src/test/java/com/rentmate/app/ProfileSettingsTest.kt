package com.rentmate.app

import com.rentmate.app.data.ProfileRow
import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.profileToggles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** RM-0005: the profile row maps onto the right local toggles. */
class ProfileSettingsTest {

    private val profile = ProfileRow(
        id = "u1",
        email = "a@example.com",
        display_name = "Thandi",
        show_leaderboard = false,
        preferred_language = "af",
        notify_bills = false,
        notify_chores = true,
        notify_shopping_list = true,
        notify_maintenance = false,
        biometric_enabled = true
    )

    @Test
    fun notificationTogglesComeFromTheProfile() {
        assertEquals(
            mapOf(
                SettingToggle.NOTIFY_BILLS to false,
                SettingToggle.NOTIFY_CHORES to true,
                SettingToggle.NOTIFY_SHOPPING to true,
                SettingToggle.NOTIFY_MAINTENANCE to false
            ),
            profileToggles(profile)
        )
    }

    @Test
    fun biometricUnlockStaysOnTheDevice() {
        assertFalse(SettingToggle.BIOMETRIC_UNLOCK in profileToggles(profile))
    }

    @Test
    fun leaderboardComesFromTheHouseholdNotTheProfile() {
        assertFalse(SettingToggle.SHOW_LEADERBOARD in profileToggles(profile))
    }
}
