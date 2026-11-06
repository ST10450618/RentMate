package com.rentmate.app.ui.feedback

import com.rentmate.app.R
import com.rentmate.app.ui.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/** RM-0052: message choice and duplicate suppression for snackbars. */
class FeedbackCenterTest {

    private var time = 10_000L
    private val feedback = FeedbackCenter().apply { now = { time } }

    @Test
    fun `offline failures use the action message`() {
        assertEquals(
            R.string.feedback_save_bill_failed,
            actionErrorRes(AppError.NO_CONNECTION, R.string.feedback_save_bill_failed)
        )
    }

    @Test
    fun `permission and session failures use the shared wording`() {
        assertEquals(R.string.error_not_allowed, actionErrorRes(AppError.NOT_ALLOWED, R.string.feedback_save_bill_failed))
        assertEquals(R.string.error_session_expired, actionErrorRes(AppError.SESSION_EXPIRED, R.string.feedback_save_bill_failed))
        assertEquals(R.string.error_already_exists, actionErrorRes(AppError.ALREADY_EXISTS, R.string.feedback_save_bill_failed))
    }

    @Test
    fun `a specific permission message replaces the generic one`() {
        assertEquals(
            R.string.feedback_mark_paid_not_allowed,
            actionErrorRes(
                AppError.NOT_ALLOWED,
                R.string.feedback_mark_paid_failed,
                notAllowedRes = R.string.feedback_mark_paid_not_allowed
            )
        )
    }

    @Test
    fun `unknown failures can use their own message`() {
        assertEquals(
            R.string.error_invalid_invite_code,
            actionErrorRes(AppError.GENERIC, R.string.feedback_join_household_failed, unknownRes = R.string.error_invalid_invite_code)
        )
    }

    @Test
    fun `a network failure becomes the action message with a retry`() {
        var retried = false
        feedback.failure(IOException("offline"), R.string.feedback_mark_bought_failed, retry = { retried = true })

        val message = feedback.drain().single()
        assertEquals(R.string.feedback_mark_bought_failed, message.messageRes)
        assertEquals(true, message.longDuration)
        message.retry?.invoke()
        assertEquals(true, retried)
    }

    @Test
    fun `a message without a retry is short and has no button`() {
        feedback.show(R.string.feedback_setting_failed)

        val message = feedback.drain().single()
        assertNull(message.retry)
        assertEquals(false, message.longDuration)
    }

    @Test
    fun `an unknown failure uses its own message`() {
        feedback.failure(
            IllegalStateException("bad row"),
            R.string.feedback_join_household_failed,
            unknownRes = R.string.error_invalid_invite_code
        )

        assertEquals(R.string.error_invalid_invite_code, feedback.drain().single().messageRes)
    }

    @Test
    fun `the same message twice in a row is shown once`() {
        feedback.show(R.string.feedback_setting_failed)
        time += 200
        feedback.show(R.string.feedback_setting_failed)

        assertEquals(1, feedback.drain().size)
    }

    @Test
    fun `the same message is shown again after the window`() {
        feedback.show(R.string.feedback_setting_failed)
        time += FeedbackCenter.DUPLICATE_WINDOW_MS + 1
        feedback.show(R.string.feedback_setting_failed)

        assertEquals(2, feedback.drain().size)
    }

    @Test
    fun `different messages are not treated as duplicates`() {
        feedback.show(R.string.feedback_setting_failed)
        feedback.show(R.string.feedback_language_failed)

        assertEquals(
            listOf(R.string.feedback_setting_failed, R.string.feedback_language_failed),
            feedback.drain().map { it.messageRes }
        )
        assertNotNull(feedback)
    }
}
