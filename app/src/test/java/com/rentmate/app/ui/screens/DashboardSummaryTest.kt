package com.rentmate.app.ui.screens

import com.rentmate.app.data.BillShareWithProfileRow
import com.rentmate.app.data.BillWithSharesRow
import com.rentmate.app.data.ChoreRow
import org.junit.Assert.assertEquals
import org.junit.Test

/** RM-0016: the Dashboard cards are computed from cached rows, the same way settle_up does. */
class DashboardSummaryTest {

    private fun bill(id: String, issuer: String, due: String, vararg shares: Pair<String, Double>) = BillWithSharesRow(
        id = id, household_id = "h1", title = id, amount = shares.sumOf { it.second }, due_date = due,
        split_method = "EQUAL", issued_by_user_id = issuer, created_at = "c", updated_at = "u",
        bill_shares = shares.mapIndexed { i, (user, amount) ->
            BillShareWithProfileRow(id = "$id-$i", user_id = user, amount_owed = amount, is_paid = false)
        }
    )

    @Test
    fun balances_netOutAcrossBills() {
        val bills = listOf(
            bill("rent", "me", "2099-01-01", "me" to 100.0, "u2" to 100.10),
            bill("wifi", "u2", "2099-01-01", "me" to 30.05, "u2" to 30.05)
        )
        val summary = summarise(bills, emptyList(), "me", "2026-01-01")
        assertEquals(70.05, summary.owedToYou, 0.0)
        assertEquals(0.0, summary.youOwe, 0.0)

        val other = summarise(bills, emptyList(), "u2", "2026-01-01")
        assertEquals(70.05, other.youOwe, 0.0)
    }

    @Test
    fun overdueBills_andNextChore_comeFromCache() {
        val bills = listOf(
            bill("old", "u2", "2025-01-01", "me" to 10.0),
            bill("new", "u2", "2099-01-01", "me" to 10.0)
        )
        val chores = listOf(
            ChoreRow("c1", "h1", "Dishes", "WEEKLY", 1, listOf("u2", "me"), 0, "c"),
            ChoreRow("c2", "h1", "Bins", "WEEKLY", 1, listOf("u2", "me"), 1, "c")
        )
        val summary = summarise(bills, chores, "me", "2026-01-01")
        assertEquals(listOf("old"), summary.overdueBills.map { it.id })
        assertEquals("c2", summary.nextChore?.id)
    }
}
