package com.rentmate.app.notifications

import com.rentmate.app.navigation.Screen
import com.rentmate.app.navigation.deepLinkPath
import com.rentmate.app.navigation.tabFor
import com.rentmate.app.navigation.tabRootRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // RM-0056: tabs are Home, Money, Chores and House. The routes did not change,
    // so a push still names the same screen; deepLinkPath decides how it opens.

    @Test fun `a bill push opens Money and a chore push opens Chores`() {
        assertEquals(listOf(Screen.Bills), deepLinkPath(PushDeepLink.screenFor(null, "bills", null)!!))
        assertEquals(listOf(Screen.Chores), deepLinkPath(PushDeepLink.screenFor(null, "chores", null)!!))
        assertEquals(Screen.Bills, tabFor(Screen.Bills.route))
    }

    @Test fun `a maintenance or shopping push opens under House`() {
        assertEquals(listOf(Screen.House, Screen.Maintenance), deepLinkPath(PushDeepLink.screenFor(null, "maintenance", null)!!))
        assertEquals(listOf(Screen.House, Screen.ShoppingList), deepLinkPath(PushDeepLink.screenFor(null, "shopping", null)!!))
        assertEquals(Screen.House, tabFor(Screen.Maintenance.route))
        assertEquals(Screen.House, tabFor(Screen.ShoppingList.route))
    }

    @Test fun `every push path starts on a tab anchored on Home, never Login`() {
        PushDeepLink.targets.values.forEach { target ->
            val path = deepLinkPath(target)
            assertTrue(path.first().route in tabRootRoutes)
            assertEquals(target, path.last())
            assertFalse(path.any { it == Screen.Login || it == Screen.HouseholdSetup })
        }
    }

    @Test fun `a maintenance tap from the House tab still opens Maintenance`() {
        assertEquals(DeepLinkStep.Open(Screen.Maintenance), PushDeepLink.nextStep("maintenance", Screen.House.route))
        assertEquals(DeepLinkStep.AlreadyThere, PushDeepLink.nextStep("maintenance", Screen.Maintenance.route))
    }
}
