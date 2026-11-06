package com.rentmate.app.ui.feedback

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.rentmate.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Gives the app scaffold access to the singleton [FeedbackCenter]. */
@HiltViewModel
class FeedbackViewModel @Inject constructor(val center: FeedbackCenter) : ViewModel()

/**
 * RM-0052: shows every [UserMessage] in the one shared [hostState].
 *
 * collectLatest means a new message replaces the one on screen, so snackbars
 * never queue up. The text is resolved here, in the UI layer, so it follows
 * the user's language.
 *
 * A message with an Undo action stays for [UndoQueue.UNDO_WINDOW_MS] and has
 * no dismiss button, so the Undo button is the only action on it.
 */
@Composable
fun CollectFeedback(hostState: SnackbarHostState, viewModel: FeedbackViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, hostState) {
        viewModel.center.messages.collectLatest { message ->
            val text = context.getString(message.messageRes, *message.formatArgs.toTypedArray())
            val undo = message.undo
            if (undo != null) {
                // RM-0056: Undo stays for exactly 5 s. Cancelling showSnackbar dismisses it.
                val undoResult = withTimeoutOrNull(UndoQueue.UNDO_WINDOW_MS) {
                    hostState.showSnackbar(
                        message = text,
                        actionLabel = context.getString(R.string.feedback_undo),
                        duration = SnackbarDuration.Indefinite
                    )
                }
                if (undoResult == SnackbarResult.ActionPerformed) undo()
                return@collectLatest
            }
            val result = hostState.showSnackbar(
                message = text,
                actionLabel = message.retry?.let { context.getString(R.string.feedback_retry) },
                withDismissAction = message.retry == null,
                duration = if (message.longDuration) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.retry?.invoke()
        }
    }
}
