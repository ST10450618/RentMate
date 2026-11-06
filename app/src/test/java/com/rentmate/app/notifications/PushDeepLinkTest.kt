package com.rentmate.app.notifications

import com.rentmate.app.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for the notification-tap routing in PushDeepLink (RM-0023). */
class PushDeepLinkTest {

    @Test fun `each channel opens its own screen`() {
        assertEquals(Screen.Bills, PushDeepLink.screenFor(null, "bills", null))
        assertEquals(Screen.Chores, PushDeepLink.screenFor(null, "chores", null))
        assertEquals(Screen.Maintenance, PushDeepLink.screenFor(null, "maintenance", null))
        assertEquals(Screen.ShoppingList, PushDeepLink.screenFor(null, "shopping", null))
    }

    @Test fun `every channel route is an allowed target`() {
        RentMateChannel.entries.forEach {
            assertEquals(it.screen, PushDeepLink.screenFor(it.screen, null, null)?.route)
        }
    }

    @Test fun `the app's own extra wins over the data keys`() {
        assertEquals(Screen.Chores, PushDeepLink.screenFor("chores", "bills", null))
    }

    @Test fun `screen key is an alias for channel`() {
        assertEquals(Screen.Maintenance, PushDeepLink.screenFor(null, null, "maintenance"))
    }

    @Test fun `values ignore case and spaces`() {
        assertEquals(Screen.Bills, PushDeepLink.screenFor(" Bills ", null, null))
        assertEquals(Screen.Bills, PushDeepLink.screenFor(null, "BILLS", null))
    }

    @Test fun `unknown or unsafe values open nothing`() {
        assertNull(PushDeepLink.screenFor(null, null, null))
        assertNull(PushDeepLink.screenFor("settings", null, null))
        assertNull(PushDeepLink.screenFor("login", "payments", "add_bill"))
    }

    @Test fun `an unknown open-screen extra falls back to the channel`() {
        assertEquals(Screen.Bills, PushDeepLink.screenFor("settings", "bills", null))
    }

    @Test fun `nothing pending means hold`() {
        assertEquals(DeepLinkStep.Hold, PushDeepLink.nextStep(null, Screen.Dashboard.route))
    }

    @Test fun `a tap waits for the start screen, Login and household setup`() {
        assertEquals(DeepLinkStep.Hold, PushDeepLink.nextStep("bills", null))
        assertEquals(DeepLinkStep.Hold, PushDeepLink.nextStep("bills", Screen.Login.route))
        assertEquals(DeepLinkStep.Hold, PushDeepLink.nextStep("bills", Screen.HouseholdSetup.route))
    }

    @Test fun `a tap on the Dashboard opens the target`() {
        assertEquals(DeepLinkStep.Open(Screen.Bills), PushDeepLink.nextStep("bills", Screen.Dashboard.route))
        assertEquals(DeepLinkStep.Open(Screen.Maintenance), PushDeepLink.nextStep("maintenance", Screen.Chores.route))
    }

    @Test fun `a tap from a form still opens the target`() {
        assertEquals(DeepLinkStep.Open(Screen.Chores), PushDeepLink.nextStep("chores", Screen.AddBill.route))
    }

    @Test fun `a tap for the screen already showing does not navigate`() {
        assertEquals(DeepLinkStep.AlreadyThere, PushDeepLink.nextStep("bills", Screen.Bills.route))
    }

    @Test fun `a pending route that is not a target is held, never opened`() {
        assertEquals(DeepLinkStep.Hold, PushDeepLink.nextStep("settings", Screen.Dashboard.route))
    }

    @Test fun `inbox keeps the newest tap and consume clears only that tap`() {
        val inbox = DeepLinkInbox()
        inbox.offer(Screen.Bills)
        inbox.offer(Screen.Chores)
        assertEquals("chores", inbox.pending.value)
        inbox.consume("bills")
        assertEquals("chores", inbox.pending.value)
        inbox.consume("chores")
        assertNull(inbox.pending.value)
    }
}
