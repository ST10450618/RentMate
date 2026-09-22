package com.rentmate.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app. Screen IDs (S1-S10) match the UI mockups and the
 * navigation wireflow in the Part 1 design document, so a screen can be traced
 * from the document to the code and back.
 *
 * "More" is not an S-numbered screen: it is the fourth bottom-bar tab, a menu
 * that reaches S8, S9 and S10.
 */
sealed class Screen(
    val route: String,
    val id: String,
    val title: String,
    val stories: String
) {
    data object Login : Screen("login", "S1", "Sign in", "US-1, US-2, US-4")
    data object HouseholdSetup : Screen("household", "S2", "Set up your household", "US-5")
    data object Dashboard : Screen("dashboard", "S3", "Dashboard", "US-3")
    data object Bills : Screen("bills", "S4", "Bills", "US-6, US-11")
    data object AddBill : Screen("add_bill", "S5", "Add bill", "US-6")
    data object Chores : Screen("chores", "S6", "Chores", "US-9, US-12")
    data object AddChore : Screen("add_chore", "S7", "Add chore", "US-9, US-12")
    data object ShoppingList : Screen("shopping", "S8", "Shopping list", "US-13")
    data object AddShoppingItem : Screen("add_shopping_item", "S8", "Add item", "US-13")
    data object Maintenance : Screen("maintenance", "S9", "Maintenance", "US-10")
    data object Settings : Screen("settings", "S10", "Settings", "US-2, US-3, US-4")
    data object More : Screen("more", "-", "More", "-")
}

data class BottomNavItem(val screen: Screen, val icon: ImageVector)

/** The four top-level destinations. Material 3 recommends three to five. */
val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Filled.Home),
    BottomNavItem(Screen.Bills, Icons.Filled.Receipt),
    BottomNavItem(Screen.Chores, Icons.Filled.Checklist),
    BottomNavItem(Screen.More, Icons.Filled.MoreHoriz)
)

/** Destinations reached from the More tab. */
val moreItems = listOf(
    BottomNavItem(Screen.ShoppingList, Icons.Filled.ShoppingCart),
    BottomNavItem(Screen.Maintenance, Icons.Filled.Build),
    BottomNavItem(Screen.Settings, Icons.Filled.Settings)
)

/**
 * Routes that show the bottom bar. Login and household setup come before the
 * app proper; the two Add forms are modal and return to their parent on save,
 * which keeps the back stack shallow.
 */
val routesWithBottomBar = setOf(
    Screen.Dashboard.route,
    Screen.Bills.route,
    Screen.Chores.route,
    Screen.More.route,
    Screen.ShoppingList.route,
    Screen.Maintenance.route,
    Screen.Settings.route
)
