package com.rentmate.app.navigation

import com.rentmate.app.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app. Screen IDs (S1-S10) match the UI mockups and the
 * navigation wireflow in the Part 1 design document, so a screen can be traced
 * from the document to the code and back.
 *
 * RM-0056: the tabs are named after jobs (Home, Money, Chores, House). "House"
 * is not an S-numbered screen: it is the fourth tab, a menu that reaches
 * Shopping (S8) and Maintenance (S9). Settings (S10) opens from the avatar in
 * the header, and Activity opens from Home. The routes did not change, so the
 * notification deep links from RM-0023 still name the same screens.
 */
sealed class Screen(
    val route: String,
    val id: String,
    @StringRes val titleRes: Int,
    val stories: String
) {
    data object Login : Screen("login", "S1", R.string.nav_sign_in, "US-1, US-2, US-4")
    data object HouseholdSetup : Screen("household", "S2", R.string.household_setup_title, "US-5")
    data object Dashboard : Screen("dashboard", "S3", R.string.nav_home, "US-3")
    data object Bills : Screen("bills", "S4", R.string.nav_money, "US-6, US-11")
    data object AddBill : Screen("add_bill", "S5", R.string.add_bill_title, "US-6")
    data object Chores : Screen("chores", "S6", R.string.nav_chores, "US-9, US-12")
    data object AddChore : Screen("add_chore", "S7", R.string.add_chore_title, "US-9, US-12")
    data object ShoppingList : Screen("shopping", "S8", R.string.nav_shopping, "US-13")
    data object AddShoppingItem : Screen("add_shopping_item", "S8", R.string.add_item_title, "US-13")
    data object Maintenance : Screen("maintenance", "S9", R.string.nav_maintenance, "US-10")
    data object Settings : Screen("settings", "S10", R.string.settings_title, "US-2, US-3, US-4")
    data object Activity : Screen("activity", "S11", R.string.activity_title, "RM-0033")
    data object House : Screen("house", "-", R.string.nav_house, "-")
}

/**
 * A destination with its icon.
 *
 * @property selectedIcon the filled icon a selected tab shows. The bottom tabs
 *   swap from [icon] (outlined) to this one (RM-0056).
 */
data class BottomNavItem(val screen: Screen, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * The four tabs, named after jobs. Material 3 recommends three to five.
 * Icons: a house for Home, a wallet for Money, a check in a circle for
 * Chores and a toolbox for House, each as an outlined and filled pair.
 */
val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Outlined.Home, Icons.Filled.Home),
    BottomNavItem(Screen.Bills, Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    BottomNavItem(Screen.Chores, Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    BottomNavItem(Screen.House, Icons.Outlined.HomeRepairService, Icons.Filled.HomeRepairService)
)

/** Destinations reached from the House tab. */
val houseItems = listOf(
    BottomNavItem(Screen.ShoppingList, Icons.Filled.ShoppingCart),
    BottomNavItem(Screen.Maintenance, Icons.Filled.Build)
)

/** The routes at the top of each tab. Their header shows the avatar that opens Settings. */
val tabRootRoutes = bottomNavItems.map { it.screen.route }.toSet()

/**
 * Routes that show the bottom bar. Login and household setup come before the
 * app proper; the Add forms are modal and return to their parent on save,
 * which keeps the back stack shallow.
 */
val routesWithBottomBar = tabRootRoutes + setOf(
    Screen.ShoppingList.route,
    Screen.Maintenance.route,
    Screen.Activity.route,
    Screen.Settings.route
)

/**
 * The tab that is highlighted while [route] shows. Shopping and Maintenance
 * belong to House, and Activity to Home. Settings and the forms belong to no tab.
 */
fun tabFor(route: String?): Screen? = when (route) {
    Screen.Dashboard.route, Screen.Activity.route -> Screen.Dashboard
    Screen.Bills.route -> Screen.Bills
    Screen.Chores.route -> Screen.Chores
    Screen.House.route, Screen.ShoppingList.route, Screen.Maintenance.route -> Screen.House
    else -> null
}
