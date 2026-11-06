package com.rentmate.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RM-0056 part 6: the tabs are Home, Money, Chores and House; Settings opens from the avatar. */
class TabsTest {

    @Test
    fun theFourTabsAreNamedAfterJobs() {
        assertEquals(
            listOf(Screen.Dashboard, Screen.Bills, Screen.Chores, Screen.House),
            bottomNavItems.map { it.screen }
        )
    }

    @Test
    fun shoppingAndMaintenanceSitUnderHouse() {
        assertEquals(listOf(Screen.ShoppingList, Screen.Maintenance), houseItems.map { it.screen })
        assertEquals(Screen.House, tabFor(Screen.ShoppingList.route))
        assertEquals(Screen.House, tabFor(Screen.Maintenance.route))
    }

    @Test
    fun activityBelongsToHome() {
        assertEquals(Screen.Dashboard, tabFor(Screen.Activity.route))
    }

    @Test
    fun settingsIsNotATab() {
        assertNull(tabFor(Screen.Settings.route))
        assertFalse(Screen.Settings.route in tabRootRoutes)
        assertTrue(Screen.Settings.route in routesWithBottomBar)
    }

    @Test
    fun formsAndSignInHaveNoTabsOrBar() {
        listOf(Screen.Login, Screen.HouseholdSetup, Screen.AddBill, Screen.AddChore, Screen.AddShoppingItem).forEach {
            assertNull(it.route, tabFor(it.route))
            assertFalse(it.route, it.route in routesWithBottomBar)
        }
    }

    @Test
    fun everyTabHasAnOutlinedAndAFilledIcon() {
        bottomNavItems.forEach { assertTrue(it.screen.route, it.icon != it.selectedIcon) }
    }
}
