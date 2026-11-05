package com.rentmate.app.notifications

import com.rentmate.app.data.SettingGroup
import com.rentmate.app.data.SettingToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Guards the contract between channels, Settings toggles and the push payload (RM-0021). */
class RentMateChannelTest {

    @Test fun `channel ids are unique`() {
        val ids = RentMateChannel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `every notification toggle in Settings has exactly one channel`() {
        val notificationToggles = SettingToggle.inGroup(SettingGroup.NOTIFICATIONS).toSet()
        assertEquals(notificationToggles, RentMateChannel.entries.map { it.toggle }.toSet())
    }

    @Test fun `payload channel values resolve, unknown values do not`() {
        RentMateChannel.entries.forEach { assertEquals(it, RentMateChannel.fromId(it.id)) }
        assertNull(RentMateChannel.fromId("payments"))
        assertNull(RentMateChannel.fromId(null))
    }

    @Test fun `channels open real screen routes`() {
        val routes = setOf("bills", "chores", "shopping", "maintenance")
        RentMateChannel.entries.forEach { assert(it.screen in routes) { "${it.name} -> ${it.screen}" } }
    }
}
