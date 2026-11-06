package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.ui.feedback.FeedbackCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** RM-0052: a setting that fails to save flips back and says why. */
class SettingRollbackTest {

    private val feedback = FeedbackCenter()

    /** A stand-in for the DataStore value behind one switch. */
    private var switchOn = false

    @Test
    fun `a saved setting stays on and shows no message`() = runTest {
        saveWithRollback(
            apply = { switchOn = true },
            save = { },
            revert = { switchOn = false },
            failedRes = R.string.feedback_setting_failed,
            feedback = feedback
        )

        assertTrue(switchOn)
        assertTrue(feedback.drain().isEmpty())
    }

    @Test
    fun `a failed save flips the switch back and shows a message`() = runTest {
        saveWithRollback(
            apply = { switchOn = true },
            save = { throw IOException("offline") },
            revert = { switchOn = false },
            failedRes = R.string.feedback_setting_failed,
            feedback = feedback
        )

        assertEquals(false, switchOn)
        assertEquals(listOf(R.string.feedback_setting_failed), feedback.drain().map { it.messageRes })
    }

    @Test
    fun `the setting is applied before it is saved so the switch moves at once`() = runTest {
        val order = mutableListOf<String>()
        saveWithRollback(
            apply = { order += "apply" },
            save = { order += "save" },
            revert = { order += "revert" },
            failedRes = R.string.feedback_setting_failed,
            feedback = feedback
        )

        assertEquals(listOf("apply", "save"), order)
    }

    @Test
    fun `a failing revert still shows the message`() = runTest {
        saveWithRollback(
            apply = { },
            save = { throw IOException("offline") },
            revert = { throw IllegalStateException("disk full") },
            failedRes = R.string.feedback_language_failed,
            feedback = feedback
        )

        assertEquals(listOf(R.string.feedback_language_failed), feedback.drain().map { it.messageRes })
    }

    @Test
    fun `cancellation is not reported as a failure`() = runTest {
        try {
            saveWithRollback(
                apply = { switchOn = true },
                save = { throw CancellationException("left the screen") },
                revert = { switchOn = false },
                failedRes = R.string.feedback_setting_failed,
                feedback = feedback
            )
            fail("cancellation should propagate")
        } catch (e: CancellationException) {
            // expected
        }

        assertTrue(switchOn)
        assertTrue(feedback.drain().isEmpty())
    }
}
