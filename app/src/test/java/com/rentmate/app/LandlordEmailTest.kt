package com.rentmate.app

import com.rentmate.app.data.LandlordContact
import com.rentmate.app.data.LandlordEmailText
import com.rentmate.app.data.MaintenanceCategory
import com.rentmate.app.data.MaintenanceRequest
import com.rentmate.app.data.RequestStatus
import com.rentmate.app.data.Urgency
import com.rentmate.app.data.buildLandlordEmail
import com.rentmate.app.data.isValidEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0009: the landlord email lists every open fault, and only the right people change status. */
class LandlordEmailTest {

    private val text = LandlordEmailText(
        subject = "Maintenance request - %1\$s",
        greetingNamed = "Dear %1\$s,",
        greetingGeneric = "Dear landlord,",
        intro = "The following faults need attention at %1\$s:",
        itemCategory = "Category: %1\$s",
        itemUrgency = "Urgency: %1\$s",
        itemLogged = "Logged: %1\$s",
        signOff = "Kind regards,\nThe tenants at %1\$s",
        categoryLabel = { it.name.lowercase() },
        urgencyLabel = { it.name.lowercase() }
    )

    private val tap = MaintenanceRequest(
        id = "1", title = "Leaking tap", description = "Kitchen tap drips",
        category = MaintenanceCategory.PLUMBING, urgency = Urgency.HIGH, createdAt = 1L,
        raisedBy = "alice"
    )
    private val light = MaintenanceRequest(
        id = "2", title = "Hall light", category = MaintenanceCategory.ELECTRICAL, createdAt = 2L,
        raisedBy = "bob"
    )

    private fun build(contact: LandlordContact) =
        buildLandlordEmail(contact, listOf(tap, light), text) { "day $it" }

    @Test
    fun itemisesEveryOpenFault() {
        val email = build(LandlordContact("12 Long St", "Mr Dlamini", " landlord@example.com "))

        assertEquals("landlord@example.com", email.to)
        assertEquals("Maintenance request - 12 Long St", email.subject)
        assertTrue(email.body.startsWith("Dear Mr Dlamini,\n\nThe following faults need attention at 12 Long St:"))
        assertTrue(email.body.contains("1. Leaking tap\n   Category: plumbing\n   Urgency: high\n   Logged: day 1\n   Kitchen tap drips"))
        assertTrue(email.body.contains("2. Hall light\n   Category: electrical\n   Urgency: medium\n   Logged: day 2"))
        assertTrue(email.body.endsWith("Kind regards,\nThe tenants at 12 Long St"))
    }

    @Test
    fun usesAGenericGreetingWithoutALandlordName() {
        assertTrue(build(LandlordContact("12 Long St", landlordName = " ")).body.startsWith("Dear landlord,"))
    }

    @Test
    fun checksTheLandlordAddress() {
        assertTrue(isValidEmail(" landlord@example.co.za "))
        assertFalse(isValidEmail("landlord"))
        assertFalse(isValidEmail("land lord@example.com"))
        assertFalse(isValidEmail("landlord@example"))
    }

    @Test
    fun onlyThePersonWhoLoggedItSendsAnOpenRequest() {
        assertTrue(tap.canChangeStatus("alice"))
        assertFalse(tap.canChangeStatus("bob"))
        assertFalse(tap.canChangeStatus(null))
    }

    @Test
    fun theSenderOrTheLoggerRecordsTheLandlordsResponse() {
        val sent = tap.copy(status = RequestStatus.SENT, sentBy = "bob")
        assertTrue(sent.canChangeStatus("bob"))
        assertTrue(sent.canChangeStatus("alice"))
        assertFalse(sent.canChangeStatus("carol"))
        assertFalse(sent.copy(status = RequestStatus.RESOLVED).canChangeStatus("alice"))
    }
}
