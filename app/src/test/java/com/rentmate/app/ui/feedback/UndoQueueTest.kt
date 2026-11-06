package com.rentmate.app.ui.feedback

import com.rentmate.app.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** RM-0056 part 4: Undo delays the commit by 5 s and cancels it when tapped. */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoQueueTest {

    private val scope = TestScope()
    private val feedback = FeedbackCenter()
    private val queue = UndoQueue(feedback, scope, UndoQueue.UNDO_WINDOW_MS)

    private val log = mutableListOf<String>()
    private var failCommit: Throwable? = null

    private fun action(key: String = "pay:s1") = UndoableAction(
        key = key,
        hide = { log += "hide" },
        restore = { log += "restore" },
        commit = {
            failCommit?.let { throw it }
            log += "commit"
        },
        onCommitFailed = { log += "failed" }
    )

    @Test
    fun windowIsFiveSeconds() {
        assertEquals(5_000L, UndoQueue.UNDO_WINDOW_MS)
    }

    @Test
    fun hidesAtOnceAndCommitsOnlyAfterTheWindow() {
        queue.submit(UserMessage(R.string.undo_marked_paid), action())
        assertEquals(listOf("hide"), log)

        scope.advanceTimeBy(4_999)
        scope.runCurrent()
        assertEquals(listOf("hide"), log)

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(listOf("hide", "commit"), log)
        assertTrue(queue.pendingKeys.isEmpty())
    }

    @Test
    fun snackbarCarriesUndoThatCancelsTheCommit() {
        queue.submit(UserMessage(R.string.undo_item_deleted), action())
        val shown = feedback.drain().single()
        assertEquals(R.string.undo_item_deleted, shown.messageRes)
        assertNotNull(shown.undo)

        scope.advanceTimeBy(3_000)
        shown.undo!!.invoke()
        scope.advanceTimeBy(10_000)
        scope.runCurrent()

        assertEquals(listOf("hide", "restore"), log)
    }

    @Test
    fun undoAfterTheWindowDoesNothing() {
        val a = action()
        queue.submit(UserMessage(R.string.undo_chore_done), a)
        scope.advanceTimeBy(6_000)
        scope.runCurrent()

        assertFalse(queue.undo(a))
        assertEquals(listOf("hide", "commit"), log)
    }

    @Test
    fun failedCommitRestoresAndReports() {
        failCommit = IOException("offline")
        queue.submit(UserMessage(R.string.undo_marked_paid), action())
        scope.advanceTimeBy(5_001)
        scope.runCurrent()

        assertEquals(listOf("hide", "restore", "failed"), log)
    }

    @Test
    fun repeatTapInsideTheWindowIsIgnored() {
        assertTrue(queue.submit(UserMessage(R.string.undo_marked_paid), action()))
        assertFalse(queue.submit(UserMessage(R.string.undo_marked_paid), action()))
        scope.advanceTimeBy(5_001)
        scope.runCurrent()

        assertEquals(listOf("hide", "commit"), log)
    }

    @Test
    fun undoMessagesAreNotDroppedAsDuplicates() {
        queue.submit(UserMessage(R.string.undo_item_deleted), action("delete:a"))
        queue.submit(UserMessage(R.string.undo_item_deleted), action("delete:b"))
        assertEquals(2, feedback.drain().size)
    }
}
