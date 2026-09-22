package com.rentmate.app

import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.RequestStatus
import com.rentmate.app.data.TitleError
import com.rentmate.app.data.validateTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * US-10. These cover the rules a marker would actually try to break: an empty
 * title, a status going backwards, and a resolved request being advanced again.
 */
class MaintenanceRequestTest {

    @Test
    fun `a blank title is rejected`() {
        assertEquals(TitleError.BLANK, validateTitle(""))
        assertEquals(TitleError.BLANK, validateTitle("   "))
    }

    @Test
    fun `an overlong title is rejected`() {
        assertEquals(TitleError.TOO_LONG, validateTitle("x".repeat(81)))
    }

    @Test
    fun `a sensible title is accepted`() {
        assertNull(validateTitle("Geyser leaking into the ceiling"))
    }

    @Test
    fun `status advances in order`() {
        assertEquals(RequestStatus.SENT, RequestStatus.OPEN.next())
        assertEquals(RequestStatus.ACKNOWLEDGED, RequestStatus.SENT.next())
        assertEquals(RequestStatus.RESOLVED, RequestStatus.ACKNOWLEDGED.next())
    }

    @Test
    fun `a resolved request cannot advance further`() {
        assertNull(RequestStatus.RESOLVED.next())
        assertFalse(RequestStatus.RESOLVED.canAdvance)
    }

    @Test
    fun `advancing stamps the transition time`() {
        val request = MaintenanceRequest(title = "Broken tap", createdAt = 1_000L)
        val sent = request.advanced(now = 2_000L)

        assertEquals(RequestStatus.SENT, sent.status)
        assertEquals(2_000L, sent.sentAt)
        assertNull(sent.acknowledgedAt)
    }

    @Test
    fun `advancing a resolved request changes nothing`() {
        val resolved = MaintenanceRequest(
            title = "Done already",
            status = RequestStatus.RESOLVED,
            resolvedAt = 5_000L
        )
        assertEquals(resolved, resolved.advanced(now = 9_000L))
    }

    @Test
    fun `earlier timestamps are retained as the request progresses`() {
        val request = MaintenanceRequest(title = "Gate motor", createdAt = 1_000L)
            .advanced(now = 2_000L)
            .advanced(now = 3_000L)

        assertEquals(RequestStatus.ACKNOWLEDGED, request.status)
        assertNotNull(request.timestampFor(RequestStatus.OPEN))
        assertEquals(2_000L, request.timestampFor(RequestStatus.SENT))
        assertEquals(3_000L, request.timestampFor(RequestStatus.ACKNOWLEDGED))
        assertNull(request.timestampFor(RequestStatus.RESOLVED))
    }

    @Test
    fun `a new request starts open with no send timestamp`() {
        val request = MaintenanceRequest(title = "New fault")
        assertEquals(RequestStatus.OPEN, request.status)
        assertTrue(request.status.canAdvance)
        assertNull(request.sentAt)
    }
}
