package com.rentmate.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for the FCM data contract in PushMessage (RM-0021). */
class PushMessageTest {

    @Test fun `a full data message parses`() {
        val push = PushMessage.parse(
            mapOf("channel" to "bills", "title" to "New bill", "body" to "Electricity R300", "id" to "b-1")
        )
        assertEquals(PushMessage(RentMateChannel.BILLS, "New bill", "Electricity R300", "b-1"), push)
    }

    @Test fun `screen is accepted when channel is missing`() {
        assertEquals(RentMateChannel.CHORES, PushMessage.parse(mapOf("screen" to "chores", "title" to "Your turn"))?.channel)
    }

    @Test fun `channel ids ignore case and spaces`() {
        assertEquals(RentMateChannel.MAINTENANCE, PushMessage.parse(mapOf("channel" to " Maintenance ", "title" to "Fixed"))?.channel)
    }

    @Test fun `a console test message uses the notification block`() {
        val push = PushMessage.parse(mapOf("channel" to "shopping"), fallbackTitle = "Test", fallbackBody = "Hello")
        assertEquals("Test", push?.title)
        assertEquals("Hello", push?.body)
    }

    @Test fun `an unknown or missing channel is dropped`() {
        assertNull(PushMessage.parse(mapOf("channel" to "payroll", "title" to "x")))
        assertNull(PushMessage.parse(mapOf("title" to "x")))
    }

    @Test fun `a message without a title is dropped`() {
        assertNull(PushMessage.parse(mapOf("channel" to "bills")))
        assertNull(PushMessage.parse(mapOf("channel" to "bills", "title" to "   ")))
    }

    @Test fun `long text is cut and a bad id is ignored`() {
        val push = PushMessage.parse(
            mapOf("channel" to "bills", "title" to "t".repeat(500), "body" to "b".repeat(5_000), "id" to "x".repeat(65))
        )!!
        assertEquals(120, push.title.length)
        assertEquals(1_000, push.body.length)
        assertNull(push.refId)
        assertNull(PushMessage.parse(mapOf("channel" to "bills", "title" to "t", "id" to " "))!!.refId)
    }
}
