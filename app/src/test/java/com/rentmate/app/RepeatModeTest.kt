package com.rentmate.app

import com.rentmate.app.data.RepeatMode
import com.rentmate.app.data.isRepeating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatModeTest {
    @Test
    fun wireValuesMatchTheDatabase() {
        assertEquals(listOf("Never", "Weekly", "Monthly"), RepeatMode.entries.map { it.wire })
    }

    @Test
    fun unknownOrMissingValuesCountAsNever() {
        assertEquals(RepeatMode.Weekly, RepeatMode.fromWire("Weekly"))
        assertEquals(RepeatMode.Never, RepeatMode.fromWire(null))
        assertEquals(RepeatMode.Never, RepeatMode.fromWire("Daily"))
    }

    @Test
    fun onlyActiveRepeatingBillsShowAsRepeating() {
        assertTrue(isRepeating("Monthly", stopped = false))
        assertTrue(isRepeating("Weekly", stopped = false))
        assertFalse(isRepeating("Monthly", stopped = true))
        assertFalse(isRepeating("Never", stopped = false))
    }
}
