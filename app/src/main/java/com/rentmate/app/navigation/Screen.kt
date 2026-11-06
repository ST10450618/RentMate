package com.rentmate.app.navigation

import com.rentmate.app.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
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
    @StringRes val titleRes: Int,
    val stories: String
) {
    data object Login : Screen("login", "S1", R.string.nav_sign_in, "US-1, US-2, US-4")
    data object HouseholdSetup : Screen("household", "S2", R.string.household_setup_title, "US-5")
    data object Dashboard : Screen("dashboard", "S3", R.string.nav_dashboard, "US-3")
    data object Bills : Screen("bills", "S4", R.string.nav_bills, "US-6, US-11")
    data object AddBill : Screen("add_bill", "S5", R.string.add_bill_title, "US-6")
    data object Chores : Screen("chores", "S6", R.string.nav_chores, "US-9, US-12")
    data object AddChore : Screen("add_chore", "S7", R.string.add_chore_title, "US-9, US-12")
    data object ShoppingList : Screen("shopping", "S8", R.string.nav_shopping, "US-13")
    data object AddShoppingItem : Screen("add_shopping_item", "S8", R.string.add_item_title, "US-13")
    data object Maintenance : Screen("maintenance", "S9", R.string.nav_maintenance, "US-10")
    data object Settings : Screen("settings", "S10", R.string.settings_title, "US-2, US-3, US-4")
    data object Activity : Screen("activity", "S11", R.string.activity_title, "RM-0033")
    data object More : Screen("more", "-", R.string.nav_more, "-")
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
    BottomNavItem(Screen.Activity, Icons.Filled.History),
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
    Screen.Activity.route,
    Screen.Settings.route
)
