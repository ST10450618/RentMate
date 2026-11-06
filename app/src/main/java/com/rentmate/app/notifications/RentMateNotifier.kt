package com.rentmate.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rentmate.app.MainActivity
import com.rentmate.app.R

private const val TAG = "RentMateNotifier"

/** Intent extra carrying the screen route a notification tap should open (read by [PushDeepLink]). */
const val EXTRA_OPEN_SCREEN = "com.rentmate.app.extra.OPEN_SCREEN"

/** Intent extra carrying the id of the bill, chore or request the notification is about. */
const val EXTRA_REF_ID = "com.rentmate.app.extra.REF_ID"

/**
 * Posts RentMate notifications (RM-0021).
 *
 * The FCM service (RM-0021/RM-0022) hands every incoming push to [show]; it is
 * kept separate from Firebase so it can be tested and reused for local alerts.
 * It never throws: if the user has denied the POST_NOTIFICATIONS permission
 * (Android 13+) or switched notifications off, the message is logged and dropped.
 *
 * Based on: Android Developers, Create a notification,
 * https://developer.android.com/develop/ui/compose/notifications/create-notification (accessed 2026-11-06)
 * Based on: Android Developers, Notification runtime permission,
 * https://developer.android.com/develop/ui/compose/notifications/notification-permission (accessed 2026-11-06)
 */
object RentMateNotifier {

    /** True when the app may post notifications right now. */
    fun canNotify(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Shows one notification on [channel]. Tapping it opens [MainActivity] with
     * [EXTRA_OPEN_SCREEN] set to the channel's screen and [EXTRA_REF_ID] set to [refId].
     *
     * @param notificationId stable per item (e.g. the bill id's hash) so an
     *   update replaces the earlier notification instead of stacking.
     */
    fun show(
        context: Context,
        channel: RentMateChannel,
        title: String,
        body: String,
        refId: String? = null,
        notificationId: Int = (refId ?: "$title$body").hashCode()
    ) {
        if (!canNotify(context)) {
            Log.i(TAG, "Notifications disabled or not permitted; dropping ${channel.id} notification")
            return
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            // RM-0023: SINGLE_TOP hands the tap to a running MainActivity through
            // onNewIntent, so the activity is not rebuilt and the lock state is kept.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SCREEN, channel.screen)
            refId?.let { putExtra(EXTRA_REF_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_rentmate)
            .setColor(ContextCompat.getColor(context, R.color.rentmate_aloe))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Posted ${channel.id} notification id=$notificationId")
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
            Log.w(TAG, "Could not post notification", e)
        }
    }
}
