package com.rentmate.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R
import com.rentmate.app.data.SettingToggle

private const val TAG = "NotificationChannels"

/**
 * One Android notification channel per kind of household update (RM-0021).
 *
 * Separate channels let users silence, say, shopping-list updates in system
 * settings while keeping bill reminders, mirroring the per-category toggles in
 * RentMate's own Settings screen ([toggle]). Channel IDs are stable: once a
 * channel ships, its ID must never change or users lose their choices.
 *
 * The [id] is also the `channel` value the push Edge Function (RM-0022) sends
 * in each FCM data payload, so the client knows where to post the message.
 *
 * Reference: https://developer.android.com/develop/ui/views/notifications/channels
 */
enum class RentMateChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val importance: Int,
    val toggle: SettingToggle,
    /** Route in navigation/Screen.kt a tap on this notification opens (used by RM-0023 deep links). */
    val screen: String
) {
    BILLS(
        "bills", R.string.channel_bills_name, R.string.channel_bills_desc,
        NotificationManager.IMPORTANCE_HIGH, SettingToggle.NOTIFY_BILLS, "bills"
    ),
    CHORES(
        "chores", R.string.channel_chores_name, R.string.channel_chores_desc,
        NotificationManager.IMPORTANCE_DEFAULT, SettingToggle.NOTIFY_CHORES, "chores"
    ),
    SHOPPING(
        "shopping", R.string.channel_shopping_name, R.string.channel_shopping_desc,
        NotificationManager.IMPORTANCE_LOW, SettingToggle.NOTIFY_SHOPPING, "shopping"
    ),
    MAINTENANCE(
        "maintenance", R.string.channel_maintenance_name, R.string.channel_maintenance_desc,
        NotificationManager.IMPORTANCE_DEFAULT, SettingToggle.NOTIFY_MAINTENANCE, "maintenance"
    );

    companion object {
        /** Looks up a channel from an FCM payload value; returns null for an unknown id. */
        fun fromId(id: String?): RentMateChannel? = entries.firstOrNull { it.id == id }

        /**
         * Registers every channel with the system. Safe to call on every app
         * start: re-creating an existing channel only updates its name and
         * description (translated into the current language), never the user's
         * chosen importance. minSdk is 26, so channels always exist.
         */
        fun createAll(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channels = entries.map { channel ->
                NotificationChannel(channel.id, context.getString(channel.nameRes), channel.importance).apply {
                    description = context.getString(channel.descriptionRes)
                }
            }
            manager.createNotificationChannels(channels)
            Log.i(TAG, "Registered ${channels.size} notification channels")
        }
    }
}
