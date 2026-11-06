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
 */
@AndroidEntryPoint
class RentMateMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: DeviceTokenRegistrar

    override fun onNewToken(token: String) {
        Log.d(TAG, "new FCM token")
        registrar.registerInBackground(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
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
            SettingsRepository(applicationContext).settings.first().isOn(push.channel.toggle)
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
