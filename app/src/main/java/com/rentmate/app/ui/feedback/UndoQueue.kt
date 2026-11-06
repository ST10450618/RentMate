package com.rentmate.app.ui.feedback

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UndoQueue"

/**
 * RM-0056 part 4: Undo for mark paid, delete shopping item and complete chore.
 *
 * Undo works by delaying the commit, not by reversing it on the server.
 * [submit] changes the screen at once ([UndoableAction.hide]) and shows a
 * snackbar with Undo for [windowMs] (5 s). Only when that time is up does it
 * run [UndoableAction.commit], the normal server call. Tapping Undo inside the
 * window cancels the commit and puts the screen back ([UndoableAction.restore]).
 *
 * We delay instead of reversing for two reasons:
 * - The server has no "unmark paid", "un-complete chore" or "undelete" call,
 *   and complete_chore advances the rotation, which cannot be rolled back
 *   safely once other housemates may have seen it.
 * - Nothing reaches the repositories, and so the offline outbox (RM-0017,
 *   RM-0018), until the window closes. An undone action never writes an
 *   outbox entry, so the sync worker never has to replay or cancel one.
 *
 * In plain terms: the change waits 5 s on this phone before it is sent. Undo
 * throws the waiting change away.
 *
 * Commits run in this singleton's own scope, so leaving the screen inside the
 * window does not cancel them. If the app process dies inside the window, the
 * change is lost and the item shows unchanged on the next refresh.
 */
@Singleton
class UndoQueue internal constructor(
    private val feedback: FeedbackCenter,
    private val scope: CoroutineScope,
    private val windowMs: Long
) {
    /** Hilt constructor: commits on the main thread, after [UNDO_WINDOW_MS]. */
    @Inject
    constructor(feedback: FeedbackCenter) :
        this(feedback, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), UNDO_WINDOW_MS)

    private val lock = Any()
    private val pending = mutableMapOf<String, Job>()

    /** Keys of actions that are still inside their Undo window. For tests and logs. */
    val pendingKeys: Set<String> get() = synchronized(lock) { pending.keys.toSet() }

    /**
     * Applies [action] to the screen, offers Undo, and commits after the window.
     *
     * @param message the snackbar text, for example "Marked as paid". Its Undo
     *   button is added here.
     * @return false when an action with the same key is already waiting; the
     *   second tap is ignored so one change is never sent twice.
     */
    fun submit(message: UserMessage, action: UndoableAction): Boolean {
        synchronized(lock) {
            if (pending.containsKey(action.key)) {
                Log.d(TAG, "ignored repeat of ${action.key}")
                return false
            }
            action.hide()
            pending[action.key] = scope.launch {
                delay(windowMs)
                // Leaving the map first means a late Undo can no longer cancel the commit.
                synchronized(lock) { pending.remove(action.key) }
                Log.d(TAG, "commit ${action.key}")
                try {
                    action.commit()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "commit ${action.key} failed", e)
                    action.restore()
                    action.onCommitFailed(e)
                }
            }
        }
        Log.d(TAG, "waiting ${windowMs} ms to commit ${action.key}")
        feedback.show(message.copy(undo = { undo(action) }))
        return true
    }

    /**
     * Cancels [action] if it is still inside its window and restores the screen.
     * Returns false when the window has closed and the commit already started.
     */
    fun undo(action: UndoableAction): Boolean {
        val job = synchronized(lock) { pending.remove(action.key) } ?: run {
            Log.d(TAG, "undo too late for ${action.key}")
            return false
        }
        job.cancel()
        action.restore()
        Log.d(TAG, "undone ${action.key}")
        return true
    }

    companion object {
        /** The plan asks for Undo to stay on screen for 5 seconds. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

/**
 * One change that can be undone for a few seconds. See [UndoQueue].
 *
 * @property key identifies the change, for example "pay:<shareId>". A second
 *   submit with the same key inside the window is ignored.
 * @property hide shows the change on screen straight away, without the server.
 * @property restore puts the screen back. Called on Undo and when [commit] fails.
 * @property commit the real server call, run once the window closes.
 * @property onCommitFailed reports a failed [commit], usually with a Retry snackbar.
 */
class UndoableAction(
    val key: String,
    val hide: () -> Unit,
    val restore: () -> Unit,
    val commit: suspend () -> Unit,
    val onCommitFailed: (Throwable) -> Unit
)
