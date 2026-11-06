package com.rentmate.app.ui.screens

import com.rentmate.app.R
import com.rentmate.app.data.ChoreRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** RM-0058: cycle days, the next-chore pick and the RM-0048 completion guard. */
class ChoresRulesTest {
    // Thursday 2026-11-05.
    private val today = LocalDate.of(2026, 11, 5)

    private fun chore(id: String, recurrence: String = "Weekly", rotation: List<String> = listOf("me", "you"), index: Int = 0) =
        ChoreRow(id, "h", "Chore $id", recurrence, 2, rotation, index, "2026-11-01T00:00:00Z")

    @Test
    fun weeklyCycleRunsMondayToSunday() {
        assertEquals(LocalDate.of(2026, 11, 2), cycleStart("Weekly", today))
        assertEquals(LocalDate.of(2026, 11, 8), cycleDueDate("Weekly", today))
        assertEquals(LocalDate.of(2026, 11, 22), cycleDueDate("Weekly", today, cyclesAhead = 2))
    }

    @Test
    fun monthlyCycleEndsOnTheLastDay() {
        assertEquals(LocalDate.of(2026, 11, 1), cycleStart("Monthly", today))
        assertEquals(LocalDate.of(2026, 11, 30), cycleDueDate("Monthly", today))
        assertEquals(LocalDate.of(2027, 2, 28), cycleDueDate("Monthly", today, cyclesAhead = 3))
    }

    @Test
    fun onceChoreHasNoDueDayAndAnyCompletionCounts() {
        assertNull(cycleDueDate("Once", today))
        assertTrue(countsForCurrentCycle("Once", LocalDate.of(2025, 1, 1), today))
    }

    @Test
    fun completionLastWeekDoesNotCountThisWeek() {
        assertFalse(countsForCurrentCycle("Weekly", LocalDate.of(2026, 11, 1), today))
        assertTrue(countsForCurrentCycle("Weekly", LocalDate.of(2026, 11, 2), today))
    }

    @Test
    fun nextChoreIsMineNotDoneAndDueFirst() {
        val monthly = chore("m", "Monthly")
        val weekly = chore("w", "Weekly")
        val theirs = chore("t", "Weekly", index = 1)
        val chores = listOf(monthly, theirs, weekly)
        assertEquals(weekly, nextChoreFor(chores, "me", emptySet(), today))
        assertEquals(monthly, nextChoreFor(chores, "me", setOf("w"), today))
        assertNull(nextChoreFor(chores, null, emptySet(), today))
    }

    @Test
    fun defaultRotationPutsYouFirstThenNames() {
        val rotation = defaultRotation(
            listOf(
                RotationMember("t", "Thandi", isYou = false),
                RotationMember("x", null, isYou = false),
                RotationMember("n", "Nomsa", isYou = true),
                RotationMember("s", "sipho", isYou = false)
            )
        )
        assertEquals(listOf("n", "s", "t", "x"), rotation.map { it.userId })
    }

    @Test
    fun onlyTheAssigneeCanComplete() {
        val mine = chore("a")
        assertNull(completeBlockedRes(mine, "me", alreadyDone = false))
        assertEquals(R.string.chore_error_not_assignee, completeBlockedRes(mine, "you", alreadyDone = false))
        assertEquals(R.string.chore_error_already_done, completeBlockedRes(mine, "me", alreadyDone = true))
        assertNull(completeBlockedRes(chore("open", rotation = emptyList()), "you", alreadyDone = false))
    }
}
