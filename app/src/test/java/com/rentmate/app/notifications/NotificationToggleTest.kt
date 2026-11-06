package com.rentmate.app.notifications

import com.rentmate.app.data.SettingToggle
import com.rentmate.app.data.UserSettings
import com.rentmate.app.ui.screens.TOGGLE_COLUMN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-channel notification toggles (RM-0023): what the client drops and what the server reads. */
class NotificationToggleTest {

    private fun push(channel: RentMateChannel) = PushMessage(channel, "Title", "Body", null)

    private fun settingsWith(toggle: SettingToggle, on: Boolean) =
        UserSettings(toggles = SettingToggle.entries.associateWith { it.default } + (toggle to on))

    @Test fun `bill pushes show when Bills is on`() {
        assertTrue(push(RentMateChannel.BILLS).isAllowedBy(settingsWith(SettingToggle.NOTIFY_BILLS, true)))
    }

    @Test fun `turning Bills off drops bill pushes only`() {
        val settings = settingsWith(SettingToggle.NOTIFY_BILLS, false)
        assertFalse(push(RentMateChannel.BILLS).isAllowedBy(settings))
        assertTrue(push(RentMateChannel.CHORES).isAllowedBy(settings))
        assertTrue(push(RentMateChannel.MAINTENANCE).isAllowedBy(settings))
    }

    @Test fun `each channel follows its own toggle`() {
        RentMateChannel.entries.forEach { channel ->
            assertFalse(channel.name, push(channel).isAllowedBy(settingsWith(channel.toggle, false)))
            assertTrue(channel.name, push(channel).isAllowedBy(settingsWith(channel.toggle, true)))
        }
    }

    @Test fun `defaults match the Settings screen`() {
        val defaults = UserSettings()
        assertTrue(push(RentMateChannel.BILLS).isAllowedBy(defaults))
        assertTrue(push(RentMateChannel.CHORES).isAllowedBy(defaults))
        assertTrue(push(RentMateChannel.MAINTENANCE).isAllowedBy(defaults))
        assertFalse(push(RentMateChannel.SHOPPING).isAllowedBy(defaults))
    }

    // send-push skips users whose profiles column is false, so a toggle saved to
    // the wrong column would leave the server sending pushes the user turned off.
    @Test fun `toggles save to the columns send-push reads`() {
        val serverColumns = mapOf(
            "bills" to "notify_bills",
            "chores" to "notify_chores",
            "shopping" to "notify_shopping_list",
            "maintenance" to "notify_maintenance"
        )
        RentMateChannel.entries.forEach { channel ->
            assertEquals(channel.name, serverColumns.getValue(channel.id), TOGGLE_COLUMN.getValue(channel.toggle))
        }
    }
}
