package com.rentmate.app.notifications

import android.content.Intent
import android.util.Log
import com.rentmate.app.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushDeepLink"

/** What the navigation host must do with a pending notification tap (RM-0023). */
sealed interface DeepLinkStep {
    /** Nothing to do yet: no tap is pending, or the user is not past Login and household setup. */
    data object Hold : DeepLinkStep

    /** The user is already on the target screen. Clear the pending tap and do not navigate. */
    data object AlreadyThere : DeepLinkStep

    /** Navigate to [screen], then clear the pending tap. */
    data class Open(val screen: Screen) : DeepLinkStep
}

/**
 * Maps a notification tap to a screen (RM-0023).
 *
 * A tap reaches MainActivity in one of two ways:
 * - The app posted the notification itself ([RentMateNotifier]). The intent
 *   carries [EXTRA_OPEN_SCREEN].
 * - Android posted it from a message with a notification block while the app
 *   was in the background (for example a Firebase console test message). The
 *   intent then carries the FCM data keys as extras, such as `channel`.
 *
 * The functions here are pure so unit tests can cover them. Only the four
 * screens a push can be about are allowed, so an extra from another app can
 * never open an arbitrary route.
 */
object PushDeepLink {

    /** Screens a notification may open, keyed by route. */
    val targets: Map<String, Screen> = listOf(
        Screen.Bills,
        Screen.Chores,
        Screen.ShoppingList,
        Screen.Maintenance
    ).associateBy { it.route }

    /** Routes where a pending tap must wait: the user has not reached the Dashboard yet. */
    private val waitingRoutes = setOf(Screen.Login.route, Screen.HouseholdSetup.route)

    /**
     * Returns the screen for a tap, or null when the extras name none.
     *
     * @param openScreen value of [EXTRA_OPEN_SCREEN], set by [RentMateNotifier].
     * @param channel the FCM `channel` data key, present on a system-tray tap.
     * @param screen the FCM `screen` data key, accepted as an alias of `channel`.
     */
    fun screenFor(openScreen: String?, channel: String?, screen: String?): Screen? {
        openScreen?.normalised()?.let { route -> targets[route]?.let { return it } }
        val channelRoute = (RentMateChannel.fromId(channel?.normalised())
            ?: RentMateChannel.fromId(screen?.normalised()))?.screen
        return channelRoute?.let { targets[it] }
    }

    /** Reads [screenFor] from a launch intent. Returns null for a normal launch. */
    fun screenFrom(intent: Intent?): Screen? {
        if (intent == null) return null
        // A launch from Recents replays the original intent. Do not open the screen again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return screenFor(
            openScreen = intent.getStringExtra(EXTRA_OPEN_SCREEN),
            channel = intent.getStringExtra(PushMessage.KEY_CHANNEL),
            screen = intent.getStringExtra(PushMessage.KEY_SCREEN)
        )
    }

    /**
     * Decides what to do with [pendingRoute] while [currentRoute] is showing.
     *
     * A tap waits while the start screen is still loading (null route), on
     * Login and on household setup. After sign-in the user reaches the
     * Dashboard and the tap is applied then.
     */
    fun nextStep(pendingRoute: String?, currentRoute: String?): DeepLinkStep {
        val target = pendingRoute?.let { targets[it] } ?: return DeepLinkStep.Hold
        if (currentRoute == null || currentRoute in waitingRoutes) return DeepLinkStep.Hold
        if (currentRoute == target.route) return DeepLinkStep.AlreadyThere
        return DeepLinkStep.Open(target)
    }

    private fun String.normalised(): String = trim().lowercase()
}

/**
 * Holds the screen from the latest notification tap until the navigation host
 * opens it (RM-0023).
 *
 * MainActivity writes to it from onCreate and onNewIntent. RentMateApp reads it
 * once its start screen is ready. It is a singleton so a tap survives the
 * splash screen and the app lock, which both come before navigation.
 */
@Singleton
class DeepLinkInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)

    /** Route of the screen to open, or null when no tap is waiting. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Records a tap. A newer tap replaces an older one that is still waiting. */
    fun offer(screen: Screen) {
        Log.d(TAG, "tap pending -> ${screen.route}")
        _pending.value = screen.route
    }

    /** Clears [route] once it is handled. A newer tap for a different route is kept. */
    fun consume(route: String) {
        if (_pending.compareAndSet(route, null)) Log.d(TAG, "tap handled -> $route")
    }
}
