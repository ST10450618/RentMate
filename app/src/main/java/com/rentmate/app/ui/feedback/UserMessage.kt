package com.rentmate.app.ui.feedback

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RM-0052: one short message for the snackbar. Only resource IDs live here, so
 * the UI layer resolves the text in the user's language.
 *
 * When [retry] is set the snackbar shows a Retry button that calls it.
 */
data class UserMessage(
    @StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    val retry: (() -> Unit)? = null
) {
    /** The snackbar stays longer when there is a button to tap. */
    val longDuration: Boolean get() = retry != null
}

/**
 * Picks the message for a failed action. Cases the user can act on (no
 * permission, session expired, duplicate) use the shared [ErrorMapper] wording.
 * Offline and unknown failures use the action's own message, because it says
 * what failed and what to do next.
 *
 * [notAllowedRes] replaces the generic permission message with a specific one.
 * [unknownRes] replaces [actionRes] for failures that are not network or permission related.
 */
@StringRes
fun actionErrorRes(
    error: AppError,
    @StringRes actionRes: Int,
    @StringRes notAllowedRes: Int? = null,
    @StringRes unknownRes: Int = actionRes
): Int = when (error) {
    AppError.NO_CONNECTION -> actionRes
    AppError.GENERIC -> unknownRes
    AppError.NOT_ALLOWED -> notAllowedRes ?: error.messageRes
    else -> error.messageRes
}

/**
 * The single place every ViewModel reports one-off feedback to. It is a Hilt
 * singleton; the app scaffold collects [messages] once and shows them in the
 * one shared SnackbarHostState.
 *
 * A repeat of the same message within [DUPLICATE_WINDOW_MS] is dropped, so
 * tapping a failing button several times does not queue the same snackbar.
 */
@Singleton
class FeedbackCenter @Inject constructor() {

    private val channel = Channel<UserMessage>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Collected once, in the app scaffold. */
    val messages: Flow<UserMessage> = channel.receiveAsFlow()

    /** Replaced in tests. */
    internal var now: () -> Long = System::currentTimeMillis

    /** Takes everything waiting in the queue. For tests. */
    internal fun drain(): List<UserMessage> = buildList {
        while (true) add(channel.tryReceive().getOrNull() ?: break)
    }

    private var lastRes: Int? = null
    private var lastAt = 0L

    fun show(message: UserMessage) {
        val time = now()
        if (message.messageRes == lastRes && time - lastAt < DUPLICATE_WINDOW_MS) return
        lastRes = message.messageRes
        lastAt = time
        channel.trySend(message)
    }

    fun show(@StringRes messageRes: Int, retry: (() -> Unit)? = null) = show(UserMessage(messageRes, retry = retry))

    /**
     * Reports a failed action. The cause is logged here and mapped to a message
     * with [actionErrorRes]. Retry is offered only when trying again can help.
     */
    fun failure(
        cause: Throwable,
        @StringRes actionRes: Int,
        retry: (() -> Unit)? = null,
        @StringRes notAllowedRes: Int? = null,
        @StringRes unknownRes: Int = actionRes
    ) {
        val error = ErrorMapper.classifyAndLog(cause)
        val canRetry = error == AppError.NO_CONNECTION || error == AppError.GENERIC
        show(
            UserMessage(
                messageRes = actionErrorRes(error, actionRes, notAllowedRes, unknownRes),
                retry = if (canRetry) retry else null
            )
        )
    }

    companion object {
        const val DUPLICATE_WINDOW_MS = 1_500L
    }
}

/** Provided by the app scaffold so dialogs can show the same snackbars in front of themselves. */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}
