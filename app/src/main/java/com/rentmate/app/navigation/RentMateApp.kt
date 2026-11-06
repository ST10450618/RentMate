package com.rentmate.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.rentmate.app.ui.feedback.CollectFeedback
import com.rentmate.app.ui.feedback.LocalSnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rentmate.app.R
import com.rentmate.app.auth.StartDestination
import com.rentmate.app.auth.StartupState
import com.rentmate.app.auth.StartupViewModel
import com.rentmate.app.notifications.DeepLinkInbox
import com.rentmate.app.notifications.DeepLinkStep
import com.rentmate.app.notifications.PushDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import com.rentmate.app.ui.screens.AddBillScreen
import com.rentmate.app.ui.screens.AddChoreScreen
import com.rentmate.app.ui.screens.ActivityScreen
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
import com.rentmate.app.ui.sync.SyncBannerHost

/**
 * The navigation host and app scaffold.
 *
 * @param deepLinks screens queued by notification taps (RM-0023). Null in
 *   previews and tests, where no tap can arrive.
 */
@Composable
fun RentMateApp(
    navController: NavHostController = rememberNavController(),
    startupViewModel: StartupViewModel = hiltViewModel(),
    deepLinks: DeepLinkInbox? = null
) {
    // RM-0002: wait for the saved session before choosing the first screen.
    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    val start = (startup as? StartupState.Ready)?.destination
    if (start == null) {
        SplashScreen()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // RM-0023: open the screen of a tapped notification. The tab is anchored on
    // the Dashboard, so Back returns there. A tap waits on Login and household setup.
    val pendingFlow = deepLinks?.pending ?: remember { MutableStateFlow<String?>(null) }
    val pendingRoute by pendingFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute, currentRoute) {
        val pending = pendingRoute ?: return@LaunchedEffect
        when (val step = PushDeepLink.nextStep(pending, currentRoute)) {
            DeepLinkStep.Hold -> Unit
            DeepLinkStep.AlreadyThere -> deepLinks?.consume(pending)
            is DeepLinkStep.Open -> {
                navController.navigateTopLevel(step.screen)
                deepLinks?.consume(pending)
            }
        }
    }

    // RM-0052: the one SnackbarHostState for the whole app.
    val snackbarHostState = remember { SnackbarHostState() }
    CollectFeedback(snackbarHostState)

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in routesWithBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = { navController.navigateTopLevel(item.screen) },
                            icon = { Icon(item.icon, contentDescription = stringResource(item.screen.titleRes)) },
                            label = { Text(stringResource(item.screen.titleRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
        // RM-0019: one offline/sync banner above every signed-in screen.
        if (currentRoute != null && currentRoute != Screen.Login.route) SyncBannerHost()
        NavHost(
            navController = navController,
            startDestination = start.toScreen().route,
            modifier = Modifier.weight(1f)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onSignedInNewUser = { navController.onSignedIn(hasHousehold = false) },
                    onSignedInExistingUser = { navController.onSignedIn(hasHousehold = true) }
                )
            }
            composable(Screen.HouseholdSetup.route) {
                HouseholdSetupScreen(onDone = { navController.onHouseholdReady() })
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
            composable(Screen.Activity.route) { ActivityScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onSignedOut = { navController.onSignedOut() }
                )
            }
        }
        }
    }
    }
}

private fun StartDestination.toScreen(): Screen = when (this) {
    StartDestination.LOGIN -> Screen.Login
    StartDestination.HOUSEHOLD_SETUP -> Screen.HouseholdSetup
    StartDestination.DASHBOARD -> Screen.Dashboard
}

/** Shown for the moment it takes to load the saved session at launch. */
@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}
