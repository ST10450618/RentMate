package com.rentmate.app.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rentmate.app.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

private const val TAG = "RentMateMessaging"

/**
 * Receives FCM tokens and messages (RM-0021).
 *
 * - [onNewToken]: FCM issued a new token (first start, reinstall, cleared
 *   data). It is saved for the signed-in user. If nobody is signed in,
 *   PushTokenSync saves it after the next sign-in.
 * - [onMessageReceived]: a push arrived. It is shown on its channel unless the
 *   user switched that category off in RentMate's Settings.
 *
 * Based on: Firebase docs, Receive messages in Android apps,
 * https://firebase.google.com/docs/cloud-messaging/android/receive-messages (accessed 2026-11-06)
 */
@AndroidEntryPoint
class RentMateMessagingService : FirebaseMessagingService() {

    /** Saves FCM tokens for the signed-in user. */
    @Inject lateinit var registrar: DeviceTokenRegistrar

    /** FCM rotated the token; save it without blocking the service. */
    override fun onNewToken(token: String) {
        Log.d(TAG, "new FCM token")
        registrar.registerInBackground(token)
    }

    /** Parses the data payload, checks the user's toggle and posts the notification. */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "push received: ${message.data.size} data key(s)")
        val push = PushMessage.parse(
            data = message.data,
            fallbackTitle = message.notification?.title,
            fallbackBody = message.notification?.body
        )
        if (push == null) {
            Log.w(TAG, "ignored a push with no known channel or no title")
            return
        }

        // FCM calls this on a background thread and allows several seconds,
        // so a short blocking read of the local settings is safe.
        val enabled = runBlocking {
            push.isAllowedBy(SettingsRepository(applicationContext).settings.first())
        }
        if (!enabled) {
            Log.i(TAG, "${push.channel.id} pushes are off in Settings; dropped")
            return
        }

        RentMateNotifier.show(
            context = applicationContext,
            channel = push.channel,
            title = push.title,
            body = push.body,
            refId = push.refId
        )
    }
}
