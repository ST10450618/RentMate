package com.rentmate.app.navigation

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rentmate.app.ui.screens.AddBillScreen
import com.rentmate.app.ui.screens.AddChoreScreen
import com.rentmate.app.ui.screens.AddShoppingItemScreen
import com.rentmate.app.ui.screens.BillsScreen
import com.rentmate.app.ui.screens.ChoresScreen
import com.rentmate.app.ui.screens.DashboardScreen
import com.rentmate.app.ui.screens.HouseholdSetupScreen
import com.rentmate.app.ui.screens.LoginScreen
import com.rentmate.app.ui.screens.MaintenanceScreen
import com.rentmate.app.ui.screens.MoreScreen
import com.rentmate.app.ui.screens.SettingsScreen
import com.rentmate.app.ui.screens.ShoppingListScreen

private const val TAG = "RentMateApp"

@Composable
fun RentMateApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in routesWithBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = { navController.navigateTopLevel(item.screen) },
                            icon = { Icon(item.icon, contentDescription = item.screen.title) },
                            label = { Text(item.screen.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onSignedInNewUser = { navController.navigate(Screen.HouseholdSetup.route) },
                    onSignedInExistingUser = { navController.navigateTopLevel(Screen.Dashboard) }
                )
            }
            composable(Screen.HouseholdSetup.route) {
                HouseholdSetupScreen(onDone = { navController.navigateTopLevel(Screen.Dashboard) })
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onOpenBills = { navController.navigateTopLevel(Screen.Bills) },
                    onOpenChores = { navController.navigateTopLevel(Screen.Chores) }
                )
            }
            composable(Screen.Bills.route) {
                BillsScreen(onAddBill = { navController.navigate(Screen.AddBill.route) })
            }
            composable(Screen.AddBill.route) {
                AddBillScreen(onClose = { navController.popBackStack() })
            }
            composable(Screen.Chores.route) {
                ChoresScreen(onAddChore = { navController.navigate(Screen.AddChore.route) })
            }
            composable(Screen.AddChore.route) {
                AddChoreScreen(onClose = { navController.popBackStack() })
            }
            composable(Screen.More.route) {
                MoreScreen(onOpen = { screen -> navController.navigate(screen.route) })
            }
            composable(Screen.ShoppingList.route) {
                ShoppingListScreen(onAddItem = { navController.navigate(Screen.AddShoppingItem.route) })
            }
            composable(Screen.AddShoppingItem.route) {
                AddShoppingItemScreen(onClose = { navController.popBackStack() })
            }
            composable(Screen.Maintenance.route) { MaintenanceScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

/**
 * Switching top-level tabs should not stack screens forever. This pops back to
 * the start destination, keeps each tab's own state, and avoids launching a
 * second copy of a tab that is already showing.
 */
private fun NavHostController.navigateTopLevel(screen: Screen) {
    Log.d(TAG, "navigateTopLevel -> ${screen.id} ${screen.route}")
    navigate(screen.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
