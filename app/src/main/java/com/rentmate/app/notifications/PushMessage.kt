package com.rentmate.app.notifications

/**
 * One push message from the RM-0022 Edge Function, read from the FCM data map.
 *
 * The server sends data-only messages so that [RentMateMessagingService] always
 * receives them, also when the app is in the background. The service then picks
 * the channel, the tap target and the user's toggle. The keys are documented in
 * supabase/migrations/0014_device_tokens.sql.
 */
data class PushMessage(
    val channel: RentMateChannel,
    val title: String,
    val body: String,
    /** Id of the bill, chore or request, for the RM-0023 deep link. Null when absent. */
    val refId: String?
) {
    companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_ID = "id"

        /** The plan calls the deep-link key "screen". Each channel id is also its screen route. */
        const val KEY_SCREEN = "screen"

        private const val TITLE_MAX = 120
        private const val BODY_MAX = 1_000
        private const val ID_MAX = 64

        /**
         * Parses an FCM data map. [fallbackTitle] and [fallbackBody] come from the
         * message's notification block, for example a test message sent from the
         * Firebase console.
         *
         * Returns null when the channel is unknown or the message has no title.
         * The app then shows nothing, because a blank or misfiled notification
         * is worse than none.
         */
        fun parse(
            data: Map<String, String>,
            fallbackTitle: String? = null,
            fallbackBody: String? = null
        ): PushMessage? {
            val channel = RentMateChannel.fromId(data[KEY_CHANNEL]?.trim()?.lowercase())
                ?: RentMateChannel.fromId(data[KEY_SCREEN]?.trim()?.lowercase())
                ?: return null
            val title = (data[KEY_TITLE] ?: fallbackTitle)?.trim()?.take(TITLE_MAX)
            if (title.isNullOrEmpty()) return null
            val body = (data[KEY_BODY] ?: fallbackBody)?.trim()?.take(BODY_MAX).orEmpty()
            val refId = data[KEY_ID]?.trim()?.takeIf { it.isNotEmpty() && it.length <= ID_MAX }
            return PushMessage(channel, title, body, refId)
        }
    }
}
