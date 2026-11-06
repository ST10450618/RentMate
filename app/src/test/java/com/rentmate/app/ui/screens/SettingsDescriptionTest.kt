package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.SettingToggle
import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0060: every switch on Settings has a short title and one line saying what it does. */
class SettingsDescriptionTest {

    @Test
    fun `every toggle has its own description line`() {
        val lines = SettingToggle.entries.map { toggleSummaryRes(it) }
        assertEquals(SettingToggle.entries.size, lines.toSet().size)
    }

    @Test
    fun `every toggle has its own title`() {
        val titles = SettingToggle.entries.map { toggleTitleRes(it) }
        assertEquals(SettingToggle.entries.size, titles.toSet().size)
    }

    @Test
    fun `maintenance reads as Repairs, as housemates say it`() {
        assertEquals(R.string.settings_repairs_title, toggleTitleRes(SettingToggle.NOTIFY_MAINTENANCE))
        assertEquals(R.string.settings_repairs_desc, toggleSummaryRes(SettingToggle.NOTIFY_MAINTENANCE))
    }

    @Test
    fun `the leaderboard line says it applies to the whole household`() {
        assertEquals(R.string.settings_leaderboard_desc, toggleSummaryRes(SettingToggle.SHOW_LEADERBOARD))
    }
}
