package com.rentmate.app.navigation

import android.util.Log
import androidx.navigation.NavHostController

private const val TAG = "AppNavigation"

/*
 * RM-0003: back-stack rules.
 *
 * Login and household setup are one-way doors: once passed, they are removed
 * from the back stack, so Back on the Dashboard closes the app instead of
 * returning to Login (whose Success state would bounce straight back, a loop).
 * Every tab is anchored on the Dashboard, never on graph.startDestinationId,
 * because the start destination may be Login and is no longer on the stack.
 */

/** After Google sign-in: new users set up a household, existing users go to the Dashboard. */
fun NavHostController.onSignedIn(hasHousehold: Boolean) {
    val next = if (hasHousehold) Screen.Dashboard else Screen.HouseholdSetup
    Log.d(TAG, "onSignedIn -> ${next.route}")
    navigate(next.route) {
        popUpTo(Screen.Login.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** After creating or joining a household. */
fun NavHostController.onHouseholdReady() {
    Log.d(TAG, "onHouseholdReady -> ${Screen.Dashboard.route}")
    navigate(Screen.Dashboard.route) {
        popUpTo(Screen.HouseholdSetup.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** After sign-out: clear everything so Back can't reopen a signed-in screen. */
fun NavHostController.onSignedOut() {
    Log.d(TAG, "onSignedOut -> ${Screen.Login.route}")
    navigate(Screen.Login.route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Switching top-level tabs should not stack screens forever. This pops back to
 * the Dashboard, keeps each tab's own state, and avoids launching a second copy
 * of a tab that is already showing. Back from any tab returns to the Dashboard;
 * Back on the Dashboard closes the app.
 *
 * @param restore false opens the tab at its root instead of where the user
 *   left it. Deep links pass false so the stack is predictable.
 */
fun NavHostController.navigateTopLevel(screen: Screen, restore: Boolean = true) {
    Log.d(TAG, "navigateTopLevel -> ${screen.id} ${screen.route}")
    navigate(screen.route) {
        popUpTo(Screen.Dashboard.route) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

/**
 * RM-0056: the screens to open, in order, for a notification about [target].
 * Shopping and Maintenance sit under the House tab, so their path goes
 * through House. Back then returns to House, then Home. Every other target is
 * a tab of its own. This is a pure function, so PushDeepLinkTest covers it.
 */
fun deepLinkPath(target: Screen): List<Screen> = when (target) {
    Screen.ShoppingList, Screen.Maintenance -> listOf(Screen.House, target)
    else -> listOf(target)
}

/**
 * Opens a notification's screen (RM-0023) along [deepLinkPath]. The first step
 * is anchored on Home, like a tab tap, so Back never reaches Login. Saved tab
 * state is not restored, so the stack is always Home followed by the path.
 */
fun NavHostController.openDeepLink(target: Screen) {
    val path = deepLinkPath(target)
    Log.d(TAG, "openDeepLink -> ${path.joinToString(" > ") { it.route }}")
    navigateTopLevel(path.first(), restore = false)
    path.drop(1).forEach { navigate(it.route) { launchSingleTop = true } }
}
